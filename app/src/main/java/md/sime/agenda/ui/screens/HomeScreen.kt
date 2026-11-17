package md.sime.agenda.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import md.sime.agenda.model.GradeCalculator
import md.sime.agenda.model.LessonEvent
import md.sime.agenda.model.PortalSnapshot
import md.sime.agenda.ui.components.HairlineDivider
import md.sime.agenda.ui.components.SectionHeader
import md.sime.agenda.ui.components.SimeCard
import md.sime.agenda.ui.components.VerticalHairlineDivider
import md.sime.agenda.ui.theme.SimeTheme
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeScreen(
    snapshot: PortalSnapshot?,
    scheduleEvents: List<LessonEvent>,
    liveOverallAverage: Double?,
    completedHomeworkIds: Set<String>,
    onToggleHomework: (String) -> Unit,
    onJumpToScheduleDate: (dateIso: String, subject: String?) -> Unit,
    onOpenGrades: () -> Unit,
    onOpenAbsences: () -> Unit,
    onOpenLessonDetail: (LessonEvent) -> Unit
) {
    val colors = SimeTheme.colors

    if (snapshot == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Se sincronizează datele de pe SIME...",
                color = colors.textSecondary,
                fontSize = 14.sp
            )
        }
        return
    }

    val today = LocalDate.now()
    val todayIso = today.toString()
    val todayLessons = scheduleEvents.filter { it.dateIso == todayIso }

    val (previewDateIso, previewTitle, previewLessons) = if (todayLessons.isNotEmpty()) {
        Triple(todayIso, "Astăzi la școală", todayLessons)
    } else {
        val nextDay = scheduleEvents
            .firstOrNull { it.dateIso > todayIso }
            ?.dateIso
        if (nextDay != null) {
            val d = LocalDate.parse(nextDay)
            val dayName = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("ro", "MD"))
                .replaceFirstChar { it.uppercaseChar() }
            Triple(
                nextDay,
                "Următoarea zi ($dayName, ${GradeCalculator.formatIsoToShortDisplay(nextDay)})",
                scheduleEvents.filter { it.dateIso == nextDay }
            )
        } else {
            val latestDay = scheduleEvents.lastOrNull { it.dateIso <= todayIso }?.dateIso
            if (latestDay != null) {
                Triple(
                    latestDay,
                    "Ultima zi cu ore (${GradeCalculator.formatIsoToShortDisplay(latestDay)})",
                    scheduleEvents.filter { it.dateIso == latestDay }
                )
            } else {
                Triple(todayIso, "Orarul zilei", emptyList())
            }
        }
    }

    val totalAbsences = snapshot.cumulativeAbsences.sem1Total + snapshot.cumulativeAbsences.sem2Total

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Unified Executive Metric Strip
        item {
            SimeCard(contentPadding = PaddingValues(0.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    ExecutiveMetricColumn(
                        label = "MEDIA",
                        value = GradeCalculator.formatAverage(liveOverallAverage),
                        onClick = onOpenGrades,
                        modifier = Modifier.weight(1f)
                    )
                    VerticalHairlineDivider()
                    ExecutiveMetricColumn(
                        label = "PREZENȚĂ",
                        value = "${snapshot.presencePercent}%",
                        onClick = onOpenAbsences,
                        modifier = Modifier.weight(1f)
                    )
                    VerticalHairlineDivider()
                    ExecutiveMetricColumn(
                        label = "ABSENȚE",
                        value = totalAbsences.toString(),
                        onClick = onOpenAbsences,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 2. Today / Next School Day Lessons Ledger
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(
                    title = previewTitle,
                    trailing = "Orar complet",
                    onTrailingClick = { onJumpToScheduleDate(previewDateIso, null) }
                )
                if (previewLessons.isEmpty()) {
                    SimeCard {
                        Text(
                            text = "Nicio lecție programată. Deschide Orar pentru a naviga prin săptămâni.",
                            color = colors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    SimeCard(contentPadding = PaddingValues(0.dp)) {
                        previewLessons.forEachIndexed { idx, lesson ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenLessonDetail(lesson) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = lesson.periodNumber.toString().padStart(2, '0'),
                                    color = colors.textMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(26.dp)
                                )
                                Text(
                                    text = lesson.startTime,
                                    color = colors.textSecondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.width(44.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = lesson.subject,
                                        color = colors.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val meta = buildList {
                                        if (!lesson.room.isNullOrBlank()) add("Cab. ${lesson.room}")
                                        if (lesson.teacher.isNotBlank()) add(lesson.teacher)
                                    }.joinToString(" · ")
                                    if (meta.isNotBlank()) {
                                        Text(
                                            text = meta,
                                            color = colors.textMuted,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (!lesson.badgeText.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.width(10.dp))
                                    if (lesson.isAbsent) {
                                        Text(
                                            text = "ABS",
                                            color = colors.absenceUnmotivated,
                                            fontSize = 11.sp,
                                            letterSpacing = 0.8.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    } else {
                                        Text(
                                            text = lesson.badgeText,
                                            color = colors.textPrimary,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            if (idx < previewLessons.lastIndex) {
                                HairlineDivider()
                            }
                        }
                    }
                }
            }
        }

        // 3. Homework Checklist (Sarcini de învățare)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Sarcini de învățare")
                if (snapshot.homeworkItems.isEmpty()) {
                    SimeCard {
                        Text(
                            text = "Nu ai teme afișate pentru zilele următoare.",
                            color = colors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    SimeCard(contentPadding = PaddingValues(0.dp)) {
                        snapshot.homeworkItems.forEachIndexed { idx, hw ->
                            val isDone = hw.id in completedHomeworkIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleHomework(hw.id) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = if (isDone) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    tint = if (isDone) colors.textPrimary else colors.textMuted,
                                    modifier = Modifier
                                        .padding(top = 1.dp)
                                        .size(19.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = hw.subject,
                                            color = if (isDone) colors.textMuted else colors.textPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (hw.date.isNotBlank()) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = GradeCalculator.formatDisplayToShort(hw.date),
                                                color = colors.textMuted,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = hw.text,
                                        color = if (isDone) colors.textMuted else colors.textSecondary,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                }
                            }
                            if (idx < snapshot.homeworkItems.lastIndex) {
                                HairlineDivider()
                            }
                        }
                    }
                }
            }
        }

        // 4. Recent Marks Ledger
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(
                    title = "Note recente",
                    trailing = "Toate notele",
                    onTrailingClick = onOpenGrades
                )
                if (snapshot.recentEvaluations.isEmpty()) {
                    SimeCard {
                        Text(
                            text = "Nu există note recente.",
                            color = colors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    SimeCard(contentPadding = PaddingValues(0.dp)) {
                        snapshot.recentEvaluations.forEachIndexed { idx, eval ->
                            val dateIso = GradeCalculator.parseDisplayDateToIso(eval.date)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = dateIso != null) {
                                        if (dateIso != null) {
                                            onJumpToScheduleDate(dateIso, eval.subject)
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = GradeCalculator.formatDisplayToShort(eval.date),
                                    color = colors.textMuted,
                                    fontSize = 12.sp,
                                    modifier = Modifier.width(46.dp)
                                )
                                Text(
                                    text = eval.subject,
                                    color = colors.textPrimary,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = eval.grade,
                                    color = colors.textPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (dateIso != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "›",
                                        color = colors.textMuted,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                            if (idx < snapshot.recentEvaluations.lastIndex) {
                                HairlineDivider()
                            }
                        }
                    }
                }
            }
        }

        // 5. Lesson Replacements
        if (snapshot.hasReplacements && snapshot.replacementsText.isNotBlank()) {
            item {
                SimeCard(borderColor = colors.gradeLowMid.copy(alpha = 0.5f)) {
                    Text(
                        text = "LECȚII ÎNLOCUITE",
                        color = colors.gradeLowMid,
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = snapshot.replacementsText,
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun ExecutiveMetricColumn(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SimeTheme.colors
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            color = colors.textMuted,
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = value,
            color = colors.textPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Light
        )
    }
}
