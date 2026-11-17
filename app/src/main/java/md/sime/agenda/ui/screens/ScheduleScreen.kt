package md.sime.agenda.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import md.sime.agenda.model.GradeCalculator
import md.sime.agenda.model.LessonEvent
import md.sime.agenda.ui.LessonDetailSheetState
import md.sime.agenda.ui.components.SimeCard
import md.sime.agenda.ui.components.StatusDot
import md.sime.agenda.ui.theme.SimeTheme
import md.sime.agenda.ui.theme.gradeColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    scheduleEvents: List<LessonEvent>,
    selectedDateIso: String,
    highlightedSubject: String?,
    lessonDetailState: LessonDetailSheetState?,
    onSelectDate: (String) -> Unit,
    onOpenLessonDetail: (LessonEvent) -> Unit,
    onDismissLessonDetail: () -> Unit
) {
    val colors = SimeTheme.colors
    val selectedDate = runCatching { LocalDate.parse(selectedDateIso) }.getOrElse { LocalDate.now() }
    val monday = selectedDate.with(DayOfWeek.MONDAY)
    val saturday = monday.plusDays(5)
    val hasSaturdayLessons = scheduleEvents.any { it.dateIso == saturday.toString() }
    val weekDays = (0 until if (hasSaturdayLessons) 6 else 5).map { monday.plusDays(it.toLong()) }

    val dayLessons = scheduleEvents.filter { it.dateIso == selectedDateIso }
    val weekRangeText = buildString {
        val end = monday.plusDays(4)
        append(GradeCalculator.formatIsoToShortDisplay(monday.toString()))
        append(" – ")
        append(GradeCalculator.formatIsoToShortDisplay(end.toString()))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Week switcher header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onSelectDate(selectedDate.minusWeeks(1).toString()) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = "Săptămâna anterioară",
                    tint = colors.textPrimary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = weekRangeText,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                val todayIso = LocalDate.now().toString()
                if (selectedDateIso != todayIso) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.surfaceElevated,
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSelectDate(todayIso) }
                    ) {
                        Text(
                            text = "Azi",
                            color = colors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            IconButton(
                onClick = { onSelectDate(selectedDate.plusWeeks(1).toString()) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "Săptămâna următoare",
                    tint = colors.textPrimary
                )
            }
        }

        // Horizontal Day Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            weekDays.forEach { day ->
                val dayIso = day.toString()
                val isSelected = dayIso == selectedDateIso
                val eventsForDay = scheduleEvents.filter { it.dateIso == dayIso }
                val hasMark = eventsForDay.any { it.numericGrade != null }
                val hasAbsence = eventsForDay.any { it.isAbsent }
                val shortDay = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ro", "MD"))
                    .replace(".", "")
                    .take(3)
                    .replaceFirstChar { it.uppercaseChar() }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) colors.textPrimary else colors.surface)
                        .border(
                            1.dp,
                            if (isSelected) colors.textPrimary else colors.border,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectDate(dayIso) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = shortDay,
                        color = if (isSelected) colors.background.copy(alpha = 0.75f) else colors.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = day.dayOfMonth.toString(),
                        color = if (isSelected) colors.background else colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.height(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasMark) StatusDot(colors.gradeHigh)
                        if (hasAbsence) StatusDot(colors.absenceUnmotivated)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Lesson Timeline
        if (dayLessons.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nu există lecții înregistrate pentru ${GradeCalculator.formatIsoToShortDisplay(selectedDateIso)}.",
                    color = colors.textSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            val listState = rememberLazyListState()
            LaunchedEffect(selectedDateIso, highlightedSubject, dayLessons.size) {
                if (!highlightedSubject.isNullOrBlank()) {
                    val targetIdx = dayLessons.indexOfFirst {
                        GradeCalculator.normalizeSubject(it.subject) ==
                            GradeCalculator.normalizeSubject(highlightedSubject)
                    }
                    if (targetIdx >= 0) {
                        listState.animateScrollToItem(targetIdx)
                    }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(dayLessons, key = { it.id }) { lesson ->
                    val isHighlighted = highlightedSubject != null &&
                        GradeCalculator.normalizeSubject(lesson.subject) ==
                        GradeCalculator.normalizeSubject(highlightedSubject)

                    LessonTimelineCard(
                        lesson = lesson,
                        isHighlighted = isHighlighted,
                        onClick = { onOpenLessonDetail(lesson) }
                    )
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
    }

    if (lessonDetailState != null) {
        ModalBottomSheet(
            onDismissRequest = onDismissLessonDetail,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surface,
            contentColor = colors.textPrimary
        ) {
            LessonDetailSheetContent(state = lessonDetailState)
        }
    }
}

@Composable
private fun LessonTimelineCard(
    lesson: LessonEvent,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    val colors = SimeTheme.colors
    val border = if (isHighlighted) colors.accent else colors.border
    val bg = if (isHighlighted) colors.accent.copy(alpha = 0.08f) else colors.surface

    SimeCard(
        borderColor = border,
        backgroundColor = bg,
        contentPadding = PaddingValues(14.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Left time & period column
            Column(
                modifier = Modifier.width(54.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Ora ${lesson.periodNumber}",
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = lesson.startTime,
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = lesson.endTime,
                    color = colors.textMuted,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right details column
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = lesson.subject,
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (!lesson.room.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.surfaceElevated,
                            border = BorderStroke(1.dp, colors.border)
                        ) {
                            Text(
                                text = lesson.room,
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = lesson.teacher,
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!lesson.badgeText.isNullOrBlank()) {
                        val col = if (lesson.isAbsent) colors.absenceUnmotivated
                        else gradeColor(lesson.numericGrade, colors)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = col.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, col.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = if (lesson.isAbsent) "Absență" else "Nota ${lesson.badgeText}",
                                color = col,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (!lesson.homeworkSnippet.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceElevated, RoundedCornerShape(8.dp))
                            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = lesson.homeworkSnippet,
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonDetailSheetContent(state: LessonDetailSheetState) {
    val colors = SimeTheme.colors
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = state.lesson.subject,
            color = colors.textPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Ora ${state.lesson.periodNumber} • ${state.lesson.startTime}–${state.lesson.endTime} • ${state.lesson.dateDisplay}",
            color = colors.textSecondary,
            fontSize = 13.sp
        )

        when {
            state.isLoading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = colors.accent
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Se încarcă detaliile lecției...", color = colors.textSecondary, fontSize = 13.sp)
                }
            }
            state.error != null -> {
                Text(
                    text = state.error,
                    color = colors.gradeLow,
                    fontSize = 13.sp
                )
            }
            state.detail != null -> {
                val detail = state.detail
                if (detail.isSummative || detail.isTeza || !detail.evaluation.isNullOrBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!detail.evaluation.isNullOrBlank()) {
                            val num = detail.evaluation.toIntOrNull()
                            val col = if (detail.evaluation.equals("a", true)) colors.absenceUnmotivated
                            else gradeColor(num, colors)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = col.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, col.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "Evaluare: ${detail.evaluation}",
                                    color = col,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        }
                        if (detail.isSummative) {
                            DetailTag("Sumativă")
                        }
                        if (detail.isTeza) {
                            DetailTag("Teză")
                        }
                    }
                }

                DetailBlock(
                    label = "Subiectul lecției",
                    text = detail.topic.ifBlank { "Necompletat de profesor." }
                )

                DetailBlock(
                    label = "Activități de învățare (Temă)",
                    text = detail.homework.ifBlank { "Fără temă pentru acasă." }
                )

                if (detail.teacherMessage.isNotBlank()) {
                    DetailBlock(
                        label = "Mesajul profesorului",
                        text = detail.teacherMessage
                    )
                }

                if (!detail.meetingLink.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(detail.meetingLink)))
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, colors.accent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.accent
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Deschide lecția online", color = colors.accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailTag(text: String) {
    val colors = SimeTheme.colors
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Text(
            text = text,
            color = colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun DetailBlock(label: String, text: String) {
    val colors = SimeTheme.colors
    SimeCard(
        backgroundColor = colors.surfaceElevated,
        contentPadding = PaddingValues(12.dp)
    ) {
        Text(
            text = label,
            color = colors.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = text,
            color = colors.textPrimary,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}
