package md.sime.agenda.model

import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object GradeCalculator {

    private val displayDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT)
    private val isoDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun normalizeSubject(subject: String): String {
        val unified = subject
            .trim()
            .replace('ş', 'ș')
            .replace('Ş', 'Ș')
            .replace('ţ', 'ț')
            .replace('Ţ', 'Ț')
            .lowercase(Locale.ROOT)
        val decomposed = Normalizer.normalize(unified, Normalizer.Form.NFD)
        return decomposed.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    fun parseDisplayDateToIso(dateDisplay: String): String? {
        return runCatching {
            LocalDate.parse(dateDisplay.trim(), displayDateFormatter).format(isoDateFormatter)
        }.getOrNull()
    }

    private val romanianMonths = mapOf(
        "ianuarie" to 1,
        "februarie" to 2,
        "martie" to 3,
        "aprilie" to 4,
        "mai" to 5,
        "iunie" to 6,
        "iulie" to 7,
        "august" to 8,
        "septembrie" to 9,
        "octombrie" to 10,
        "noiembrie" to 11,
        "decembrie" to 12
    )

    fun parseRomanianDateToIso(dateText: String): String? {
        val trimmed = dateText.trim()
        parseDisplayDateToIso(trimmed)?.let { return it }

        val parts = trimmed.lowercase(Locale.ROOT).split("\\s+".toRegex())
        if (parts.size < 3) return null
        val day = parts[0].toIntOrNull() ?: return null
        val month = romanianMonths[parts[1]] ?: return null
        val year = parts[2].toIntOrNull() ?: return null
        return runCatching {
            LocalDate.of(year, month, day).format(isoDateFormatter)
        }.getOrNull()
    }

    fun parseDisplayDateToEpochDay(dateDisplay: String): Long {
        return runCatching {
            LocalDate.parse(dateDisplay.trim(), displayDateFormatter).toEpochDay()
        }.getOrDefault(0L)
    }

    fun formatIsoToShortDisplay(dateIso: String): String {
        return runCatching {
            val d = LocalDate.parse(dateIso.trim(), isoDateFormatter)
            String.format(Locale.ROOT, "%02d.%02d", d.dayOfMonth, d.monthValue)
        }.getOrElse { dateIso }
    }

    fun formatDisplayToShort(dateDisplay: String): String {
        val parts = dateDisplay.trim().split('.')
        return if (parts.size >= 2) "${parts[0]}.${parts[1]}" else dateDisplay
    }

    /**
     * Computes live subject average.
     * Standard Moldova rule: arithmetic mean of numeric grades;
     * if a Teză grade exists, average of (mean of current grades) and (Teză).
     */
    fun calculateSubjectAverage(numericGrades: List<Int>, teza: Int?): Double? {
        if (numericGrades.isEmpty() && teza == null) return null
        if (numericGrades.isEmpty()) return teza?.toDouble()
        val mean = numericGrades.average()
        return if (teza != null) {
            (mean + teza.toDouble()) / 2.0
        } else {
            mean
        }
    }

    /**
     * Computes live overall GPA across all subjects with a numeric average,
     * merging any user-simulated grades per subject.
     */
    fun calculateOverallAverage(
        subjects: List<SubjectGrades>,
        simulatedBySubject: Map<String, List<Int>> = emptyMap()
    ): Double? {
        val averages = subjects.mapNotNull { subject ->
            val extra = simulatedBySubject[subject.subject].orEmpty()
            calculateSubjectAverage(subject.numericGrades + extra, subject.teza)
        }
        return if (averages.isEmpty()) null else averages.average()
    }

    /**
     * Produces a live view of SubjectGrades incorporating simulated marks.
     */
    fun applySimulatedMarks(
        subject: SubjectGrades,
        simulatedMarks: List<Int>
    ): SubjectGrades {
        if (simulatedMarks.isEmpty()) {
            val live = calculateSubjectAverage(subject.numericGrades, subject.teza)
            return subject.copy(liveAverage = live)
        }
        val extraDated = simulatedMarks.mapIndexed { idx, value ->
            DatedGrade(
                id = "sim_${subject.subject}_$idx",
                numericValue = value,
                rawText = value.toString(),
                dateDisplay = "Simulat",
                dateIso = null,
                isSimulated = true
            )
        }
        val combinedNums = subject.numericGrades + simulatedMarks
        return subject.copy(
            datedGrades = subject.datedGrades + extraDated,
            numericGrades = combinedNums,
            liveAverage = calculateSubjectAverage(combinedNums, subject.teza)
        )
    }

    private data class DatedOccurrence(
        val subjectKey: String,
        val grade: Int,
        val dateDisplay: String,
        val dateIso: String,
        val periodNumber: Int?,
        val lessonId: String?,
        val detailUrl: String?
    )

    /**
     * Cross-references Reușita marks with dated grade badges from Agenda schedule events
     * and Dashboard's RecentEvaluations so tapping a mark jumps directly to that lesson day.
     */
    fun enrichGradesWithDates(
        subjects: List<SubjectGrades>,
        recentEvaluations: List<RecentEvaluation>,
        scheduleEvents: List<LessonEvent>
    ): List<SubjectGrades> {
        val poolBySubject = mutableMapOf<String, MutableList<DatedOccurrence>>()

        // 1. Add dated lesson events from Agenda FullCalendar
        val seenKeys = mutableSetOf<String>()
        scheduleEvents
            .filter { it.numericGrade != null && it.dateIso.isNotBlank() }
            .sortedWith(compareBy<LessonEvent> { it.dateIso }.thenBy { it.periodNumber })
            .forEach { ev ->
                val subjKey = normalizeSubject(ev.subject)
                val grade = ev.numericGrade ?: return@forEach
                val dedupKey = "$subjKey|${ev.dateDisplay}|$grade|${ev.periodNumber}"
                if (seenKeys.add(dedupKey)) {
                    poolBySubject.getOrPut(subjKey) { mutableListOf() }.add(
                        DatedOccurrence(
                            subjectKey = subjKey,
                            grade = grade,
                            dateDisplay = ev.dateDisplay,
                            dateIso = ev.dateIso,
                            periodNumber = ev.periodNumber,
                            lessonId = ev.id,
                            detailUrl = ev.detailUrl
                        )
                    )
                }
            }

        // 2. Add any RecentEvaluations not already represented in scheduleEvents
        recentEvaluations.forEach { eval ->
            val grade = eval.grade.trim().toIntOrNull() ?: return@forEach
            val subjKey = normalizeSubject(eval.subject)
            val dateIso = parseDisplayDateToIso(eval.date) ?: return@forEach
            val existingList = poolBySubject.getOrPut(subjKey) { mutableListOf() }
            val alreadyHasSameDayAndGrade = existingList.any {
                it.dateDisplay == eval.date.trim() && it.grade == grade
            }
            if (!alreadyHasSameDayAndGrade) {
                existingList.add(
                    DatedOccurrence(
                        subjectKey = subjKey,
                        grade = grade,
                        dateDisplay = eval.date.trim(),
                        dateIso = dateIso,
                        periodNumber = null,
                        lessonId = null,
                        detailUrl = null
                    )
                )
            }
        }

        // Sort each subject's pool chronologically (oldest -> newest)
        poolBySubject.values.forEach { list ->
            list.sortWith(compareBy<DatedOccurrence> { it.dateIso }.thenBy { it.periodNumber ?: 0 })
        }

        return subjects.map { subject ->
            val subjKey = normalizeSubject(subject.subject)
            val available = poolBySubject[subjKey]?.toMutableList() ?: mutableListOf()

            // Reușita lists grades oldest -> newest; match newest -> oldest so recent/known
            // marks align accurately even if earlier semester months haven't been loaded yet.
            val matched = MutableList<DatedGrade?>(subject.datedGrades.size) { null }
            for (i in subject.datedGrades.indices.reversed()) {
                val g = subject.datedGrades[i]
                val num = g.numericValue
                if (num != null) {
                    val matchIdx = available.indexOfLast { it.grade == num }
                    if (matchIdx >= 0) {
                        val occ = available.removeAt(matchIdx)
                        matched[i] = g.copy(
                            dateDisplay = occ.dateDisplay,
                            dateIso = occ.dateIso,
                            periodNumber = occ.periodNumber,
                            lessonId = occ.lessonId,
                            detailUrl = occ.detailUrl
                        )
                        continue
                    }
                }
                matched[i] = g
            }

            subject.copy(
                datedGrades = matched.filterNotNull(),
                liveAverage = calculateSubjectAverage(subject.numericGrades, subject.teza)
            )
        }
    }

    fun groupAndSortAbsencesByDay(
        absences: List<DailyAbsence>,
        sortMode: AbsenceSortMode,
        filterMode: AbsenceFilterMode
    ): List<DayAbsenceGroup> {
        val filtered = when (filterMode) {
            AbsenceFilterMode.ALL -> absences
            AbsenceFilterMode.UNMOTIVATED -> absences.filter { !it.isMotivated }
            AbsenceFilterMode.MOTIVATED -> absences.filter { it.isMotivated }
        }

        val groups = filtered
            .groupBy { it.date }
            .map { (dateDisplay, list) ->
                val sortedItems = list.sortedBy { it.periodNumber.toIntOrNull() ?: Int.MAX_VALUE }
                DayAbsenceGroup(
                    dateDisplay = dateDisplay,
                    dateEpochDay = sortedItems.firstOrNull()?.dateEpochDay
                        ?: parseDisplayDateToEpochDay(dateDisplay),
                    items = sortedItems,
                    motivatedCount = sortedItems.count { it.isMotivated },
                    unmotivatedCount = sortedItems.count { !it.isMotivated }
                )
            }

        return when (sortMode) {
            AbsenceSortMode.NEWEST ->
                groups.sortedByDescending { it.dateEpochDay }
            AbsenceSortMode.OLDEST ->
                groups.sortedBy { it.dateEpochDay }
            AbsenceSortMode.MOST_PER_DAY ->
                groups.sortedWith(
                    compareByDescending<DayAbsenceGroup> { it.items.size }
                        .thenByDescending { it.dateEpochDay }
                )
        }
    }

    fun formatAverage(avg: Double?): String {
        return if (avg == null) "—" else String.format(Locale.US, "%.2f", avg)
    }
}
