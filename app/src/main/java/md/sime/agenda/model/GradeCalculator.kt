package md.sime.agenda.model

import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object GradeCalculator {

    private val displayDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT)
    private val isoDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private val qualifierSubjectKeywords = listOf(
        "educatie fizica",
        "dezvoltare personala",
        "educatie pentru societate",
        "educatie muzicala",
        "educatie plastica",
        "educatie tehnologica"
    )

    fun isQualifierSubject(subject: SubjectGrades): Boolean {
        if (subject.qualifiers.isNotEmpty() && subject.numericGrades.isEmpty()) return true
        if (subject.numericGrades.isNotEmpty()) return false
        val norm = normalizeSubject(subject.subject)
        return qualifierSubjectKeywords.any { norm.contains(it) }
    }

    fun canonicalQualifier(raw: String): String? {
        val norm = normalizeSubject(raw)
        return when {
            norm == "fb" || norm.contains("foarte bine") -> "Foarte bine"
            norm == "b" || norm == "bine" -> "Bine"
            norm == "s" || norm == "suficient" -> "Suficient"
            norm == "i" || norm.contains("insuficient") || norm.contains("nesuficient") -> "Insuficient"
            else -> null
        }
    }

    fun calculateQualifierSummary(
        qualifiers: List<String>,
        officialAverage: String? = null
    ): String? {
        val points = qualifiers.mapNotNull { q ->
            when (canonicalQualifier(q)) {
                "Foarte bine" -> 4
                "Bine" -> 3
                "Suficient" -> 2
                "Insuficient" -> 1
                else -> null
            }
        }
        if (points.isEmpty()) {
            return officialAverage?.takeIf { it.isNotBlank() }
        }
        return when (points.average().roundToInt().coerceIn(1, 4)) {
            4 -> "Foarte bine"
            3 -> "Bine"
            2 -> "Suficient"
            else -> "Insuficient"
        }
    }

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
     * Produces a live view of SubjectGrades incorporating simulated marks and qualifiers.
     */
    fun applySimulatedMarks(
        subject: SubjectGrades,
        simulatedMarks: List<Int>,
        simulatedQualifiers: List<String> = emptyList()
    ): SubjectGrades {
        if (simulatedMarks.isEmpty() && simulatedQualifiers.isEmpty()) {
            val live = calculateSubjectAverage(subject.numericGrades, subject.teza)
            return subject.copy(liveAverage = live)
        }
        val extraNumeric = simulatedMarks.mapIndexed { idx, value ->
            DatedGrade(
                id = "sim_${subject.subject}_$idx",
                numericValue = value,
                rawText = value.toString(),
                dateDisplay = "Simulat",
                dateIso = null,
                isSimulated = true
            )
        }
        val extraQualifiers = simulatedQualifiers.mapIndexed { idx, qualifier ->
            DatedGrade(
                id = "simq_${subject.subject}_$idx",
                numericValue = null,
                rawText = qualifier,
                dateDisplay = "Simulat",
                dateIso = null,
                isSimulated = true
            )
        }
        val combinedNums = subject.numericGrades + simulatedMarks
        val combinedQuals = subject.qualifiers + simulatedQualifiers
        return subject.copy(
            datedGrades = subject.datedGrades + extraNumeric + extraQualifiers,
            numericGrades = combinedNums,
            qualifiers = combinedQuals,
            liveAverage = calculateSubjectAverage(combinedNums, subject.teza)
        )
    }

    private data class DatedOccurrence(
        val subjectKey: String,
        val grade: Int?,
        val qualifier: String?,
        val dateDisplay: String,
        val dateIso: String,
        val periodNumber: Int?,
        val lessonId: String?,
        val detailUrl: String?
    )

    /**
     * Cross-references Reușita marks & qualifiers with dated badges from Agenda schedule events
     * and Dashboard's RecentEvaluations so tapping a mark or qualifier jumps to that lesson day.
     */
    fun enrichGradesWithDates(
        subjects: List<SubjectGrades>,
        recentEvaluations: List<RecentEvaluation>,
        scheduleEvents: List<LessonEvent>
    ): List<SubjectGrades> {
        val poolBySubject = mutableMapOf<String, MutableList<DatedOccurrence>>()

        // 1. Add dated lesson events from Agenda FullCalendar (numeric grades & qualifiers)
        val seenKeys = mutableSetOf<String>()
        scheduleEvents
            .filter { it.dateIso.isNotBlank() && !it.isAbsent && (it.numericGrade != null || !it.badgeText.isNullOrBlank()) }
            .sortedWith(compareBy<LessonEvent> { it.dateIso }.thenBy { it.periodNumber })
            .forEach { ev ->
                val subjKey = normalizeSubject(ev.subject)
                val grade = ev.numericGrade
                val qualifier = if (grade == null) ev.badgeText?.let { canonicalQualifier(it) } else null
                if (grade == null && qualifier == null) return@forEach
                val tokenKey = grade?.toString() ?: qualifier.orEmpty()
                val dedupKey = "$subjKey|${ev.dateDisplay}|$tokenKey|${ev.periodNumber}"
                if (seenKeys.add(dedupKey)) {
                    poolBySubject.getOrPut(subjKey) { mutableListOf() }.add(
                        DatedOccurrence(
                            subjectKey = subjKey,
                            grade = grade,
                            qualifier = qualifier,
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
            val rawGrade = eval.grade.trim()
            val grade = rawGrade.toIntOrNull()
            val qualifier = if (grade == null) canonicalQualifier(rawGrade) else null
            if (grade == null && qualifier == null) return@forEach
            val subjKey = normalizeSubject(eval.subject)
            val dateIso = parseDisplayDateToIso(eval.date) ?: return@forEach
            val existingList = poolBySubject.getOrPut(subjKey) { mutableListOf() }
            val alreadyExists = existingList.any {
                it.dateDisplay == eval.date.trim() &&
                    ((grade != null && it.grade == grade) || (qualifier != null && it.qualifier == qualifier))
            }
            if (!alreadyExists) {
                existingList.add(
                    DatedOccurrence(
                        subjectKey = subjKey,
                        grade = grade,
                        qualifier = qualifier,
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

            // Match newest -> oldest so known marks/qualifiers align accurately
            val matched = MutableList<DatedGrade?>(subject.datedGrades.size) { null }
            for (i in subject.datedGrades.indices.reversed()) {
                val g = subject.datedGrades[i]
                val num = g.numericValue
                val qual = canonicalQualifier(g.rawText)
                val matchIdx = when {
                    num != null -> available.indexOfLast { it.grade == num }
                    qual != null -> available.indexOfLast { it.qualifier == qual }
                    else -> -1
                }
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
