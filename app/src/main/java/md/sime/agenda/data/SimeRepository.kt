package md.sime.agenda.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import md.sime.agenda.model.GradeCalculator
import md.sime.agenda.model.LessonDetail
import md.sime.agenda.model.LessonEvent
import md.sime.agenda.model.PortalSnapshot
import md.sime.agenda.model.TeacherMessage
import md.sime.agenda.model.ThemeMode
import md.sime.agenda.model.UserType
import md.sime.agenda.notifications.NotificationHelper
import md.sime.agenda.notifications.SimeSyncWorker

class SimeRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    val credentialStore = CredentialStore(appContext)
    val cacheStore = LocalCacheStore(appContext)
    private val bridge = SimeSessionBridge(appContext)

    private val _snapshot = MutableStateFlow(cacheStore.loadSnapshot())
    val snapshot: StateFlow<PortalSnapshot?> = _snapshot.asStateFlow()

    private val _scheduleEvents = MutableStateFlow(cacheStore.loadScheduleEvents())
    val scheduleEvents: StateFlow<List<LessonEvent>> = _scheduleEvents.asStateFlow()

    private val _themeMode = MutableStateFlow(cacheStore.getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _completedHomeworkIds = MutableStateFlow(cacheStore.getCompletedHomeworkIds())
    val completedHomeworkIds: StateFlow<Set<String>> = _completedHomeworkIds.asStateFlow()

    init {
        NotificationHelper.ensureChannel(appContext)
        recomputeEnrichedGrades()
    }

    fun getSavedCredentials(): SavedCredentials? = credentialStore.getCredentials()

    fun hasActiveLocalSession(): Boolean {
        val creds = credentialStore.getCredentials()
        return _snapshot.value != null || (creds != null && creds.stayLoggedIn)
    }

    fun setThemeMode(mode: ThemeMode) {
        cacheStore.saveThemeMode(mode)
        _themeMode.value = mode
    }

    fun toggleHomeworkDone(homeworkId: String) {
        val updated = _completedHomeworkIds.value.toMutableSet()
        if (!updated.add(homeworkId)) {
            updated.remove(homeworkId)
        }
        cacheStore.saveCompletedHomeworkIds(updated)
        _completedHomeworkIds.value = updated
    }

    fun markAllMessagesSeen() {
        val currentIds = _snapshot.value?.messages?.map { it.id }?.toSet().orEmpty()
        if (currentIds.isNotEmpty()) {
            val merged = cacheStore.getSeenMessageIds() + currentIds
            cacheStore.saveSeenMessageIds(merged)
            cacheStore.setMessagesInitialized(true)
        }
    }

    suspend fun login(
        username: String,
        password: String,
        userType: UserType,
        stayLoggedIn: Boolean = true
    ): Result<PortalSnapshot> {
        return when (val result = bridge.loginAndFetchPortal(
            username = username,
            password = password,
            userType = userType,
            prefetchSemesterMonths = true
        )) {
            is BridgeResult.Success -> {
                credentialStore.saveCredentials(username, password, userType, stayLoggedIn)
                val parsed = handleSuccessResult(result, notifyNewMessages = false)
                SimeSyncWorker.schedulePeriodicSync(appContext)
                Result.success(parsed)
            }
            is BridgeResult.Failure -> {
                Result.failure(Exception(result.message))
            }
        }
    }

    suspend fun refreshPortal(prefetchCalendar: Boolean = true): Result<PortalSnapshot> {
        val creds = credentialStore.getCredentials()
            ?: return Result.failure(Exception("Nu există date de autentificare salvate."))

        return when (val result = bridge.loginAndFetchPortal(
            username = creds.username,
            password = creds.password,
            userType = creds.userType,
            prefetchSemesterMonths = prefetchCalendar,
            targetMainUrl = cacheStore.getLastMainPageUrl()
        )) {
            is BridgeResult.Success -> {
                SimeSyncWorker.schedulePeriodicSync(appContext)
                Result.success(handleSuccessResult(result, notifyNewMessages = true))
            }
            is BridgeResult.Failure -> {
                Result.failure(Exception(result.message))
            }
        }
    }

    suspend fun ensureScheduleForDate(dateIso: String): Result<List<LessonEvent>> {
        val creds = credentialStore.getCredentials()
            ?: return Result.failure(Exception("Nu există date de autentificare salvate."))

        val res = bridge.fetchCalendarMonthForDate(
            targetDateIso = dateIso,
            username = creds.username,
            password = creds.password,
            userType = creds.userType,
            targetMainUrl = cacheStore.getLastMainPageUrl()
        )
        return res.map { (finalUrl, newEvents) ->
            cacheStore.saveLastMainPageUrl(finalUrl)
            val merged = mergeScheduleEvents(newEvents)
            recomputeEnrichedGrades()
            merged
        }
    }

    private fun mergeScheduleEvents(newEvents: List<LessonEvent>): List<LessonEvent> {
        val mergedEventsMap = LinkedHashMap<String, LessonEvent>()
        _scheduleEvents.value.forEach { mergedEventsMap[it.id] = it }
        newEvents.forEach { mergedEventsMap[it.id] = it }
        val mergedEvents = mergedEventsMap.values
            .sortedWith(compareBy<LessonEvent> { it.dateIso }.thenBy { it.periodNumber })
        if (mergedEvents.isNotEmpty()) {
            cacheStore.saveScheduleEvents(mergedEvents)
            _scheduleEvents.value = mergedEvents
        }
        return mergedEvents
    }

    suspend fun switchStudent(switchPathOrUrl: String): Result<PortalSnapshot> {
        val creds = credentialStore.getCredentials()
            ?: return Result.failure(Exception("Nu există date de autentificare salvate."))

        val fullUrl = if (switchPathOrUrl.startsWith("http")) {
            switchPathOrUrl
        } else {
            "https://agenda.sime.md$switchPathOrUrl"
        }

        return when (val result = bridge.loginAndFetchPortal(
            username = creds.username,
            password = creds.password,
            userType = creds.userType,
            prefetchSemesterMonths = true,
            targetMainUrl = fullUrl
        )) {
            is BridgeResult.Success -> {
                // Reset schedule cache when switching student
                _scheduleEvents.value = emptyList()
                cacheStore.saveScheduleEvents(emptyList())
                Result.success(handleSuccessResult(result, notifyNewMessages = false))
            }
            is BridgeResult.Failure -> Result.failure(Exception(result.message))
        }
    }

    suspend fun loadLessonDetail(lesson: LessonEvent): Result<LessonDetail> {
        val url = lesson.detailUrl
            ?: return Result.failure(Exception("Detaliile acestei lecții nu sunt disponibile."))

        val firstTry = bridge.fetchLessonDetail(url)
        if (firstTry.isSuccess) return firstTry

        // If session expired, transparently re-login and retry once
        if (firstTry.exceptionOrNull() is SimeSessionBridge.SessionExpiredException) {
            val refresh = refreshPortal(prefetchCalendar = true)
            if (refresh.isSuccess) {
                val updatedLesson = _scheduleEvents.value.firstOrNull {
                    it.dateIso == lesson.dateIso &&
                        it.periodNumber == lesson.periodNumber &&
                        GradeCalculator.normalizeSubject(it.subject) ==
                        GradeCalculator.normalizeSubject(lesson.subject)
                }
                val retryUrl = updatedLesson?.detailUrl ?: url
                return bridge.fetchLessonDetail(retryUrl)
            }
        }
        return firstTry
    }

    suspend fun checkForNewMessagesInBackground(): List<TeacherMessage> {
        val creds = credentialStore.getCredentials() ?: return emptyList()
        if (!creds.stayLoggedIn) return emptyList()

        val previousSnapshot = _snapshot.value
        val previousIds = cacheStore.getSeenMessageIds()
        val wasInitialized = cacheStore.hasInitializedMessages()

        // Fast HTTP check first; fallback to headless WebView only if session expired
        val fast = bridge.fetchFastWithExistingSession(cacheStore.getLastMainPageUrl())
        if (fast is BridgeResult.Success) {
            val freshMsgOnly = SimeHtmlParser.parseMainPage(fast.mainPageHtml)
            cacheStore.saveLastMainPageUrl(fast.mainPageUrl)
            val newMessages = computeUnseenMessages(freshMsgOnly.messages, previousSnapshot, previousIds, wasInitialized)
            if (freshMsgOnly.messages.isNotEmpty()) {
                val updated = previousSnapshot?.copy(
                    updatedAtMillis = System.currentTimeMillis(),
                    messages = freshMsgOnly.messages
                ) ?: freshMsgOnly
                cacheStore.saveSnapshot(updated)
                _snapshot.value = updated
                cacheStore.saveSeenMessageIds(cacheStore.getSeenMessageIds() + freshMsgOnly.messages.map { it.id })
                cacheStore.setMessagesInitialized(true)
            }
            return newMessages
        }

        val result = bridge.loginAndFetchPortal(
            username = creds.username,
            password = creds.password,
            userType = creds.userType,
            prefetchSemesterMonths = false,
            targetMainUrl = cacheStore.getLastMainPageUrl()
        )

        if (result !is BridgeResult.Success) return emptyList()

        val fresh = SimeHtmlParser.parseMainPage(
            html = result.mainPageHtml,
            reusitaHtml = result.reusitaHtml,
            dosarHtml = result.dosarHtml,
            absenteHtml = result.absenteHtml,
            mesajeHtml = result.mesajeHtml
        )
        val newMessages = computeUnseenMessages(fresh.messages, previousSnapshot, previousIds, wasInitialized)
        handleSuccessResult(result, notifyNewMessages = false)
        return newMessages
    }

    private fun computeUnseenMessages(
        freshMessages: List<TeacherMessage>,
        previousSnapshot: PortalSnapshot?,
        previousIds: Set<String>,
        wasInitialized: Boolean
    ): List<TeacherMessage> {
        if (!wasInitialized && previousSnapshot == null) return emptyList()
        val known = if (previousIds.isNotEmpty()) previousIds
        else previousSnapshot?.messages?.map { it.id }?.toSet().orEmpty()
        return freshMessages.filter { it.id !in known }
    }

    suspend fun logout(clearSavedPassword: Boolean) {
        val sid = _snapshot.value?.sessionId.orEmpty()
        bridge.logoutSession(sid)
        SimeSyncWorker.cancelSync(appContext)
        cacheStore.clearPortalData()
        _snapshot.value = null
        _scheduleEvents.value = emptyList()
        if (clearSavedPassword) {
            credentialStore.clearPasswordKeepUsername()
        } else {
            credentialStore.setStayLoggedIn(false)
        }
    }

    private fun handleSuccessResult(
        result: BridgeResult.Success,
        notifyNewMessages: Boolean
    ): PortalSnapshot {
        cacheStore.saveLastMainPageUrl(result.mainPageUrl)

        val mergedEvents = mergeScheduleEvents(result.calendarEvents)

        val parsed = SimeHtmlParser.parseMainPage(
            html = result.mainPageHtml,
            reusitaHtml = result.reusitaHtml,
            dosarHtml = result.dosarHtml,
            absenteHtml = result.absenteHtml,
            mesajeHtml = result.mesajeHtml
        )
        val enrichedSem1 = GradeCalculator.enrichGradesWithDates(
            subjects = parsed.sem1Grades,
            recentEvaluations = parsed.recentEvaluations,
            scheduleEvents = mergedEvents
        )
        val enrichedSem2 = GradeCalculator.enrichGradesWithDates(
            subjects = parsed.sem2Grades,
            recentEvaluations = parsed.recentEvaluations,
            scheduleEvents = mergedEvents
        )
        val enrichedSnapshot = parsed.copy(
            sem1Grades = enrichedSem1,
            sem2Grades = enrichedSem2
        )

        if (notifyNewMessages && cacheStore.hasInitializedMessages()) {
            val known = cacheStore.getSeenMessageIds()
            val unseen = enrichedSnapshot.messages.filter { it.id !in known }
            unseen.forEachIndexed { idx, msg ->
                NotificationHelper.showTeacherMessageNotification(
                    context = appContext,
                    message = msg,
                    notificationId = 3000 + (msg.id.hashCode() and 0x7FFF) + idx
                )
            }
        }

        if (enrichedSnapshot.messages.isNotEmpty()) {
            cacheStore.saveSeenMessageIds(
                cacheStore.getSeenMessageIds() + enrichedSnapshot.messages.map { it.id }
            )
            cacheStore.setMessagesInitialized(true)
        }

        cacheStore.saveSnapshot(enrichedSnapshot)
        _snapshot.value = enrichedSnapshot
        return enrichedSnapshot
    }

    private fun recomputeEnrichedGrades() {
        val snap = _snapshot.value ?: return
        val events = _scheduleEvents.value
        if (events.isEmpty() && snap.recentEvaluations.isEmpty()) return
        val updated = snap.copy(
            sem1Grades = GradeCalculator.enrichGradesWithDates(
                snap.sem1Grades, snap.recentEvaluations, events
            ),
            sem2Grades = GradeCalculator.enrichGradesWithDates(
                snap.sem2Grades, snap.recentEvaluations, events
            )
        )
        _snapshot.value = updated
    }

    companion object {
        @Volatile
        private var instance: SimeRepository? = null

        fun getInstance(context: Context): SimeRepository {
            return instance ?: synchronized(this) {
                instance ?: SimeRepository(context).also { instance = it }
            }
        }
    }
}
