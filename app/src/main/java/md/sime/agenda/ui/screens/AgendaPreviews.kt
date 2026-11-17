package md.sime.agenda.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import md.sime.agenda.model.AbsenceFilterMode
import md.sime.agenda.model.AbsenceSortMode
import md.sime.agenda.model.CumulativeAbsences
import md.sime.agenda.model.DailyAbsence
import md.sime.agenda.model.DatedGrade
import md.sime.agenda.model.HomeworkItem
import md.sime.agenda.model.LessonEvent
import md.sime.agenda.model.PortalSnapshot
import md.sime.agenda.model.RecentEvaluation
import md.sime.agenda.model.StudentProfile
import md.sime.agenda.model.SubjectAbsenceSummary
import md.sime.agenda.model.SubjectGrades
import md.sime.agenda.model.ThemeMode
import md.sime.agenda.ui.theme.SimeAgendaTheme
import md.sime.agenda.ui.theme.SimeTheme
import java.time.DayOfWeek
import java.time.LocalDate

private fun sampleDateIso(offsetFromMonday: Long): String {
    val monday = LocalDate.now().with(DayOfWeek.MONDAY)
    return monday.plusDays(offsetFromMonday).toString()
}

private fun sampleLessons(): List<LessonEvent> {
    val mon = sampleDateIso(0)
    return listOf(
        LessonEvent(
            id = "1",
            dateIso = mon,
            dateDisplay = mon,
            periodNumber = 1,
            startTime = "08:30",
            endTime = "09:15",
            subject = "Matematică",
            room = "209",
            teacher = "Prof. Bairac R.",
            homeworkSnippet = "Ex. 12–16, pag. 48",
            badgeText = "10",
            numericGrade = 10,
            isAbsent = false,
            detailUrl = null
        ),
        LessonEvent(
            id = "2",
            dateIso = mon,
            dateDisplay = mon,
            periodNumber = 2,
            startTime = "09:25",
            endTime = "10:10",
            subject = "Chimie",
            room = "105",
            teacher = "Prof. Prunici L.",
            homeworkSnippet = null,
            badgeText = "9",
            numericGrade = 9,
            isAbsent = false,
            detailUrl = null
        ),
        LessonEvent(
            id = "3",
            dateIso = mon,
            dateDisplay = mon,
            periodNumber = 3,
            startTime = "10:20",
            endTime = "11:05",
            subject = "Informatica",
            room = "202",
            teacher = "Prof. Uzunova L.",
            homeworkSnippet = "Algoritmi de sortare pe matrice.",
            badgeText = null,
            numericGrade = null,
            isAbsent = false,
            detailUrl = null
        )
    )
}

