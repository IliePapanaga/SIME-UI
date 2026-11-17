package md.sime.agenda

import md.sime.agenda.data.SimeHtmlParser
import md.sime.agenda.model.GradeCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimeHtmlParserTest {

    private val sampleMainPageHtml = """
        <html>
        <body>
        <input type="hidden" id="pInstance" value="25280474488139">
        <input type="hidden" id="P1_PRESENCE_PERCENT" value="78">
        <input type="hidden" id="P1_INSTITUTION_NAME" value="Instituția Publică Liceul Teoretic „Spiru Haret”">
        <input type="hidden" id="P1_ADDRESS" value="Maria Cebotari,53">
        <input type="hidden" id="P1_MOTIVATED" value="70">
        <input type="hidden" id="P1_UNMOTIVATED" value="6">
        <input type="hidden" id="P1_TOTAL_ABSENCES1" value="76">
        <input type="hidden" id="P1_MOTIVATED2" value="0">
        <input type="hidden" id="P1_UNMOTIVATED2" value="0">
        <input type="hidden" id="P1_TOTAL_ABSENCES2" value="0">
        <nav aria-label="Application">
          <div class="t-TreeNav">
            <ul>
              <li class="is-current"><a href="/ords/r/agenda/main/main-page?user_eleve_id=5BAC16690A8DA915E0630E206A0A4241&clear=1&session=25280474488139">PAPANAGA ILIE</a></li>
            </ul>
          </div>
        </nav>
        <div id="RegBadge"></div>
        <ul id="homework_list">
          <b>18.11.2026</b>
          <li><span>Limba engleză:</span> <span>Make a poster according to Ex. 16, p. 35.</span></li>
        </ul>
        <ul id="last3-eval">
          <li><span class="eval-date">17.11.2026</span> <span class="eval-discipline">Informatica:</span> <span class="grade">10</span></li>
          <li><span class="eval-date">12.11.2026</span> <span class="eval-discipline">Geografie:</span> <span class="grade">10</span></li>
          <li><span class="eval-date">09.11.2026</span> <span class="eval-discipline">Chimie:</span> <span class="grade">10</span></li>
        </ul>
        <div id="replacements">
          <div class="empty-replacements"><div class="empty-text">Nu există lecții înlocuite pentru perioada curentă</div></div>
        </div>
        <div id="dosarRegion">
          <div class="student-folder">
            <div id="student-name">PAPANAGA ILIE</div>
            <div class="student-info">
              <div class="info-item"><span><b>Data nașterii:</b> 20.07.2009</span></div>
              <div class="info-item"><span><b>Cod permanent:</b> EC05927108</span></div>
              <div class="info-item"><span><b>Clasa:</b> 11C</span></div>
              <div class="info-item"><span><b>Diriginte:</b> ȚÎGANAȘ ODETTA</span></div>
            </div>
          </div>
          <div id="R546889360489434893">
            <table>
              <tbody>
                <tr><td>Biologie</td><td>Țîganaș Odetta</td></tr>
                <tr><td>Chimie</td><td>Prunici Ludmila</td></tr>
              </tbody>
            </table>
          </div>
        </div>
        <table id="report_table_sem1">
          <tbody>
            <tr><td headers="DISCIPLINA">Biologie</td><td headers="LISTA_EVALUATION1">10, 10</td><td headers="TEZA1"></td><td headers="MED_SEM1"></td></tr>
            <tr><td headers="DISCIPLINA">Chimie</td><td headers="LISTA_EVALUATION1">10, 9, 10, 10</td><td headers="TEZA1"></td><td headers="MED_SEM1"></td></tr>
            <tr><td headers="DISCIPLINA">Geografie</td><td headers="LISTA_EVALUATION1">9, 10</td><td headers="TEZA1"></td><td headers="MED_SEM1"></td></tr>
            <tr><td headers="DISCIPLINA">Educație fizică</td><td headers="LISTA_EVALUATION1">Foarte bine</td><td headers="TEZA1"></td><td headers="MED_SEM1"></td></tr>
          </tbody>
        </table>
        <div id="custom-tabs-blue">
          <div role="tabpanel">
            <div class="absence-report-container">
              <div class="month-card">
                <div class="month-header"><h2>Octombrie 2026</h2></div>
                <div class="month-content">
                  <div class="absence-field highlight">
                    <div class="field-group"><span class="field-title">Data lecției:</span><span class="field-value">05.10.2026</span></div>
                    <div class="field-group"><span class="field-title">Lecția:</span><span class="field-value">2</span></div>
                    <div class="field-group"><span class="field-title">Disciplina:</span><span class="field-value">Fizica. Astronomia</span></div>
                    <div class="field-group"><span class="field-title">Absență:</span><span class="field-value motivat">motivat</span></div>
                    <div class="field-group"><span class="field-title">Absențecumulative:</span><span class="field-value">54</span></div>
                  </div>
                </div>
              </div>
              <div class="month-card">
                <div class="month-header"><h2>Noiembrie 2026</h2></div>
                <div class="month-content">
                  <div class="absence-field highlight">
                    <div class="field-group"><span class="field-title">Data lecției:</span><span class="field-value">06.11.2026</span></div>
                    <div class="field-group"><span class="field-title">Lecția:</span><span class="field-value">1</span></div>
                    <div class="field-group"><span class="field-title">Disciplina:</span><span class="field-value">Limba engleză</span></div>
                    <div class="field-group"><span class="field-title">Absență:</span><span class="field-value">nemotivat</span></div>
                    <div class="field-group"><span class="field-title">Absențecumulative:</span><span class="field-value">61</span></div>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div role="tabpanel">
            <table aria-label="Discipline școlare">
              <tbody>
                <tr><td>Limba engleză Semestru Motivate Nemotivate Total Semestrul 1 13 2 15 Semestrul 2 0 0 0</td></tr>
                <tr><td>Matematică Semestru Motivate Nemotivate Total Semestrul 1 14 0 14 Semestrul 2 0 0 0</td></tr>
              </tbody>
            </table>
          </div>
        </div>
        <div id="messageContainer">
          <div class="card">
            <div class="card-header"><span class="date">6 octombrie 2026</span><span class="from">Prof. Rubanov-Cazacu Angela</span></div>
            <div class="card-discipline">Limba engleză</div>
            <div class="card-text">Dear student! Try to be more active during English Classes.</div>
          </div>
        </div>
        </body>
        </html>
    """.trimIndent()

    @Test
    fun parseMainPage_extractsAllSectionsAccurately() {
        val snap = SimeHtmlParser.parseMainPage(sampleMainPageHtml)

        assertEquals("25280474488139", snap.sessionId)
        assertEquals("PAPANAGA ILIE", snap.profile.fullName)
        assertEquals("11C", snap.profile.className)
        assertEquals("EC05927108", snap.profile.permanentCode)
        assertEquals(78, snap.presencePercent)
        assertFalse(snap.hasReplacements)

        assertEquals(1, snap.homeworkItems.size)
        assertEquals("Limba engleză", snap.homeworkItems[0].subject)
        assertEquals("18.11.2026", snap.homeworkItems[0].date)

        assertEquals(3, snap.recentEvaluations.size)
        assertEquals(4, snap.sem1Grades.size)

        val chimie = snap.sem1Grades.first { it.subject == "Chimie" }
        assertEquals(listOf(10, 9, 10, 10), chimie.numericGrades)
        assertEquals(9.75, chimie.liveAverage!!, 0.001)
        // Last Chimie 10 should already be matched to 09.11.2026 from #last3-eval
        assertEquals("2026-11-09", chimie.datedGrades.last().dateIso)

        val pe = snap.sem1Grades.first { it.subject == "Educație fizică" }
        assertEquals(listOf("Foarte bine"), pe.qualifiers)
        assertNull(pe.liveAverage)

        assertEquals(2, snap.dailyAbsences.size)
        assertEquals("2", snap.dailyAbsences[0].periodNumber)
        assertEquals("1", snap.dailyAbsences[1].periodNumber)
        assertTrue(snap.dailyAbsences[0].isMotivated)
        assertFalse(snap.dailyAbsences[1].isMotivated)
        assertEquals(76, snap.cumulativeAbsences.sem1Total)

        assertEquals(2, snap.subjectAbsences.size)
        assertEquals(15, snap.subjectAbsences[0].sem1Total)

        assertEquals(1, snap.messages.size)
        assertEquals("Prof. Rubanov-Cazacu Angela", snap.messages[0].professor)
        assertEquals(2, snap.teachers.size)
    }

    @Test
    fun parseCalendarEventsJson_extractsLessonCardsGradesAndAbsences() {
        val json = """
            [
              {
                "start": "2026-11-09T10:20:00",
                "end": "2026-11-09T11:05:00",
                "title": "<div class=\"lesson-card\"><div class=\"lesson-left\"><div class=\"lesson-order\">3</div><div class=\"lesson-time\"><span>10:20</span><span>11:05</span></div></div><div class=\"lesson-right\"><div class=\"lesson-line1\"><span class=\"lesson-text-wrap\">Chimie</span><span class=\"lesson-room\">105</span></div><div class=\"lesson-line2\"><span class=\"lesson-teacher\">Prunici Ludmila</span><span class=\"lesson-grade\">10</span></div></div></div>",
                "url": "javascript:apex.theme42.dialog('\\u002Fords\\u002Fr\\u002Fagenda\\u002Fmain\\u002Fcalendar-info?p19_orar_id=ABC&p19_lesson_date=09.11.2026',{title:'Detalii'})"
              },
              {
                "start": "2026-11-13T13:25:00",
                "end": "2026-11-13T14:10:00",
                "title": "<div class=\"lesson-card\"><div class=\"lesson-left\"><div class=\"lesson-order\">6</div><div class=\"lesson-time\"><span>13:25</span><span>14:10</span></div></div><div class=\"lesson-right\"><div class=\"lesson-line1\"><span class=\"lesson-text-wrap\">Limba engleză</span><span class=\"lesson-room\">014</span></div><div class=\"lesson-line2\"><span class=\"lesson-teacher\">Rubanov-Cazacu Angela</span><span class=\"lesson-grade\">a</span></div><div class=\"lesson-line3\"><span class=\"lesson-homework\">Read handout</span></div></div></div>",
                "url": ""
              }
            ]
        """.trimIndent()

        val events = SimeHtmlParser.parseCalendarEventsJson(json)
        assertEquals(2, events.size)

        val chem = events[0]
        assertEquals("2026-11-09", chem.dateIso)
        assertEquals("09.11.2026", chem.dateDisplay)
        assertEquals(3, chem.periodNumber)
        assertEquals("10:20", chem.startTime)
        assertEquals("Chimie", chem.subject)
        assertEquals("105", chem.room)
        assertEquals(10, chem.numericGrade)
        assertFalse(chem.isAbsent)
        assertNotNull(chem.detailUrl)
        assertTrue(chem.detailUrl!!.startsWith("https://agenda.sime.md/ords/r/agenda/main/calendar-info"))

        val eng = events[1]
        assertTrue(eng.isAbsent)
        assertNull(eng.numericGrade)
        assertEquals("Read handout", eng.homeworkSnippet)
        assertEquals(GradeCalculator.normalizeSubject("Educaţie"), GradeCalculator.normalizeSubject("Educație"))
    }
}
