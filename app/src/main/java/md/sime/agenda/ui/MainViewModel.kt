package md.sime.agenda.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import md.sime.agenda.data.SavedCredentials
import md.sime.agenda.data.SimeRepository
import md.sime.agenda.model.AbsenceFilterMode
import md.sime.agenda.model.AbsenceSortMode
import md.sime.agenda.model.AppUpdateInfo
import md.sime.agenda.model.GradeCalculator
import md.sime.agenda.model.LessonDetail
import md.sime.agenda.model.LessonEvent
import md.sime.agenda.model.PortalSnapshot
import md.sime.agenda.model.ThemeMode
import md.sime.agenda.model.UserType
import md.sime.agenda.notifications.AppUpdateChecker
import md.sime.agenda.notifications.NotificationHelper
import md.sime.agenda.notifications.SimeSyncWorker
import java.time.DayOfWeek
import java.time.LocalDate

enum class AppTab(val label: String) {
    HOME("Acasă"),
    SCHEDULE("Orar"),
    GRADES("Reușita"),
    ABSENCES("Absențe"),
    MESSAGES("Mesaje")
}

data class LessonDetailSheetState(
    val lesson: LessonEvent,
    val isLoading: Boolean = true,
    val detail: LessonDetail? = null,
    val error: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SimeRepository.getInstance(application)

    val snapshot: StateFlow<PortalSnapshot?> = repo.snapshot
    val scheduleEvents: StateFlow<List<LessonEvent>> = repo.scheduleEvents
    val themeMode: StateFlow<ThemeMode> = repo.themeMode
    val completedHomeworkIds: StateFlow<Set<String>> = repo.completedHomeworkIds

    private val _isLoggedIn = MutableStateFlow(repo.hasActiveLocalSession())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _savedCredentials = MutableStateFlow(repo.getSavedCredentials())
    val savedCredentials: StateFlow<SavedCredentials?> = _savedCredentials.asStateFlow()

    private val _selectedTab = MutableStateFlow(AppTab.HOME)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val _loginLoading = MutableStateFlow(false)
    val loginLoading: StateFlow<Boolean> = _loginLoading.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _selectedScheduleDateIso = MutableStateFlow(LocalDate.now().toString())
    val selectedScheduleDateIso: StateFlow<String> = _selectedScheduleDateIso.asStateFlow()

    private val _highlightedLessonSubject = MutableStateFlow<String?>(null)
    val highlightedLessonSubject: StateFlow<String?> = _highlightedLessonSubject.asStateFlow()

    private val _selectedSemester = MutableStateFlow(1)
    val selectedSemester: StateFlow<Int> = _selectedSemester.asStateFlow()

    // Subject name -> list of simulated marks (e.g., [10, 9])
    private val _simulatedMarks = MutableStateFlow<Map<String, List<Int>>>(emptyMap())
    val simulatedMarks: StateFlow<Map<String, List<Int>>> = _simulatedMarks.asStateFlow()

    // Subject name -> list of simulated qualifiers (e.g., ["Foarte bine", "Bine"])
    private val _simulatedQualifiers = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val simulatedQualifiers: StateFlow<Map<String, List<String>>> = _simulatedQualifiers.asStateFlow()

    private val _absenceSortMode = MutableStateFlow(AbsenceSortMode.NEWEST)
    val absenceSortMode: StateFlow<AbsenceSortMode> = _absenceSortMode.asStateFlow()

    private val _absenceFilterMode = MutableStateFlow(AbsenceFilterMode.ALL)
    val absenceFilterMode: StateFlow<AbsenceFilterMode> = _absenceFilterMode.asStateFlow()

    private val _lessonDetailState = MutableStateFlow<LessonDetailSheetState?>(null)
    val lessonDetailState: StateFlow<LessonDetailSheetState?> = _lessonDetailState.asStateFlow()

    private val _showDossierSheet = MutableStateFlow(false)
    val showDossierSheet: StateFlow<Boolean> = _showDossierSheet.asStateFlow()

    private val _availableUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val availableUpdate: StateFlow<AppUpdateInfo?> = _availableUpdate.asStateFlow()

    private val checkedWeekMondays = mutableSetOf<String>()

    // Live overall average (updates instantly as marks or simulated marks change)
    val liveOverallAverage: StateFlow<Double?> = combine(
        snapshot,
        selectedSemester,
        simulatedMarks
    ) { snap, sem, sim ->
        if (snap == null) null
        else {
            val subjects = if (sem == 1) snap.sem1Grades else snap.sem2Grades
            GradeCalculator.calculateOverallAverage(subjects, sim)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        SimeSyncWorker.schedulePeriodicSync(application)
        checkForAppUpdate()
        val creds = repo.getSavedCredentials()
        if (creds != null && creds.stayLoggedIn) {
            _isLoggedIn.value = true
            refreshInBackground(prefetchCalendar = true)
        }
    }

    fun checkForAppUpdate() {
        viewModelScope.launch {
            _availableUpdate.value = AppUpdateChecker.checkAndNotifyIfAvailable(getApplication())
        }
    }

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
        if (tab != AppTab.SCHEDULE) {
            _highlightedLessonSubject.value = null
        }
        if (tab == AppTab.MESSAGES) {
            repo.markAllMessagesSeen()
        }
    }

    fun handleDeepLinkTab(tabName: String?) {
        if (tabName == NotificationHelper.TAB_MESEJE && _isLoggedIn.value) {
            selectTab(AppTab.MESSAGES)
        }
    }

    fun login(username: String, password: String, userType: UserType, stayLoggedIn: Boolean) {
        if (_loginLoading.value) return
        if (username.isBlank() || password.isBlank()) {
            _loginError.value = "Introdu numele de utilizator și parola."
            return
        }
        viewModelScope.launch {
            _loginLoading.value = true
            _loginError.value = null
            val res = repo.login(username.trim(), password, userType, stayLoggedIn)
            _loginLoading.value = false
            res.onSuccess {
                _savedCredentials.value = repo.getSavedCredentials()
                _isLoggedIn.value = true
                _selectedTab.value = AppTab.HOME
                _simulatedMarks.value = emptyMap()
                _simulatedQualifiers.value = emptyMap()
                SimeSyncWorker.schedulePeriodicSync(getApplication())
                checkForAppUpdate()
            }.onFailure { err ->
                _loginError.value = err.message ?: "Autentificare eșuată."
            }
        }
    }

    fun oneTapLogin() {
        val creds = _savedCredentials.value ?: return
        login(creds.username, creds.password, creds.userType, stayLoggedIn = true)
    }

    fun refresh(prefetchCalendar: Boolean = true) {
        refreshInBackground(prefetchCalendar)
    }

    private fun refreshInBackground(prefetchCalendar: Boolean) {
        if (_isSyncing.value) return
        checkForAppUpdate()
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            val res = repo.refreshPortal(prefetchCalendar = prefetchCalendar)
            _isSyncing.value = false
            res.onSuccess {
                if (prefetchCalendar) {
                    checkedWeekMondays.clear()
                }
            }.onFailure { err ->
                _syncError.value = err.message
            }
        }
    }

    fun selectScheduleDate(dateIso: String) {
        _selectedScheduleDateIso.value = dateIso
        _highlightedLessonSubject.value = null
        ensureWeekLoaded(dateIso)
    }

    fun jumpToScheduleDate(
        dateIso: String,
        subjectToHighlight: String? = null,
        openDetailAutomatically: Boolean = false
    ) {
        _selectedScheduleDateIso.value = dateIso
        _highlightedLessonSubject.value = subjectToHighlight
        _selectedTab.value = AppTab.SCHEDULE

        viewModelScope.launch {
            ensureWeekLoadedSuspend(dateIso)
            if (openDetailAutomatically && !subjectToHighlight.isNullOrBlank()) {
                val targetKey = GradeCalculator.normalizeSubject(subjectToHighlight)
                val candidates = scheduleEvents.value.filter {
                    it.dateIso == dateIso && GradeCalculator.normalizeSubject(it.subject) == targetKey
                }
                val bestMatch = candidates.firstOrNull { it.numericGrade != null }
                    ?: candidates.firstOrNull()
                if (bestMatch != null) {
                    openLessonDetail(bestMatch)
                }
            }
        }
    }

    private fun ensureWeekLoaded(dateIso: String) {
        viewModelScope.launch {
            ensureWeekLoadedSuspend(dateIso)
        }
    }

    private suspend fun ensureWeekLoadedSuspend(dateIso: String) {
        val date = runCatching { LocalDate.parse(dateIso) }.getOrNull() ?: return
        val monday = date.with(DayOfWeek.MONDAY)
        val mondayIso = monday.toString()
        val sundayIso = monday.plusDays(6).toString()

        val hasEventsForWeek = scheduleEvents.value.any { it.dateIso in mondayIso..sundayIso }
        if (hasEventsForWeek || mondayIso in checkedWeekMondays) {
            return
        }

        checkedWeekMondays.add(mondayIso)
        _isSyncing.value = true
        val res = repo.ensureScheduleForDate(dateIso)
        _isSyncing.value = false
        res.onFailure { err ->
            checkedWeekMondays.remove(mondayIso)
            _syncError.value = err.message
        }
    }

    fun selectSemester(semester: Int) {
        _selectedSemester.value = semester
    }

    fun addSimulatedMark(subject: String, mark: Int) {
        _simulatedMarks.value = appendToSimulatedMap(_simulatedMarks.value, subject, mark)
    }

    fun removeLastSimulatedMark(subject: String) {
        _simulatedMarks.value = popFromSimulatedMap(_simulatedMarks.value, subject)
    }

    fun addSimulatedQualifier(subject: String, qualifier: String) {
        _simulatedQualifiers.value = appendToSimulatedMap(_simulatedQualifiers.value, subject, qualifier)
    }

    fun removeLastSimulatedQualifier(subject: String) {
        _simulatedQualifiers.value = popFromSimulatedMap(_simulatedQualifiers.value, subject)
    }

    fun resetSimulatedMarks(subject: String? = null) {
        if (subject == null) {
            _simulatedMarks.value = emptyMap()
            _simulatedQualifiers.value = emptyMap()
        } else {
            _simulatedMarks.value = _simulatedMarks.value.toMutableMap().apply { remove(subject) }
            _simulatedQualifiers.value = _simulatedQualifiers.value.toMutableMap().apply { remove(subject) }
        }
    }

    private fun <T> appendToSimulatedMap(
        map: Map<String, List<T>>,
        subject: String,
        value: T
    ): Map<String, List<T>> = map.toMutableMap().apply {
        this[subject] = this[subject].orEmpty() + value
    }

    private fun <T> popFromSimulatedMap(
        map: Map<String, List<T>>,
        subject: String
    ): Map<String, List<T>> = map.toMutableMap().apply {
        val list = this[subject].orEmpty()
        if (list.size <= 1) remove(subject) else this[subject] = list.dropLast(1)
    }

    fun setAbsenceSortMode(mode: AbsenceSortMode) {
        _absenceSortMode.value = mode
    }

    fun setAbsenceFilterMode(mode: AbsenceFilterMode) {
        _absenceFilterMode.value = mode
    }

    fun toggleHomeworkDone(homeworkId: String) {
        repo.toggleHomeworkDone(homeworkId)
    }

    fun openLessonDetail(lesson: LessonEvent) {
        _lessonDetailState.value = LessonDetailSheetState(lesson = lesson, isLoading = true)
        viewModelScope.launch {
            val res = repo.loadLessonDetail(lesson)
            _lessonDetailState.value = res.fold(
                onSuccess = { detail ->
                    LessonDetailSheetState(lesson = lesson, isLoading = false, detail = detail)
                },
                onFailure = { err ->
                    LessonDetailSheetState(
                        lesson = lesson,
                        isLoading = false,
                        error = err.message ?: "Nu s-au putut încărca detaliile."
                    )
                }
            )
        }
    }

    fun closeLessonDetail() {
        _lessonDetailState.value = null
    }

    fun openDossierSheet() {
        _showDossierSheet.value = true
    }

    fun closeDossierSheet() {
        _showDossierSheet.value = false
    }

    fun setThemeMode(mode: ThemeMode) {
        repo.setThemeMode(mode)
    }

    fun switchStudent(switchUrl: String) {
        viewModelScope.launch {
            _showDossierSheet.value = false
            _isSyncing.value = true
            repo.switchStudent(switchUrl)
            _isSyncing.value = false
            _simulatedMarks.value = emptyMap()
            _simulatedQualifiers.value = emptyMap()
        }
    }

    fun triggerTestNotification() {
        NotificationHelper.showTestNotification(getApplication())
    }

    fun logout(forgetSavedCredentials: Boolean) {
        viewModelScope.launch {
            _showDossierSheet.value = false
            repo.logout(clearSavedPassword = forgetSavedCredentials)
            _savedCredentials.value = repo.getSavedCredentials()
            _isLoggedIn.value = false
            _simulatedMarks.value = emptyMap()
            _simulatedQualifiers.value = emptyMap()
            _loginError.value = null
        }
    }

    fun dismissSyncError() {
        _syncError.value = null
    }
}