private fun sampleSnapshot(): PortalSnapshot = PortalSnapshot(
    updatedAtMillis = 0L,
    profile = StudentProfile(
        fullName = "Elev Demo",
        shortName = "Elev Demo",
        initials = "ED",
        className = "11A",
        homeroomTeacher = "Prof. Popescu M.",
        schoolName = "Liceul Teoretic"
    ),
    presencePercent = 94,
    homeworkItems = listOf(
        HomeworkItem("h1", "17.11.2026", "Matematică", "Ex. 12–16, pag. 48 — Funcții exponențiale."),
        HomeworkItem("h2", "18.11.2026", "Limba română", "Eseu argumentativ pe marginea textului studiat.")
    ),
    recentEvaluations = listOf(
        RecentEvaluation("14.11.2026", "Matematică", "10"),
        RecentEvaluation("12.11.2026", "Informatica", "10"),
        RecentEvaluation("09.11.2026", "Chimie", "9")
    ),
    sem1Grades = listOf(
        SubjectGrades(
            semester = 1,
            subject = "Matematică",
            rawEntries = listOf("10", "10", "9"),
            datedGrades = listOf(
                DatedGrade("g1", 10, "10", "21.09.2026", "2026-09-21"),
                DatedGrade("g2", 10, "10", "19.10.2026", "2026-10-19"),
                DatedGrade("g3", 9, "9", "14.11.2026", "2026-11-14")
            ),
            numericGrades = listOf(10, 10, 9),
            qualifiers = emptyList(),
            teza = null,
            tezaRaw = null,
            officialAverage = null,
            liveAverage = 9.67
        ),
        SubjectGrades(
            semester = 1,
            subject = "Informatica",
            rawEntries = listOf("10", "10"),
            datedGrades = listOf(
                DatedGrade("g4", 10, "10", "06.10.2026", "2026-10-06"),
                DatedGrade("g5", 10, "10", "12.11.2026", "2026-11-12")
            ),
            numericGrades = listOf(10, 10),
            qualifiers = emptyList(),
            teza = null,
            tezaRaw = null,
            officialAverage = null,
            liveAverage = 10.0
        ),
        SubjectGrades(
            semester = 1,
            subject = "Educație fizică",
            rawEntries = listOf("Foarte bine"),
            datedGrades = listOf(
                DatedGrade("q1", null, "Foarte bine")
            ),
            numericGrades = emptyList(),
            qualifiers = listOf("Foarte bine"),
            teza = null,
            tezaRaw = null,
            officialAverage = null,
            liveAverage = null
        )
    ),
    dailyAbsences = listOf(
        DailyAbsence("17.11.2026", 20774L, "4", "Matematică", false, 74, "Noiembrie 2026"),
        DailyAbsence("17.11.2026", 20774L, "5", "Limba engleză", false, 75, "Noiembrie 2026"),
        DailyAbsence("13.11.2026", 20770L, "1", "Limba engleză", false, 70, "Noiembrie 2026"),
        DailyAbsence("13.11.2026", 20770L, "2", "Geografie", true, 71, "Noiembrie 2026")
    ),
    subjectAbsences = listOf(
        SubjectAbsenceSummary("Limba engleză", 8, 2, 10, 0, 0, 0),
        SubjectAbsenceSummary("Matematică", 4, 1, 5, 0, 0, 0),
        SubjectAbsenceSummary("Geografie", 6, 0, 6, 0, 0, 0)
    ),
    cumulativeAbsences = CumulativeAbsences(
        sem1Motivated = 70,
        sem1Unmotivated = 6,
        sem1Total = 76
    )
)

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 412, heightDp = 892)
@Composable
fun HomePreview() {
    SimeAgendaTheme(ThemeMode.AMOLED) {
        Box(modifier = Modifier.fillMaxSize().background(SimeTheme.colors.background)) {
            HomeScreen(
                snapshot = sampleSnapshot(),
                scheduleEvents = sampleLessons(),
                liveOverallAverage = 9.84,
                completedHomeworkIds = emptySet(),
                onToggleHomework = {},
                onJumpToScheduleDate = { _, _ -> },
                onOpenGrades = {},
                onOpenAbsences = {},
                onOpenLessonDetail = {}
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 412, heightDp = 892)
@Composable
fun GradesPreview() {
    SimeAgendaTheme(ThemeMode.AMOLED) {
        Box(modifier = Modifier.fillMaxSize().background(SimeTheme.colors.background)) {
            GradesScreen(
                snapshot = sampleSnapshot(),
                selectedSemester = 1,
                simulatedMarks = emptyMap(),
                simulatedQualifiers = mapOf("Educație fizică" to listOf("Bine")),
                liveOverallAverage = 9.84,
                onSelectSemester = {},
                onAddSimulatedMark = { _, _ -> },
                onRemoveLastSimulatedMark = {},
                onResetSimulatedMarks = {},
                onJumpToScheduleDate = { _, _ -> }
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 412, heightDp = 892)
@Composable
fun AbsencesPreview() {
    SimeAgendaTheme(ThemeMode.AMOLED) {
        Box(modifier = Modifier.fillMaxSize().background(SimeTheme.colors.background)) {
            AbsencesScreen(
                snapshot = sampleSnapshot(),
                sortMode = AbsenceSortMode.NEWEST,
                filterMode = AbsenceFilterMode.ALL,
                onSelectSortMode = {},
                onSelectFilterMode = {},
                onJumpToScheduleDate = { _, _ -> }
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 412, heightDp = 892)
@Composable
fun SchedulePreview() {
    SimeAgendaTheme(ThemeMode.AMOLED) {
        Box(modifier = Modifier.fillMaxSize().background(SimeTheme.colors.background)) {
            ScheduleScreen(
                scheduleEvents = sampleLessons(),
                selectedDateIso = sampleDateIso(0),
                highlightedSubject = "Chimie",
                lessonDetailState = null,
                onSelectDate = {},
                onOpenLessonDetail = {},
                onDismissLessonDetail = {}
            )
        }
    }
}
