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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import md.sime.agenda.model.GradeCalculator
import md.sime.agenda.model.LessonEvent
import md.sime.agenda.ui.LessonDetailSheetState
import md.sime.agenda.ui.components.EvaluationBadge
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
    val today = LocalDate.now()
    val todayIso = today.toString()
    val selectedDate = runCatching { LocalDate.parse(selectedDateIso) }.getOrElse { today }
    val monday = selectedDate.with(DayOfWeek.MONDAY)
    val saturday = monday.plusDays(5)
    val hasSaturdayLessons = scheduleEvents.any { it.dateIso == saturday.toString() }
    val weekDays = (0 until if (hasSaturdayLessons) 6 else 5).map { monday.plusDays(it.toLong()) }

    val weekRangeText = buildString {
        val end = if (hasSaturdayLessons) saturday else monday.plusDays(4)
        append(GradeCalculator.formatIsoToShortDisplay(monday.toString()))
        append(" – ")
        append(GradeCalculator.formatIsoToShortDisplay(end.toString()))
    }
    val weekStartIso = weekDays.first().toString()
    val weekEndIso = weekDays.last().toString()
    val weekHasAnyLessons = scheduleEvents.any { it.dateIso in weekStartIso..weekEndIso }

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

        // Horizontal Quick-Jump Day Strip
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
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.height(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasMark) StatusDot(colors.gradeLowMid)
                        if (hasAbsence) StatusDot(colors.absenceUnmotivated)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Continuous Full-Week Feed centered on active/current day
        if (!weekHasAnyLessons) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nu există lecții înregistrate pentru săptămâna $weekRangeText.",
                    color = colors.textSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            val initialIdx = weekDays.indexOfFirst { it.toString() == selectedDateIso }
                .let { if (it >= 0) it else weekDays.lastIndex }
                .coerceAtLeast(0)
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = initialIdx,
                initialFirstVisibleItemScrollOffset = if (initialIdx > 0) -140 else 0
            )

            LaunchedEffect(selectedDateIso, highlightedSubject, weekHasAnyLessons) {
                val targetIdx = weekDays.indexOfFirst { it.toString() == selectedDateIso }
                    .let { if (it >= 0) it else weekDays.lastIndex }
                if (targetIdx >= 0 && weekHasAnyLessons) {
                    listState.scrollToItem(targetIdx, scrollOffset = -90)
                    val viewportHeight = listState.layoutInfo.viewportSize.height
                    val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIdx }
                    if (viewportHeight > 0 && itemInfo != null) {
                        val centerOffset = if (itemInfo.size < viewportHeight) {
                            -((viewportHeight - itemInfo.size) / 2)
                        } else {
                            -12
                        }
                        listState.animateScrollToItem(targetIdx, centerOffset)
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(weekDays, key = { _, day -> day.toString() }) { _, day ->
                    val dayIso = day.toString()
                    val isSelectedDay = dayIso == selectedDateIso
                    val isToday = dayIso == todayIso
                    val lessonsForDay = scheduleEvents.filter { it.dateIso == dayIso }

                    WeekDayScheduleCard(
                        day = day,
                        dayIso = dayIso,
                        isSelectedDay = isSelectedDay,
                        isToday = isToday,
                        lessons = lessonsForDay,
                        highlightedSubject = if (isSelectedDay) highlightedSubject else null,
                        onSelectDay = { onSelectDate(dayIso) },
                        onLessonClick = { lesson ->
                            onSelectDate(dayIso)
                            onOpenLessonDetail(lesson)
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(14.dp)) }
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
private fun WeekDayScheduleCard(
    day: LocalDate,
    dayIso: String,
    isSelectedDay: Boolean,
    isToday: Boolean,
    lessons: List<LessonEvent>,
    highlightedSubject: String?,
    onSelectDay: () -> Unit,
    onLessonClick: (LessonEvent) -> Unit
) {
    val colors = SimeTheme.colors
    val dayName = day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("ro", "MD")).uppercase()
    val dateShort = GradeCalculator.formatIsoToShortDisplay(dayIso)
    val countLabel = if (lessons.size == 1) "1 oră" else "${lessons.size} ore"

    SimeCard(
        isHighlighted = isSelectedDay,
        backgroundColor = if (isSelectedDay && colors.isDark) Color(0xFF101218) else colors.surface,
        contentPadding = PaddingValues(0.dp)
    ) {
        // Day Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelectDay)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "$dayName  ·  $dateShort",
                    color = if (isSelectedDay) colors.textPrimary else colors.textSecondary,
                    fontSize = 12.sp,
                    letterSpacing = 1.1.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isToday) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(colors.surfaceElevated, RoundedCornerShape(5.dp))
                            .border(0.75.dp, colors.gradeLowMid.copy(alpha = 0.5f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "AZI",
                            color = colors.gradeLowMid,
                            fontSize = 9.sp,
                            letterSpacing = 0.9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = countLabel,
                color = colors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        HairlineDivider()

        if (lessons.isEmpty()) {
            Text(
                text = "Nicio lecție înregistrată",
                color = colors.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(14.dp)
            )
        } else {
            lessons.forEachIndexed { idx, lesson ->
                val isLessonHighlighted = !highlightedSubject.isNullOrBlank() &&
                    GradeCalculator.normalizeSubject(lesson.subject) ==
                    GradeCalculator.normalizeSubject(highlightedSubject)

                WeekDayLessonRow(
                    lesson = lesson,
                    isHighlighted = isLessonHighlighted,
                    onClick = { onLessonClick(lesson) }
                )
                if (idx < lessons.lastIndex) {
                    HairlineDivider()
                }
            }
        }
    }
}

@Composable
private fun WeekDayLessonRow(
    lesson: LessonEvent,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    val colors = SimeTheme.colors
    val rowBg = if (isHighlighted) {
        if (colors.isDark) Color.White.copy(alpha = 0.08f) else colors.surfaceElevated
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable(onClick = onClick)
            .height(IntrinsicSize.Min)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Left period & time column
        Column(
            modifier = Modifier.width(50.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "ORA ${lesson.periodNumber}",
                color = colors.textMuted,
                fontSize = 9.sp,
                letterSpacing = 0.9.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = lesson.startTime,
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = lesson.endTime,
                color = colors.textMuted,
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))
        VerticalHairlineDivider()
        Spacer(modifier = Modifier.width(12.dp))

        // Middle subject, teacher/room & homework column
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = lesson.subject,
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            val meta = buildList {
                if (lesson.teacher.isNotBlank()) add(lesson.teacher)
                if (!lesson.room.isNullOrBlank()) add("Cab. ${lesson.room}")
            }.joinToString("  ·  ")

            if (meta.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = meta,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!lesson.homeworkSnippet.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Temă: ${lesson.homeworkSnippet}",
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Right evaluation / absence figure
        if (!lesson.badgeText.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(10.dp))
            val isQual = lesson.numericGrade == null &&
                !lesson.isAbsent &&
                GradeCalculator.canonicalQualifier(lesson.badgeText) != null
            val isDesc = lesson.numericGrade == null && !lesson.isAbsent && !isQual
            EvaluationBadge(
                text = if (lesson.isAbsent) "ABS" else lesson.badgeText,
                isAbsent = lesson.isAbsent,
                numericGrade = lesson.numericGrade,
                isQualifier = isQual,
                isDescriptor = isDesc
            )
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
