package md.sime.agenda.data

import md.sime.agenda.model.CumulativeAbsences
import md.sime.agenda.model.DailyAbsence
import md.sime.agenda.model.DatedGrade
import md.sime.agenda.model.GradeCalculator
import md.sime.agenda.model.HomeworkItem
import md.sime.agenda.model.LessonDetail
import md.sime.agenda.model.LessonEvent
import md.sime.agenda.model.PortalSnapshot
import md.sime.agenda.model.RecentEvaluation
import md.sime.agenda.model.StudentOption
import md.sime.agenda.model.StudentProfile
import md.sime.agenda.model.SubjectAbsenceSummary
import md.sime.agenda.model.SubjectGrades
import md.sime.agenda.model.SubjectTeacher
import md.sime.agenda.model.TeacherMessage
import org.json.JSONArray
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object SimeHtmlParser {

    fun parseMainPage(
        html: String,
        reusitaHtml: String? = null,
        dosarHtml: String? = null,
        absenteHtml: String? = null,
        mesajeHtml: String? = null
    ): PortalSnapshot {
        val doc = Jsoup.parse(html)
        val reusitaDoc = if (!reusitaHtml.isNullOrBlank()) Jsoup.parseBodyFragment(reusitaHtml) else doc
        val dosarDoc = if (!dosarHtml.isNullOrBlank()) Jsoup.parseBodyFragment(dosarHtml) else doc
        val absenteDoc = if (!absenteHtml.isNullOrBlank()) Jsoup.parseBodyFragment(absenteHtml) else doc
        val mesajeDoc = if (!mesajeHtml.isNullOrBlank()) Jsoup.parseBodyFragment(mesajeHtml) else doc

        val sessionId = doc.selectFirst("#pInstance")?.attr("value")?.trim().orEmpty()
        val profile = parseStudentProfile(doc, dosarDoc)
        val studentOptions = parseStudentOptions(doc)
        val presencePercent = parsePresencePercent(doc)
        val homework = parseHomework(doc)
        val recentEvals = parseRecentEvaluations(doc)
        val (replacementsText, hasReplacements) = parseReplacements(doc)

        val sem1Raw = parseSemesterGrades(reusitaDoc, "#report_table_sem1", semester = 1)
        val sem2Raw = parseSemesterGrades(reusitaDoc, "#report_table_sem2", semester = 2)

        val sem1Enriched = GradeCalculator.enrichGradesWithDates(sem1Raw, recentEvals, emptyList())
        val sem2Enriched = GradeCalculator.enrichGradesWithDates(sem2Raw, recentEvals, emptyList())

        val dailyAbsences = parseDailyAbsences(absenteDoc)
        val subjectAbsences = parseSubjectAbsences(absenteDoc)
        val cumulativeAbsences = parseCumulativeAbsences(doc, absenteDoc, dailyAbsences, subjectAbsences)
        val messages = parseMessages(mesajeDoc)
        val teachers = parseTeachers(dosarDoc)

        return PortalSnapshot(
            updatedAtMillis = System.currentTimeMillis(),
            sessionId = sessionId,
            profile = profile,
            studentOptions = studentOptions,
            presencePercent = presencePercent,
            homeworkItems = homework,
            recentEvaluations = recentEvals,
            replacementsText = replacementsText,
            hasReplacements = hasReplacements,
            sem1Grades = sem1Enriched,
            sem2Grades = sem2Enriched,
            dailyAbsences = dailyAbsences,
            subjectAbsences = subjectAbsences,
            cumulativeAbsences = cumulativeAbsences,
            messages = messages,
            teachers = teachers
        )
    }

    private fun parseStudentProfile(doc: Document, dosarDoc: Document): StudentProfile {
        val fullName = dosarDoc.selectFirst("#student-name")?.text()?.trim()
            ?: doc.selectFirst("#student-name")?.text()?.trim()
            ?: hiddenValue(doc, "P1_ELEVE")
        val shortName = doc.selectFirst(".t-Header-nav .student-name-short, .user-name, .student-pill")
            ?.text()?.trim().orEmpty()
            .ifBlank { toTitleCase(fullName) }

        val infoMap = mutableMapOf<String, String>()
        dosarDoc.select(".info-item").ifEmpty { doc.select("#dosarRegion .info-item") }.forEach { item ->
            val label = item.selectFirst("b")?.text()?.trim()?.removeSuffix(":")?.lowercase().orEmpty()
            val fullText = item.text().trim()
            val value = fullText.substringAfter(":", "").trim()
            if (label.isNotEmpty() && value.isNotEmpty()) {
                infoMap[label] = value
            }
        }

        val birthDate = infoMap.entries.firstOrNull { it.key.contains("nașter") || it.key.contains("naster") }?.value
            ?: hiddenValue(doc, "P1_BIRTH_DATE")
        val permanentCode = infoMap.entries.firstOrNull { it.key.contains("cod") }?.value
            ?: hiddenValue(doc, "P1_ELEVE_UK")
        val className = infoMap.entries.firstOrNull { it.key.contains("clas") }?.value
            ?: hiddenValue(doc, "P1_CLASS_NAME")
        val homeroomTeacher = infoMap.entries.firstOrNull { it.key.contains("dirig") }?.value
            ?: hiddenValue(doc, "P1_DIRIGINTE")

        val schoolName = hiddenValue(doc, "P1_INSTITUTION_NAME").ifBlank {
            doc.selectFirst("footer .t-Footer-version, .t-Footer-body, footer")?.text()
                ?.lines()?.firstOrNull { it.isNotBlank() && !it.contains("Contacte", ignoreCase = true) }
                ?.trim().orEmpty()
        }

        val initials = fullName.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifBlank { "AE" }

        return StudentProfile(
            fullName = fullName,
            shortName = shortName.ifBlank { fullName },
            initials = initials,
            birthDate = birthDate,
            permanentCode = permanentCode,
            className = className,
            homeroomTeacher = homeroomTeacher,
            schoolName = schoolName,
            schoolAddress = hiddenValue(doc, "P1_ADDRESS"),
            schoolPhone = hiddenValue(doc, "P1_PHONE"),
            schoolWebsite = hiddenValue(doc, "P1_WEBSITE"),
            schoolDirector = hiddenValue(doc, "P1_DIRECTOR")
        )
    }

    private fun parseStudentOptions(doc: Document): List<StudentOption> {
        return doc.select(".t-TreeNav a, nav[aria-label='Application'] a").mapNotNull { link ->
            val href = link.attr("href").trim()
            val name = link.text().trim()
            if (name.isBlank() || href.isBlank()) return@mapNotNull null
            val id = Regex("user_eleve_id=([A-Za-z0-9]+)").find(href)?.groupValues?.get(1) ?: name
            val selected = link.closest("li")?.hasClass("is-current") == true ||
                link.attr("aria-selected") == "true"
            StudentOption(id = id, name = name, switchUrl = href, isSelected = selected)
        }
    }

    private fun parsePresencePercent(doc: Document): Int {
        val hidden = hiddenValue(doc, "P1_PRESENCE_PERCENT").replace("%", "").trim().toIntOrNull()
        if (hidden != null) return hidden
        val styleWidth = doc.selectFirst(".presence-fill")?.attr("style").orEmpty()
        Regex("width\\s*:\\s*(\\d+)%").find(styleWidth)?.groupValues?.get(1)?.toIntOrNull()?.let {
            return it
        }
        val text = doc.selectFirst("#grades")?.text().orEmpty()
        return Regex("Prezență\\s*:\\s*(\\d+)%").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun parseHomework(doc: Document): List<HomeworkItem> {
        val list = doc.selectFirst("#homework_list") ?: return emptyList()
        val items = mutableListOf<HomeworkItem>()
        var currentDate = ""
        list.children().forEach { child ->
            when (child.tagName().lowercase()) {
                "b", "strong" -> currentDate = child.text().trim()
                "li" -> {
                    val spans = child.select("span")
                    val subject: String
                    val text: String
                    if (spans.size >= 2) {
                        subject = spans[0].text().trim().removeSuffix(":").trim()
                        text = spans.drop(1).joinToString(" ") { it.text().trim() }.trim()
                    } else {
                        val raw = child.text().trim()
                        subject = raw.substringBefore(":", "").trim()
                        text = raw.substringAfter(":", raw).trim()
                    }
                    if (subject.isNotBlank() || text.isNotBlank()) {
                        val id = "${currentDate}_${subject}_${text.hashCode()}"
                        items.add(HomeworkItem(id = id, date = currentDate, subject = subject, text = text))
                    }
                }
            }
        }
        return items
    }

    private fun parseRecentEvaluations(doc: Document): List<RecentEvaluation> {
        return doc.select("#last3-eval li").mapNotNull { li ->
            val date = li.selectFirst(".eval-date")?.text()?.trim().orEmpty()
            val subject = li.selectFirst(".eval-discipline")?.text()?.trim()?.removeSuffix(":")?.trim().orEmpty()
            val grade = li.selectFirst(".grade")?.text()?.trim().orEmpty()
            if (subject.isBlank() && grade.isBlank()) null
            else RecentEvaluation(date = date, subject = subject, grade = grade)
        }
    }

    private fun parseReplacements(doc: Document): Pair<String, Boolean> {
        val container = doc.selectFirst("#replacements") ?: return "" to false
        val empty = container.selectFirst(".empty-replacements")
        return if (empty != null) {
            empty.text().trim() to false
        } else {
            val txt = container.text().trim()
            txt to txt.isNotBlank()
        }
    }

    private fun parseSemesterGrades(
        doc: Document,
        tableSelector: String,
        semester: Int
    ): List<SubjectGrades> {
        val table = doc.selectFirst(tableSelector)
            ?: doc.selectFirst("#sem$semester table.t-Report-report, #report_sem$semester table")
            ?: return emptyList()
        return table.select("tbody tr").mapNotNull { row ->
            val subject = row.selectFirst("td[headers='DISCIPLINA']")?.text()?.trim()
                ?: row.select("td").getOrNull(0)?.text()?.trim()
                ?: return@mapNotNull null
            if (subject.isBlank()) return@mapNotNull null

            val listaCell = row.selectFirst("td[headers^='LISTA_EVALUATION']")
                ?: row.select("td").getOrNull(1)
            val tezaCell = row.selectFirst("td[headers^='TEZA']")
                ?: row.select("td").getOrNull(2)
            val medCell = row.selectFirst("td[headers^='MED_SEM']")
                ?: row.select("td").getOrNull(3)

            val rawGradesText = listaCell?.text()?.trim().orEmpty()
            val entries = if (rawGradesText.isBlank()) emptyList()
            else rawGradesText.split(",").map { it.trim() }.filter { it.isNotBlank() }

            val numericGrades = mutableListOf<Int>()
            val qualifiers = mutableListOf<String>()
            val datedGrades = entries.mapIndexed { idx, token ->
                val num = token.toIntOrNull()
                if (num != null) {
                    numericGrades.add(num)
                } else {
                    qualifiers.add(token)
                }
                DatedGrade(
                    id = "s${semester}_${subject}_${idx}_$token",
                    numericValue = num,
                    rawText = token
                )
            }

            val tezaRaw = tezaCell?.text()?.trim()?.takeIf { it.isNotBlank() }
            val teza = tezaRaw?.toIntOrNull()
            val officialAvg = medCell?.text()?.trim()?.takeIf { it.isNotBlank() }
            val liveAvg = GradeCalculator.calculateSubjectAverage(numericGrades, teza)

            SubjectGrades(
                semester = semester,
                subject = subject,
                rawEntries = entries,
                datedGrades = datedGrades,
                numericGrades = numericGrades,
                qualifiers = qualifiers,
                teza = teza,
                tezaRaw = tezaRaw,
                officialAverage = officialAvg,
                liveAverage = liveAvg
            )
        }
    }

    private fun parseDailyAbsences(doc: Document): List<DailyAbsence> {
        val results = mutableListOf<DailyAbsence>()
        doc.select(".absence-report-container .month-card").forEach { card ->
            val monthTitle = card.selectFirst(".month-header h2")?.text()?.trim().orEmpty()
            card.select(".absence-field").forEach { field ->
                val map = mutableMapOf<String, Element>()
                field.select(".field-group").forEach { group ->
                    val title = group.selectFirst(".field-title")?.text()?.trim()
                        ?.removeSuffix(":")?.lowercase().orEmpty()
                    val valEl = group.selectFirst(".field-value")
                    if (title.isNotBlank() && valEl != null) {
                        map[title] = valEl
                    }
                }
                val date = map.entries.firstOrNull { it.key.contains("data") }?.value?.text()?.trim().orEmpty()
                val lesson = map.entries.firstOrNull {
                    it.key.contains("lec") && !it.key.contains("data")
                }?.value?.text()?.trim().orEmpty()
                val discipline = map.entries.firstOrNull { it.key.contains("disciplin") }?.value?.text()?.trim().orEmpty()
                val absenceEl = map.entries.firstOrNull { it.key == "absență" || it.key == "absenta" }?.value
                val absenceText = absenceEl?.text()?.trim().orEmpty()
                val isMotivated = absenceEl?.hasClass("motivat") == true ||
                    (absenceText.contains("motivat", ignoreCase = true) &&
                        !absenceText.contains("nemotivat", ignoreCase = true))
                val cumulative = map.entries.firstOrNull { it.key.contains("cumulativ") }
                    ?.value?.text()?.trim()?.toIntOrNull() ?: 0

                if (date.isNotBlank() || discipline.isNotBlank()) {
                    results.add(
                        DailyAbsence(
                            date = date,
                            dateEpochDay = GradeCalculator.parseDisplayDateToEpochDay(date),
                            periodNumber = lesson,
                            subject = discipline,
                            isMotivated = isMotivated,
                            cumulativeCount = cumulative,
                            monthTitle = monthTitle
                        )
                    )
                }
            }
        }
        return results
    }

    private fun parseSubjectAbsences(doc: Document): List<SubjectAbsenceSummary> {
        val tabPanel = doc.select("[role='tabpanel']").getOrNull(1)
            ?: doc.selectFirst("#R988934330764996473")
            ?: doc
        val outerTable = tabPanel.selectFirst("table[aria-label='Discipline școlare']")
            ?: tabPanel.selectFirst("table.t-Report-report, table")
            ?: return emptyList()

        val summaries = mutableListOf<SubjectAbsenceSummary>()

        val rows = outerTable.select("> tbody > tr")
        val seenSubjects = mutableSetOf<String>()
        rows.forEach { tr ->
            val text = tr.text().replace("\\s+".toRegex(), " ").trim()
            if (!text.contains("Semestrul 1", ignoreCase = true)) return@forEach

            val subject = text.substringBefore("Semestru", "").trim()
            if (subject.isBlank() || !seenSubjects.add(subject)) return@forEach

            val sem1Match = Regex("Semestrul\\s*1\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)").find(text)
            val sem2Match = Regex("Semestrul\\s*2\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)").find(text)
            if (sem1Match != null) {
                summaries.add(
                    SubjectAbsenceSummary(
                        subject = subject,
                        sem1Motivated = sem1Match.groupValues[1].toIntOrNull() ?: 0,
                        sem1Unmotivated = sem1Match.groupValues[2].toIntOrNull() ?: 0,
                        sem1Total = sem1Match.groupValues[3].toIntOrNull() ?: 0,
                        sem2Motivated = sem2Match?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                        sem2Unmotivated = sem2Match?.groupValues?.get(2)?.toIntOrNull() ?: 0,
                        sem2Total = sem2Match?.groupValues?.get(3)?.toIntOrNull() ?: 0
                    )
                )
            }
        }
        return summaries
    }

    private fun parseCumulativeAbsences(
        doc: Document,
        absenteDoc: Document,
        dailyAbsences: List<DailyAbsence>,
        subjectAbsences: List<SubjectAbsenceSummary>
    ): CumulativeAbsences {
        val m1 = hiddenValue(doc, "P1_MOTIVATED").toIntOrNull()
        val u1 = hiddenValue(doc, "P1_UNMOTIVATED").toIntOrNull()
        val t1 = hiddenValue(doc, "P1_TOTAL_ABSENCES1").toIntOrNull()
        val m2 = hiddenValue(doc, "P1_MOTIVATED2").toIntOrNull()
        val u2 = hiddenValue(doc, "P1_UNMOTIVATED2").toIntOrNull()
        val t2 = hiddenValue(doc, "P1_TOTAL_ABSENCES2").toIntOrNull()

        if (m1 != null || u1 != null || t1 != null) {
            val mot1 = m1 ?: 0
            val unmot1 = u1 ?: 0
            val mot2 = m2 ?: 0
            val unmot2 = u2 ?: 0
            return CumulativeAbsences(
                sem1Motivated = mot1,
                sem1Unmotivated = unmot1,
                sem1Total = t1 ?: (mot1 + unmot1),
                sem2Motivated = mot2,
                sem2Unmotivated = unmot2,
                sem2Total = t2 ?: (mot2 + unmot2)
            )
        }

        val summaryCardText = absenteDoc.selectFirst("#custom-tabs-blue")?.text()
            ?: absenteDoc.text()
        val motMatch = Regex("Motivate\\s+(\\d+)\\s+(\\d+)").find(summaryCardText)
        val unmotMatch = Regex("Nemotivate\\s+(\\d+)\\s+(\\d+)").find(summaryCardText)
        val totMatch = Regex("Total\\s+(\\d+)\\s+(\\d+)").find(summaryCardText)
        if (motMatch != null && unmotMatch != null && totMatch != null) {
            return CumulativeAbsences(
                sem1Motivated = motMatch.groupValues[1].toIntOrNull() ?: 0,
                sem2Motivated = motMatch.groupValues[2].toIntOrNull() ?: 0,
                sem1Unmotivated = unmotMatch.groupValues[1].toIntOrNull() ?: 0,
                sem2Unmotivated = unmotMatch.groupValues[2].toIntOrNull() ?: 0,
                sem1Total = totMatch.groupValues[1].toIntOrNull() ?: 0,
                sem2Total = totMatch.groupValues[2].toIntOrNull() ?: 0
            )
        }

        if (subjectAbsences.isNotEmpty()) {
            return CumulativeAbsences(
                sem1Motivated = subjectAbsences.sumOf { it.sem1Motivated },
                sem1Unmotivated = subjectAbsences.sumOf { it.sem1Unmotivated },
                sem1Total = subjectAbsences.sumOf { it.sem1Total },
                sem2Motivated = subjectAbsences.sumOf { it.sem2Motivated },
                sem2Unmotivated = subjectAbsences.sumOf { it.sem2Unmotivated },
                sem2Total = subjectAbsences.sumOf { it.sem2Total }
            )
        }

        val mot = dailyAbsences.count { it.isMotivated }
        val unmot = dailyAbsences.count { !it.isMotivated }
        return CumulativeAbsences(
            sem1Motivated = mot,
            sem1Unmotivated = unmot,
            sem1Total = mot + unmot
        )
    }

    private fun parseMessages(doc: Document): List<TeacherMessage> {
        val container = doc.selectFirst("#messageContainer") ?: doc
        return container.select(".card").mapNotNull { card ->
            val date = card.selectFirst(".date")?.text()?.trim().orEmpty()
            val prof = card.selectFirst(".from")?.text()?.trim().orEmpty()
            val subject = card.selectFirst(".card-discipline")?.text()?.trim().orEmpty()
            val text = card.selectFirst(".card-text")?.text()?.trim().orEmpty()
            if (prof.isBlank() && text.isBlank()) return@mapNotNull null
            val id = "${date}_${prof}_${subject}_${text.hashCode()}"
            TeacherMessage(
                id = id,
                date = date,
                professor = prof,
                subject = subject,
                text = text
            )
        }
    }

    private fun parseTeachers(doc: Document): List<SubjectTeacher> {
        val table = doc.selectFirst("#R546889360489434893 table") ?: doc.selectFirst("table") ?: return emptyList()
        return table.select("tbody tr").mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size < 2) return@mapNotNull null
            val subject = cells[0].text().trim()
            val teacher = cells[1].text().trim()
            if (subject.isBlank() && teacher.isBlank()) null
            else SubjectTeacher(subject = subject, teacherName = teacher)
        }
    }

    fun parseCalendarEventsJson(jsonArrayStr: String): List<LessonEvent> {
        val trimmed = jsonArrayStr.trim()
        if (!trimmed.startsWith("[")) return emptyList()
        val array = JSONArray(trimmed)
        val events = mutableListOf<LessonEvent>()

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val start = obj.optString("start")
            val end = obj.optString("end")
            val titleHtml = obj.optString("title")
            val rawUrl = obj.optString("url")

            val dateIso = start.substringBefore("T")
            val dateParts = dateIso.split("-")
            val dateDisplay = if (dateParts.size == 3) {
                "${dateParts[2]}.${dateParts[1]}.${dateParts[0]}"
            } else {
                dateIso
            }

            val cardDoc = Jsoup.parseBodyFragment(titleHtml)
            val order = cardDoc.selectFirst(".lesson-order")?.text()?.trim()?.toIntOrNull() ?: (i + 1)
            val times = cardDoc.select(".lesson-time span").map { it.text().trim() }
            val startTime = times.getOrNull(0) ?: start.substringAfter("T", "").take(5)
            val endTime = times.getOrNull(1) ?: end.substringAfter("T", "").take(5)
            val subject = cardDoc.selectFirst(".lesson-text-wrap")?.text()?.trim().orEmpty()
            val room = cardDoc.selectFirst(".lesson-room")?.text()?.trim()?.takeIf { it.isNotBlank() }
            val teacher = cardDoc.selectFirst(".lesson-teacher")?.text()?.trim().orEmpty()
            val homework = cardDoc.selectFirst(".lesson-homework")?.text()?.trim()?.takeIf { it.isNotBlank() }
            val gradeBadge = cardDoc.selectFirst(".lesson-grade")?.text()?.trim()?.takeIf { it.isNotBlank() }

            val numericGrade = gradeBadge?.toIntOrNull()
            val isAbsent = gradeBadge?.equals("a", ignoreCase = true) == true

            val detailUrl = extractDialogUrl(rawUrl)
            val id = "${dateIso}_${order}_$subject"

            events.add(
                LessonEvent(
                    id = id,
                    dateIso = dateIso,
                    dateDisplay = dateDisplay,
                    periodNumber = order,
                    startTime = startTime,
                    endTime = endTime,
                    subject = subject,
                    room = room,
                    teacher = teacher,
                    homeworkSnippet = homework,
                    badgeText = gradeBadge,
                    numericGrade = numericGrade,
                    isAbsent = isAbsent,
                    detailUrl = detailUrl
                )
            )
        }
        return events.sortedWith(compareBy<LessonEvent> { it.dateIso }.thenBy { it.periodNumber })
    }

    fun extractDialogUrl(rawJsUrl: String): String? {
        if (rawJsUrl.isBlank()) return null
        if (rawJsUrl.startsWith("http")) return rawJsUrl
        val match = Regex("apex\\.theme42\\.dialog\\(\\s*'([^']+)'").find(rawJsUrl)
            ?: return null
        val decoded = match.groupValues[1]
            .replace("\\u002F", "/")
            .replace("\\u0026", "&")
            .replace("\\u003D", "=")
        return when {
            decoded.startsWith("http") -> decoded
            decoded.startsWith("/") -> "https://agenda.sime.md$decoded"
            else -> "https://agenda.sime.md/ords/r/agenda/main/$decoded"
        }
    }

    fun parseLessonDetailPage(html: String): LessonDetail {
        val doc = Jsoup.parse(html)
        val period = doc.selectFirst(".lesson-hero-badge")?.text()?.trim()?.toIntOrNull()
            ?: hiddenValue(doc, "P19_LESSON_ORDER_ID").toIntOrNull() ?: 0
        val subject = doc.selectFirst(".lesson-hero-title")?.text()?.trim()
            ?: hiddenValue(doc, "P19_DISCIPLINY_NAME")
        val date = doc.selectFirst(".lesson-hero-date span")?.text()?.trim()
            ?: hiddenValue(doc, "P19_LESSON_DATE")

        val sections = doc.select(".lesson-view .card-section")
        var topic = ""
        var teacherMsg = ""
        var evaluation: String? = null
        var homework = ""
        var hwDueDate: String? = null
        var hwCreatedDate: String? = null

        sections.forEach { sec ->
            val heading = sec.selectFirst("h3")?.text()?.trim()?.lowercase().orEmpty()
            val bodyText = sec.selectFirst(".lesson-text")?.text()?.trim()
                ?: sec.select("ul").text().trim()
            when {
                heading.contains("subiect") -> topic = bodyText
                heading.contains("mesaj") -> {
                    // This card also holds "Nota / absență" on the right
                    val rawMsg = sec.selectFirst(".lesson-text")?.text()?.trim()
                        ?: sec.selectFirst("p")?.text()?.trim().orEmpty()
                    teacherMsg = if (rawMsg.equals("Nota / absență", ignoreCase = true)) "" else rawMsg
                    val gradeChip = sec.select(".nota-badge, .grade, .evaluation-badge, span")
                        .firstOrNull { el ->
                            val t = el.text().trim()
                            t.matches(Regex("^(\\d{1,2}|a|A|Foarte bine|Bine|Suficient|Insuficient)$"))
                        }?.text()?.trim()
                    if (!gradeChip.isNullOrBlank()) evaluation = gradeChip
                }
                heading.contains("activit") -> {
                    sec.select("li").forEach { li ->
                        val t = li.text().trim()
                        if (t.startsWith("Pentru data de:", ignoreCase = true)) {
                            hwDueDate = t.substringAfter(":").trim().takeIf { it.isNotBlank() }
                        } else if (t.startsWith("Data complet", ignoreCase = true)) {
                            hwCreatedDate = t.substringAfter(":").trim().takeIf { it.isNotBlank() }
                        }
                    }
                    homework = sec.selectFirst(".lesson-text, .homework-text")?.text()?.trim()
                        ?: hiddenValue(doc, "P19_HOME_WORK_TXT")
                }
            }
        }

        if (topic.isBlank()) topic = hiddenValue(doc, "P19_LESSON_SUBJECT")
        if (homework.isBlank()) homework = hiddenValue(doc, "P19_HOME_WORK_TXT")
        if (evaluation.isNullOrBlank()) evaluation = hiddenValue(doc, "P19_EVALUATION").takeIf { it.isNotBlank() }

        val meetingLink = hiddenValue(doc, "P19_MEETING_ROOM_LINK").takeIf { it.isNotBlank() }
            ?: doc.selectFirst(".lesson-view a[href^='http']")?.attr("href")

        val isSummative = hiddenValue(doc, "P19_SUMATIVA").equals("1", ignoreCase = true) ||
            hiddenValue(doc, "P19_SUMATIVA").equals("Y", ignoreCase = true) ||
            doc.text().contains("sumativ", ignoreCase = true)
        val isTeza = hiddenValue(doc, "P19_TEZA_FLG").equals("1", ignoreCase = true) ||
            hiddenValue(doc, "P19_TEZA_FLG").equals("Y", ignoreCase = true)

        return LessonDetail(
            subject = subject,
            periodNumber = period,
            date = date,
            topic = topic,
            teacherMessage = teacherMsg,
            evaluation = evaluation,
            homework = homework,
            homeworkDueDate = hwDueDate,
            homeworkCreatedDate = hwCreatedDate,
            isSummative = isSummative,
            isTeza = isTeza,
            meetingLink = meetingLink
        )
    }

    private fun hiddenValue(doc: Document, id: String): String {
        return doc.selectFirst("#$id, input[name='$id']")?.attr("value")?.trim().orEmpty()
    }

    private fun toTitleCase(input: String): String {
        return input.lowercase()
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercaseChar() }
            }
    }
}
