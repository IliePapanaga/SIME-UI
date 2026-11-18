package md.sime.agenda

import md.sime.agenda.model.AbsenceFilterMode
import md.sime.agenda.model.AbsenceSortMode
import md.sime.agenda.model.DailyAbsence
import md.sime.agenda.model.DatedGrade
import md.sime.agenda.model.GradeCalculator
import md.sime.agenda.model.LessonEvent
import md.sime.agenda.model.SubjectEvaluationType
import md.sime.agenda.model.SubjectGrades
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeCalculatorTest {

    @Test
    fun subjectAndOverallAverages_updateLiveWithMarksAndTeza() {
        val bio = SubjectGrades(
            semester = 1,
            subject = "Biologie",
            rawEntries = listOf("10", "10"),
            datedGrades = listOf(DatedGrade("1", 10, "10"), DatedGrade("2", 10, "10")),
            numericGrades = listOf(10, 10),
            qualifiers = emptyList(),
            teza = null,
            tezaRaw = null,
            officialAverage = null,
            liveAverage = 10.0
        )
        val math = SubjectGrades(
            semester = 1,
            subject = "Matematică",
            rawEntries = listOf("9", "10"),
            datedGrades = listOf(DatedGrade("3", 9, "9"), DatedGrade("4", 10, "10")),
            numericGrades = listOf(9, 10),
            qualifiers = emptyList(),
            teza = 8,
            tezaRaw = "8",
            officialAverage = null,
            liveAverage = null
        )

        // With teza: ((9+10)/2 + 8) / 2 = (9.5 + 8)/2 = 8.75
        val mathWithTeza = GradeCalculator.calculateSubjectAverage(math.numericGrades, math.teza)
        assertEquals(8.75, mathWithTeza!!, 0.001)

        // Overall average without simulation: (10.0 + 8.75) / 2 = 9.375
        val overallBefore = GradeCalculator.calculateOverallAverage(listOf(bio, math))
        assertEquals(9.375, overallBefore!!, 0.001)

        // Simulate adding a 10 to Matematică: current mean (9+10+10)/3 = 9.6667, with teza 8 => 8.8333
        val overallAfter = GradeCalculator.calculateOverallAverage(
            listOf(bio, math),
            simulatedBySubject = mapOf("Matematică" to listOf(10))
        )
        assertEquals((10.0 + ((29.0 / 3.0 + 8.0) / 2.0)) / 2.0, overallAfter!!, 0.001)
    }

    @Test
    fun enrichGradesWithDates_matchesScheduleEventsToReusitaMarks() {
        val chimie = SubjectGrades(
            semester = 1,
            subject = "Chimie",
            rawEntries = listOf("10", "9", "10", "10"),
            datedGrades = listOf(
                DatedGrade("c0", 10, "10"),
                DatedGrade("c1", 9, "9"),
                DatedGrade("c2", 10, "10"),
                DatedGrade("c3", 10, "10")
            ),
            numericGrades = listOf(10, 9, 10, 10),
            qualifiers = emptyList(),
            teza = null,
            tezaRaw = null,
            officialAverage = null,
            liveAverage = 9.75
        )
        val events = listOf(
            LessonEvent(
                id = "e1", dateIso = "2026-11-04", dateDisplay = "04.11.2026",
                periodNumber = 2, startTime = "09:25", endTime = "10:10",
                subject = "Chimie", room = "105", teacher = "Prunici Ludmila",
                homeworkSnippet = null, badgeText = "10", numericGrade = 10,
                isAbsent = false, detailUrl = "url1"
            ),
            LessonEvent(
                id = "e2", dateIso = "2026-11-09", dateDisplay = "09.11.2026",
                periodNumber = 3, startTime = "10:20", endTime = "11:05",
                subject = "Chimie", room = "105", teacher = "Prunici Ludmila",
                homeworkSnippet = null, badgeText = "10", numericGrade = 10,
                isAbsent = false, detailUrl = "url2"
            )
        )

        val enriched = GradeCalculator.enrichGradesWithDates(listOf(chimie), emptyList(), events).first()
        assertNull(enriched.datedGrades[0].dateIso)
        assertNull(enriched.datedGrades[1].dateIso)
        assertEquals("2026-11-04", enriched.datedGrades[2].dateIso)
        assertEquals("2026-11-09", enriched.datedGrades[3].dateIso)
        assertEquals("url2", enriched.datedGrades[3].detailUrl)
    }

    @Test
    fun groupAndSortAbsencesByDay_sortsAndFiltersProperly() {
        val absences = listOf(
            DailyAbsence("05.10.2026", GradeCalculator.parseDisplayDateToEpochDay("05.10.2026"), "2", "Fizica", true, 54, "Octombrie 2026"),
            DailyAbsence("16.10.2026", GradeCalculator.parseDisplayDateToEpochDay("16.10.2026"), "1", "Engleză", true, 56, "Octombrie 2026"),
            DailyAbsence("16.10.2026", GradeCalculator.parseDisplayDateToEpochDay("16.10.2026"), "2", "Geografie", true, 57, "Octombrie 2026"),
            DailyAbsence("06.11.2026", GradeCalculator.parseDisplayDateToEpochDay("06.11.2026"), "1", "Engleză", false, 61, "Noiembrie 2026")
        )

        val newest = GradeCalculator.groupAndSortAbsencesByDay(absences, AbsenceSortMode.NEWEST, AbsenceFilterMode.ALL)
        assertEquals(listOf("06.11.2026", "16.10.2026", "05.10.2026"), newest.map { it.dateDisplay })

        val oldest = GradeCalculator.groupAndSortAbsencesByDay(absences, AbsenceSortMode.OLDEST, AbsenceFilterMode.ALL)
        assertEquals(listOf("05.10.2026", "16.10.2026", "06.11.2026"), oldest.map { it.dateDisplay })

        val mostPerDay = GradeCalculator.groupAndSortAbsencesByDay(absences, AbsenceSortMode.MOST_PER_DAY, AbsenceFilterMode.ALL)
        assertEquals("16.10.2026", mostPerDay.first().dateDisplay)
        assertEquals(2, mostPerDay.first().items.size)

        val unmotivatedOnly = GradeCalculator.groupAndSortAbsencesByDay(absences, AbsenceSortMode.NEWEST, AbsenceFilterMode.UNMOTIVATED)
        assertEquals(1, unmotivatedOnly.size)
        assertEquals("06.11.2026", unmotivatedOnly.first().dateDisplay)
    }

    @Test
    fun parseRomanianDateToIso_parsesFullRomanianMonthNames() {
        assertEquals("2026-10-06", GradeCalculator.parseRomanianDateToIso("6 octombrie 2026"))
        assertEquals("2026-09-28", GradeCalculator.parseRomanianDateToIso("28 septembrie 2026"))
        assertEquals("2026-11-17", GradeCalculator.parseRomanianDateToIso("17.11.2026"))
    }

    @Test
    fun qualifierSubjects_simulateAndComputeSummaryQualifiers() {
        val pe = SubjectGrades(
            semester = 1,
            subject = "Educație fizică",
            rawEntries = listOf("Foarte bine"),
            datedGrades = listOf(DatedGrade("q1", null, "Foarte bine")),
            numericGrades = emptyList(),
            qualifiers = listOf("Foarte bine"),
            teza = null,
            tezaRaw = null,
            officialAverage = null,
            liveAverage = null
        )

        assertTrue(GradeCalculator.isQualifierSubject(pe))
        assertEquals("Foarte bine", GradeCalculator.calculateQualifierSummary(pe.qualifiers))

        val simulated = GradeCalculator.applySimulatedMarks(
            subject = pe,
            simulatedMarks = emptyList(),
            simulatedQualifiers = listOf("Bine", "Bine")
        )
        assertEquals(listOf("Foarte bine", "Bine", "Bine"), simulated.qualifiers)
        assertEquals(3, simulated.datedGrades.size)
        assertTrue(simulated.datedGrades.last().isSimulated)
        assertEquals("Bine", GradeCalculator.calculateQualifierSummary(simulated.qualifiers))

        val math = SubjectGrades(
            semester = 1,
            subject = "Matematică",
            rawEntries = listOf("10"),
            datedGrades = listOf(DatedGrade("m1", 10, "10")),
            numericGrades = listOf(10),
            qualifiers = emptyList(),
            teza = null,
            tezaRaw = null,
            officialAverage = null,
            liveAverage = 10.0
        )
        assertFalse(GradeCalculator.isQualifierSubject(math))
        assertEquals(SubjectEvaluationType.NUMERIC, GradeCalculator.getEvaluationType(math))

        val edSoc = SubjectGrades(
            semester = 1,
            subject = "Educație pentru societate",
            rawEntries = emptyList(),
            datedGrades = emptyList(),
            numericGrades = emptyList(),
            qualifiers = emptyList(),
            teza = null,
            tezaRaw = null,
            officialAverage = null,
            liveAverage = null
        )
        val dezv = edSoc.copy(subject = "Dezvoltare personală")
        val litUniv = edSoc.copy(subject = "Literatura universală")

        assertEquals(SubjectEvaluationType.QUALIFIER, GradeCalculator.getEvaluationType(pe))
        assertEquals(SubjectEvaluationType.DESCRIPTOR_ONLY, GradeCalculator.getEvaluationType(edSoc))
        assertEquals(SubjectEvaluationType.DESCRIPTOR_ONLY, GradeCalculator.getEvaluationType(dezv))
        assertEquals(SubjectEvaluationType.NUMERIC, GradeCalculator.getEvaluationType(litUniv))
    }
}
