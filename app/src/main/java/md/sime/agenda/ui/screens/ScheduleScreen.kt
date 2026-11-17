package md.sime.agenda.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import md.sime.agenda.model.GradeCalculator
import md.sime.agenda.model.LessonEvent
import md.sime.agenda.ui.LessonDetailSheetState
import md.sime.agenda.ui.components.HairlineDivider
import md.sime.agenda.ui.components.SimeCard
import md.sime.agenda.ui.components.StatusDot
import md.sime.agenda.ui.components.VerticalHairlineDivider
import md.sime.agenda.ui.components.liquidGlass
import md.sime.agenda.ui.theme.SimeTheme
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
                    Box(
                        modifier = Modifier
                            .liquidGlass(
                                shape = RoundedCornerShape(7.dp),
                                backgroundColor = colors.surfaceElevated
                            )
                            .clickable { onSelectDate(todayIso) }
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Azi",
                            color = colors.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
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
                    .uppercase()

                val cellBg = if (isSelected) {
                    if (colors.isDark) Color.White.copy(alpha = 0.13f) else colors.surface
                } else {
                    colors.surface
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .liquidGlass(
                            shape = RoundedCornerShape(10.dp),
                            backgroundColor = cellBg,
                            isHighlighted = isSelected
                        )
                        .clickable { onSelectDate(dayIso) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = shortDay,
                        color = if (isSelected) colors.textPrimary else colors.textMuted,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = day.dayOfMonth.toString(),
                        color = if (isSelected) colors.textPrimary else colors.textSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.height(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasMark) StatusDot(colors.textSecondary)
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

    SimeCard(
        isHighlighted = isHighlighted,
        backgroundColor = if (isHighlighted && colors.isDark) Color(0xFF13151B) else colors.surface,
        contentPadding = PaddingValues(14.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top
        ) {
            // Left time & period column
            Column(
                modifier = Modifier.width(52.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "ORA ${lesson.periodNumber}",
                    color = colors.textMuted,
                    fontSize = 10.sp,
                    letterSpacing = 1.0.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = lesson.startTime,
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = lesson.endTime,
                    color = colors.textMuted,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))
            VerticalHairlineDivider()
            Spacer(modifier = Modifier.width(12.dp))

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
                    if (!lesson.badgeText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(10.dp))
                        if (lesson.isAbsent) {
                            Text(
                                text = "ABSENȚĂ",
                                color = colors.absenceUnmotivated,
                                fontSize = 10.sp,
                                letterSpacing = 0.8.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "NOTA",
                                    color = colors.textMuted,
                                    fontSize = 9.sp,
                                    letterSpacing = 1.0.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = lesson.badgeText,
                                    color = colors.textPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                val meta = buildList {
                    if (lesson.teacher.isNotBlank()) add(lesson.teacher)
                    if (!lesson.room.isNullOrBlank()) add("Cab. ${lesson.room}")
                }.joinToString("  ·  ")

                if (meta.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = meta,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!lesson.homeworkSnippet.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HairlineDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "TEMĂ",
                        color = colors.textMuted,
                        fontSize = 9.sp,
                        letterSpacing = 1.1.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
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
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Ora ${state.lesson.periodNumber}  ·  ${state.lesson.startTime}–${state.lesson.endTime}  ·  ${state.lesson.dateDisplay}",
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
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 1.8.dp,
                        color = colors.textPrimary
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
                            val isAbs = detail.evaluation.equals("a", true)
                            Surface(
                                shape = RoundedCornerShape(7.dp),
                                color = colors.surfaceElevated,
                                border = BorderStroke(0.75.dp, colors.border)
                            ) {
                                Text(
                                    text = "Evaluare: ${detail.evaluation}",
                                    color = if (isAbs) colors.absenceUnmotivated else colors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
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
                    label = "SUBIECTUL LECȚIEI",
                    text = detail.topic.ifBlank { "Necompletat de profesor." }
                )

                DetailBlock(
                    label = "ACTIVITĂȚI DE ÎNVĂȚARE (TEMĂ)",
                    text = detail.homework.ifBlank { "Fără temă pentru acasă." }
                )

                if (detail.teacherMessage.isNotBlank()) {
                    DetailBlock(
                        label = "MESAJUL PROFESORULUI",
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
                        shape = RoundedCornerShape(9.dp),
                        border = BorderStroke(0.75.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Deschide lecția online", color = colors.textPrimary)
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
        shape = RoundedCornerShape(7.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(0.75.dp, colors.border)
    ) {
        Text(
            text = text,
            color = colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun DetailBlock(label: String, text: String) {
    val colors = SimeTheme.colors
    SimeCard(
        backgroundColor = colors.surfaceElevated,
        contentPadding = PaddingValues(13.dp)
    ) {
        Text(
            text = label,
            color = colors.textMuted,
            fontSize = 10.sp,
            letterSpacing = 1.1.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = text,
            color = colors.textPrimary,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}
