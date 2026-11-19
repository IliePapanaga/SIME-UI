package md.sime.agenda.data

import android.content.Context
import md.sime.agenda.model.CumulativeAbsences
import md.sime.agenda.model.DailyAbsence
import md.sime.agenda.model.DatedGrade
import md.sime.agenda.model.GradeCalculator
import md.sime.agenda.model.HomeworkItem
import md.sime.agenda.model.LessonEvent
import md.sime.agenda.model.PortalSnapshot
import md.sime.agenda.model.RecentEvaluation
import md.sime.agenda.model.StudentOption
import md.sime.agenda.model.StudentProfile
import md.sime.agenda.model.SubjectAbsenceSummary
import md.sime.agenda.model.SubjectGrades
import md.sime.agenda.model.SubjectTeacher
import md.sime.agenda.model.TeacherMessage
import md.sime.agenda.model.ThemeMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LocalCacheStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("sime_ui_prefs", Context.MODE_PRIVATE)
    private val snapshotFile = File(appContext.filesDir, "sime_snapshot_v1.json")
    private val scheduleFile = File(appContext.filesDir, "sime_schedule_v1.json")

    fun saveThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.id).apply()
    }

    fun getThemeMode(): ThemeMode = ThemeMode.fromId(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.id))

    fun saveLastMainPageUrl(url: String) {
        prefs.edit().putString(KEY_LAST_MAIN_URL, url).apply()
    }

    fun getLastMainPageUrl(): String? = prefs.getString(KEY_LAST_MAIN_URL, null)

    fun saveCompletedHomeworkIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_COMPLETED_HOMEWORK, ids).apply()
    }

    fun getCompletedHomeworkIds(): Set<String> {
        return prefs.getStringSet(KEY_COMPLETED_HOMEWORK, emptySet())?.toSet() ?: emptySet()
    }

    fun saveSeenMessageIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_SEEN_MESSAGES, ids).apply()
    }

    fun getSeenMessageIds(): Set<String> {
        return prefs.getStringSet(KEY_SEEN_MESSAGES, emptySet())?.toSet() ?: emptySet()
    }

    fun hasInitializedMessages(): Boolean = prefs.getBoolean(KEY_MESSAGES_INITIALIZED, false)

    fun setMessagesInitialized(initialized: Boolean) {
        prefs.edit().putBoolean(KEY_MESSAGES_INITIALIZED, initialized).apply()
    }

    fun saveSnapshot(snapshot: PortalSnapshot) {
        runCatching {
            val root = JSONObject().apply {
                put("updatedAtMillis", snapshot.updatedAtMillis)
                put("sessionId", snapshot.sessionId)
                put("presencePercent", snapshot.presencePercent)
                put("replacementsText", snapshot.replacementsText)
                put("hasReplacements", snapshot.hasReplacements)
                put("profile", encodeProfile(snapshot.profile))
                put("studentOptions", encodeStudentOptions(snapshot.studentOptions))
                put("homeworkItems", encodeHomework(snapshot.homeworkItems))
                put("recentEvaluations", encodeRecentEvaluations(snapshot.recentEvaluations))
                put("sem1Grades", encodeGrades(snapshot.sem1Grades))
                put("sem2Grades", encodeGrades(snapshot.sem2Grades))
                put("dailyAbsences", encodeDailyAbsences(snapshot.dailyAbsences))
                put("subjectAbsences", encodeSubjectAbsences(snapshot.subjectAbsences))
                put("cumulativeAbsences", encodeCumulative(snapshot.cumulativeAbsences))
                put("messages", encodeMessages(snapshot.messages))
                put("teachers", encodeTeachers(snapshot.teachers))
            }
            snapshotFile.writeText(root.toString(), Charsets.UTF_8)
        }
    }

    fun loadSnapshot(): PortalSnapshot? {
        if (!snapshotFile.exists()) return null
        return runCatching {
            val root = JSONObject(snapshotFile.readText(Charsets.UTF_8))
            PortalSnapshot(
                updatedAtMillis = root.optLong("updatedAtMillis"),
                sessionId = root.optString("sessionId"),
                profile = decodeProfile(root.optJSONObject("profile")),
                studentOptions = decodeStudentOptions(root.optJSONArray("studentOptions")),
                presencePercent = root.optInt("presencePercent"),
                homeworkItems = decodeHomework(root.optJSONArray("homeworkItems")),
                recentEvaluations = decodeRecentEvaluations(root.optJSONArray("recentEvaluations")),
                replacementsText = root.optString("replacementsText"),
                hasReplacements = root.optBoolean("hasReplacements"),
                sem1Grades = decodeGrades(root.optJSONArray("sem1Grades"), semester = 1),
                sem2Grades = decodeGrades(root.optJSONArray("sem2Grades"), semester = 2),
                dailyAbsences = decodeDailyAbsences(root.optJSONArray("dailyAbsences")),
                subjectAbsences = decodeSubjectAbsences(root.optJSONArray("subjectAbsences")),
                cumulativeAbsences = decodeCumulative(root.optJSONObject("cumulativeAbsences")),
                messages = decodeMessages(root.optJSONArray("messages")),
                teachers = decodeTeachers(root.optJSONArray("teachers"))
            )
        }.getOrNull()
    }

    fun saveScheduleEvents(events: List<LessonEvent>) {
        runCatching {
            val arr = JSONArray()
            events.forEach { ev ->
                arr.put(JSONObject().apply {
                    put("id", ev.id)
                    put("dateIso", ev.dateIso)
                    put("dateDisplay", ev.dateDisplay)
                    put("periodNumber", ev.periodNumber)
                    put("startTime", ev.startTime)
                    put("endTime", ev.endTime)
                    put("subject", ev.subject)
                    put("room", ev.room ?: JSONObject.NULL)
                    put("teacher", ev.teacher)
                    put("homeworkSnippet", ev.homeworkSnippet ?: JSONObject.NULL)
                    put("badgeText", ev.badgeText ?: JSONObject.NULL)
                    put("numericGrade", ev.numericGrade ?: JSONObject.NULL)
                    put("isAbsent", ev.isAbsent)
                    put("detailUrl", ev.detailUrl ?: JSONObject.NULL)
                })
            }
            scheduleFile.writeText(arr.toString(), Charsets.UTF_8)
        }
    }

    fun loadScheduleEvents(): List<LessonEvent> {
        if (!scheduleFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(scheduleFile.readText(Charsets.UTF_8))
            val list = mutableListOf<LessonEvent>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                list.add(
                    LessonEvent(
                        id = o.optString("id"),
                        dateIso = o.optString("dateIso"),
                        dateDisplay = o.optString("dateDisplay"),
                        periodNumber = o.optInt("periodNumber"),
                        startTime = o.optString("startTime"),
                        endTime = o.optString("endTime"),
                        subject = o.optString("subject"),
                        room = o.optString("room").takeIf { it.isNotBlank() && it != "null" },
                        teacher = o.optString("teacher"),
                        homeworkSnippet = o.optString("homeworkSnippet").takeIf { it.isNotBlank() && it != "null" },
                        badgeText = o.optString("badgeText").takeIf { it.isNotBlank() && it != "null" },
                        numericGrade = if (o.isNull("numericGrade")) null else o.optInt("numericGrade"),
                        isAbsent = o.optBoolean("isAbsent"),
                        detailUrl = o.optString("detailUrl").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    fun clearPortalData() {
        runCatching { snapshotFile.delete() }
        runCatching { scheduleFile.delete() }
        prefs.edit()
            .remove(KEY_LAST_MAIN_URL)
            .remove(KEY_SEEN_MESSAGES)
            .remove(KEY_MESSAGES_INITIALIZED)
            .apply()
    }

    private fun encodeProfile(p: StudentProfile) = JSONObject().apply {
        put("fullName", p.fullName)
        put("shortName", p.shortName)
        put("initials", p.initials)
        put("birthDate", p.birthDate)
        put("permanentCode", p.permanentCode)
        put("className", p.className)
        put("homeroomTeacher", p.homeroomTeacher)
        put("schoolName", p.schoolName)
        put("schoolAddress", p.schoolAddress)
        put("schoolPhone", p.schoolPhone)
        put("schoolWebsite", p.schoolWebsite)
        put("schoolDirector", p.schoolDirector)
    }

    private fun decodeProfile(o: JSONObject?): StudentProfile {
        if (o == null) return StudentProfile()
        return StudentProfile(
            fullName = o.optString("fullName"),
            shortName = o.optString("shortName"),
            initials = o.optString("initials"),
            birthDate = o.optString("birthDate"),
            permanentCode = o.optString("permanentCode"),
            className = o.optString("className"),
            homeroomTeacher = o.optString("homeroomTeacher"),
            schoolName = o.optString("schoolName"),
            schoolAddress = o.optString("schoolAddress"),
            schoolPhone = o.optString("schoolPhone"),
            schoolWebsite = o.optString("schoolWebsite"),
            schoolDirector = o.optString("schoolDirector")
        )
    }

    private fun encodeStudentOptions(list: List<StudentOption>) = JSONArray().apply {
        list.forEach { s ->
            put(JSONObject().apply {
                put("id", s.id); put("name", s.name); put("switchUrl", s.switchUrl); put("isSelected", s.isSelected)
            })
        }
    }

    private fun decodeStudentOptions(arr: JSONArray?): List<StudentOption> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            StudentOption(o.optString("id"), o.optString("name"), o.optString("switchUrl"), o.optBoolean("isSelected"))
        }
    }

    private fun encodeHomework(list: List<HomeworkItem>) = JSONArray().apply {
        list.forEach { h ->
            put(JSONObject().apply {
                put("id", h.id); put("date", h.date); put("subject", h.subject); put("text", h.text)
            })
        }
    }

    private fun decodeHomework(arr: JSONArray?): List<HomeworkItem> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            HomeworkItem(o.optString("id"), o.optString("date"), o.optString("subject"), o.optString("text"))
        }
    }

    private fun encodeRecentEvaluations(list: List<RecentEvaluation>) = JSONArray().apply {
        list.forEach { e ->
            put(JSONObject().apply {
                put("date", e.date); put("subject", e.subject); put("grade", e.grade)
            })
        }
    }

    private fun decodeRecentEvaluations(arr: JSONArray?): List<RecentEvaluation> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            RecentEvaluation(o.optString("date"), o.optString("subject"), o.optString("grade"))
        }
    }

    private fun encodeGrades(list: List<SubjectGrades>) = JSONArray().apply {
        list.forEach { s ->
            put(JSONObject().apply {
                put("subject", s.subject)
                put("rawEntries", JSONArray(s.rawEntries))
                put("numericGrades", JSONArray(s.numericGrades))
                put("qualifiers", JSONArray(s.qualifiers))
                put("teza", s.teza ?: JSONObject.NULL)
                put("tezaRaw", s.tezaRaw ?: JSONObject.NULL)
                put("officialAverage", s.officialAverage ?: JSONObject.NULL)
                put("datedGrades", JSONArray().apply {
                    s.datedGrades.forEach { dg ->
                        put(JSONObject().apply {
                            put("id", dg.id)
                            put("numericValue", dg.numericValue ?: JSONObject.NULL)
                            put("rawText", dg.rawText)
                            put("dateDisplay", dg.dateDisplay ?: JSONObject.NULL)
                            put("dateIso", dg.dateIso ?: JSONObject.NULL)
                            put("periodNumber", dg.periodNumber ?: JSONObject.NULL)
                            put("lessonId", dg.lessonId ?: JSONObject.NULL)
                            put("detailUrl", dg.detailUrl ?: JSONObject.NULL)
                        })
                    }
                })
            })
        }
    }

    private fun decodeGrades(arr: JSONArray?, semester: Int): List<SubjectGrades> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val rawEntries = o.optJSONArray("rawEntries").toStringList()
            val numGrades = o.optJSONArray("numericGrades").toIntList()
            val qualifiers = o.optJSONArray("qualifiers").toStringList()
            val teza = if (o.isNull("teza")) null else o.optInt("teza")
            val tezaRaw = o.optString("tezaRaw").takeIf { it.isNotBlank() && it != "null" }
            val officialAvg = o.optString("officialAverage").takeIf { it.isNotBlank() && it != "null" }
            val dgArr = o.optJSONArray("datedGrades")
            val dated = if (dgArr != null) {
                (0 until dgArr.length()).mapNotNull inner@{ j ->
                    val d = dgArr.optJSONObject(j) ?: return@inner null
                    DatedGrade(
                        id = d.optString("id"),
                        numericValue = if (d.isNull("numericValue")) null else d.optInt("numericValue"),
                        rawText = d.optString("rawText"),
                        dateDisplay = d.optString("dateDisplay").takeIf { it.isNotBlank() && it != "null" },
                        dateIso = d.optString("dateIso").takeIf { it.isNotBlank() && it != "null" },
                        periodNumber = if (d.isNull("periodNumber")) null else d.optInt("periodNumber"),
                        lessonId = d.optString("lessonId").takeIf { it.isNotBlank() && it != "null" },
                        detailUrl = d.optString("detailUrl").takeIf { it.isNotBlank() && it != "null" }
                    )
                }
            } else emptyList()

            SubjectGrades(
                semester = semester,
                subject = o.optString("subject"),
                rawEntries = rawEntries,
                datedGrades = dated,
                numericGrades = numGrades,
                qualifiers = qualifiers,
                teza = teza,
                tezaRaw = tezaRaw,
                officialAverage = officialAvg,
                liveAverage = GradeCalculator.calculateSubjectAverage(numGrades, teza)
            )
        }
    }

    private fun encodeDailyAbsences(list: List<DailyAbsence>) = JSONArray().apply {
        list.forEach { a ->
            put(JSONObject().apply {
                put("date", a.date)
                put("dateEpochDay", a.dateEpochDay)
                put("periodNumber", a.periodNumber)
                put("subject", a.subject)
                put("isMotivated", a.isMotivated)
                put("cumulativeCount", a.cumulativeCount)
                put("monthTitle", a.monthTitle)
            })
        }
    }

    private fun decodeDailyAbsences(arr: JSONArray?): List<DailyAbsence> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            DailyAbsence(
                date = o.optString("date"),
                dateEpochDay = o.optLong("dateEpochDay"),
                periodNumber = o.optString("periodNumber"),
                subject = o.optString("subject"),
                isMotivated = o.optBoolean("isMotivated"),
                cumulativeCount = o.optInt("cumulativeCount"),
                monthTitle = o.optString("monthTitle")
            )
        }
    }

    private fun encodeSubjectAbsences(list: List<SubjectAbsenceSummary>) = JSONArray().apply {
        list.forEach { s ->
            put(JSONObject().apply {
                put("subject", s.subject)
                put("s1m", s.sem1Motivated); put("s1u", s.sem1Unmotivated); put("s1t", s.sem1Total)
                put("s2m", s.sem2Motivated); put("s2u", s.sem2Unmotivated); put("s2t", s.sem2Total)
            })
        }
    }

    private fun decodeSubjectAbsences(arr: JSONArray?): List<SubjectAbsenceSummary> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            SubjectAbsenceSummary(
                subject = o.optString("subject"),
                sem1Motivated = o.optInt("s1m"),
                sem1Unmotivated = o.optInt("s1u"),
                sem1Total = o.optInt("s1t"),
                sem2Motivated = o.optInt("s2m"),
                sem2Unmotivated = o.optInt("s2u"),
                sem2Total = o.optInt("s2t")
            )
        }
    }

    private fun encodeCumulative(c: CumulativeAbsences) = JSONObject().apply {
        put("s1m", c.sem1Motivated); put("s1u", c.sem1Unmotivated); put("s1t", c.sem1Total)
        put("s2m", c.sem2Motivated); put("s2u", c.sem2Unmotivated); put("s2t", c.sem2Total)
    }

    private fun decodeCumulative(o: JSONObject?): CumulativeAbsences {
        if (o == null) return CumulativeAbsences()
        return CumulativeAbsences(
            sem1Motivated = o.optInt("s1m"),
            sem1Unmotivated = o.optInt("s1u"),
            sem1Total = o.optInt("s1t"),
            sem2Motivated = o.optInt("s2m"),
            sem2Unmotivated = o.optInt("s2u"),
            sem2Total = o.optInt("s2t")
        )
    }

    private fun encodeMessages(list: List<TeacherMessage>) = JSONArray().apply {
        list.forEach { m ->
            put(JSONObject().apply {
                put("id", m.id); put("date", m.date); put("professor", m.professor)
                put("subject", m.subject); put("text", m.text)
            })
        }
    }

    private fun decodeMessages(arr: JSONArray?): List<TeacherMessage> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            TeacherMessage(
                id = o.optString("id"),
                date = o.optString("date"),
                professor = o.optString("professor"),
                subject = o.optString("subject"),
                text = o.optString("text")
            )
        }
    }

    private fun encodeTeachers(list: List<SubjectTeacher>) = JSONArray().apply {
        list.forEach { t ->
            put(JSONObject().apply {
                put("subject", t.subject); put("teacherName", t.teacherName)
            })
        }
    }

    private fun decodeTeachers(arr: JSONArray?): List<SubjectTeacher> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            SubjectTeacher(o.optString("subject"), o.optString("teacherName"))
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }
    }

    private fun JSONArray?.toIntList(): List<Int> {
        if (this == null) return emptyList()
        return (0 until length()).map { optInt(it) }
    }

    companion object {
        private const val KEY_THEME_MODE = "theme_mode_v2"
        private const val KEY_LAST_MAIN_URL = "last_main_url"
        private const val KEY_COMPLETED_HOMEWORK = "completed_homework_ids"
        private const val KEY_SEEN_MESSAGES = "seen_message_ids"
        private const val KEY_MESSAGES_INITIALIZED = "messages_initialized"
    }
}
