package md.sime.agenda.model

enum class UserType(val apexCode: Int, val labelRo: String) {
    ELEV(2, "Elev"),
    PARINTE(1, "Părinte");

    companion object {
        fun fromCode(code: Int): UserType = entries.firstOrNull { it.apexCode == code } ?: ELEV
    }
}

enum class ThemeMode(val id: String, val label: String) {
    SYSTEM("system", "Sistem"),
    AMOLED("amoled", "OLED"),
    DARK("dark", "Întunecat"),
    LIGHT("light", "Luminos");

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

enum class AbsenceSortMode(val label: String) {
    NEWEST("Cele mai noi"),
    OLDEST("Cele mai vechi"),
    MOST_PER_DAY("Cele mai multe/zi")
}

enum class AbsenceFilterMode(val label: String) {
    ALL("Toate"),
    UNMOTIVATED("Nemotivate"),
    MOTIVATED("Motivate")
}

data class StudentOption(
    val id: String,
    val name: String,
    val switchUrl: String,
    val isSelected: Boolean
)

data class StudentProfile(
    val fullName: String = "",
    val shortName: String = "",
    val initials: String = "",
    val birthDate: String = "",
    val permanentCode: String = "",
    val className: String = "",
    val homeroomTeacher: String = "",
    val schoolName: String = "",
    val schoolAddress: String = "",
    val schoolPhone: String = "",
    val schoolWebsite: String = "",
    val schoolDirector: String = ""
)

data class HomeworkItem(
    val id: String,
    val date: String,
    val subject: String,
    val text: String
)

data class RecentEvaluation(
    val date: String,
    val subject: String,
    val grade: String
)

data class LessonEvent(
    val id: String,
    val dateIso: String,
    val dateDisplay: String,
    val periodNumber: Int,
    val startTime: String,
    val endTime: String,
    val subject: String,
    val room: String?,
    val teacher: String,
    val homeworkSnippet: String?,
    val badgeText: String?,
    val numericGrade: Int?,
    val isAbsent: Boolean,
    val detailUrl: String?
)

data class LessonDetail(
    val subject: String,
    val periodNumber: Int,
    val date: String,
    val topic: String,
    val teacherMessage: String,
    val evaluation: String?,
    val homework: String,
    val homeworkDueDate: String?,
    val homeworkCreatedDate: String?,
    val isSummative: Boolean,
    val isTeza: Boolean,
    val meetingLink: String?
)

data class DatedGrade(
    val id: String,
    val numericValue: Int?,
    val rawText: String,
    val dateDisplay: String? = null,
    val dateIso: String? = null,
    val periodNumber: Int? = null,
    val lessonId: String? = null,
    val detailUrl: String? = null,
    val isSimulated: Boolean = false
)

data class SubjectGrades(
    val semester: Int,
    val subject: String,
    val rawEntries: List<String>,
    val datedGrades: List<DatedGrade>,
    val numericGrades: List<Int>,
    val qualifiers: List<String>,
    val teza: Int?,
    val tezaRaw: String?,
    val officialAverage: String?,
    val liveAverage: Double?
)

data class DailyAbsence(
    val date: String,
    val dateEpochDay: Long,
    val periodNumber: String,
    val subject: String,
    val isMotivated: Boolean,
    val cumulativeCount: Int,
    val monthTitle: String
)

data class DayAbsenceGroup(
    val dateDisplay: String,
    val dateEpochDay: Long,
    val items: List<DailyAbsence>,
    val motivatedCount: Int,
    val unmotivatedCount: Int
)

data class SubjectAbsenceSummary(
    val subject: String,
    val sem1Motivated: Int,
    val sem1Unmotivated: Int,
    val sem1Total: Int,
    val sem2Motivated: Int,
    val sem2Unmotivated: Int,
    val sem2Total: Int
)

data class CumulativeAbsences(
    val sem1Motivated: Int = 0,
    val sem1Unmotivated: Int = 0,
    val sem1Total: Int = 0,
    val sem2Motivated: Int = 0,
    val sem2Unmotivated: Int = 0,
    val sem2Total: Int = 0
)

data class TeacherMessage(
    val id: String,
    val date: String,
    val professor: String,
    val subject: String,
    val text: String
)

data class SubjectTeacher(
    val subject: String,
    val teacherName: String
)

data class PortalSnapshot(
    val updatedAtMillis: Long = 0L,
    val sessionId: String = "",
    val profile: StudentProfile = StudentProfile(),
    val studentOptions: List<StudentOption> = emptyList(),
    val presencePercent: Int = 0,
    val homeworkItems: List<HomeworkItem> = emptyList(),
    val recentEvaluations: List<RecentEvaluation> = emptyList(),
    val replacementsText: String = "",
    val hasReplacements: Boolean = false,
    val sem1Grades: List<SubjectGrades> = emptyList(),
    val sem2Grades: List<SubjectGrades> = emptyList(),
    val dailyAbsences: List<DailyAbsence> = emptyList(),
    val subjectAbsences: List<SubjectAbsenceSummary> = emptyList(),
    val cumulativeAbsences: CumulativeAbsences = CumulativeAbsences(),
    val messages: List<TeacherMessage> = emptyList(),
    val teachers: List<SubjectTeacher> = emptyList()
)

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String
)

enum class SubjectEvaluationType {
    NUMERIC,
    QUALIFIER,
    DESCRIPTOR_ONLY
}
