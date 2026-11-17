package md.sime.agenda.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import md.sime.agenda.model.AbsenceFilterMode
import md.sime.agenda.model.AbsenceSortMode
import md.sime.agenda.model.DailyAbsence
import md.sime.agenda.model.DayAbsenceGroup
import md.sime.agenda.model.GradeCalculator
import md.sime.agenda.model.PortalSnapshot
import md.sime.agenda.model.SubjectAbsenceSummary
import md.sime.agenda.ui.components.HairlineDivider
import md.sime.agenda.ui.components.SegmentedGlassToggle
import md.sime.agenda.ui.components.SimeCard
import md.sime.agenda.ui.components.StatusDot
import md.sime.agenda.ui.components.liquidGlass
import md.sime.agenda.ui.theme.SimeTheme
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AbsencesScreen(
    snapshot: PortalSnapshot?,
    sortMode: AbsenceSortMode,
    filterMode: AbsenceFilterMode,
    onSelectSortMode: (AbsenceSortMode) -> Unit,
    onSelectFilterMode: (AbsenceFilterMode) -> Unit,
    onJumpToScheduleDate: (dateIso: String, subject: String?) -> Unit
) {
    val colors = SimeTheme.colors
    var viewBySubject by remember { mutableStateOf(false) }

    if (snapshot == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background),
            contentAlignment = Alignment.Center
        ) {
            Text("Se încarcă absențele...", color = colors.textSecondary, fontSize = 14.sp)
        }
        return
    }

    val cum = snapshot.cumulativeAbsences
    val total = cum.sem1Total + cum.sem2Total
    val motivated = cum.sem1Motivated + cum.sem2Motivated
    val unmotivated = cum.sem1Unmotivated + cum.sem2Unmotivated

    val dayGroups = remember(snapshot.dailyAbsences, sortMode, filterMode) {
        GradeCalculator.groupAndSortAbsencesByDay(snapshot.dailyAbsences, sortMode, filterMode)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Unified Interactive Summary + Filter Card
        item {
            SimeCard(contentPadding = PaddingValues(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "PREZENȚĂ SEMESTRIALĂ",
                            color = colors.textMuted,
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${snapshot.presencePercent}%",
                            color = colors.textPrimary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Light
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$total absențe",
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$unmotivated nemot. · $motivated mot.",
                            color = colors.textMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3-segment visual progress bar: Present / Motivated / Unmotivated
                val presentFrac = (snapshot.presencePercent / 100f).coerceIn(0f, 1f)
                val absentFrac = (1f - presentFrac).coerceAtLeast(0f)
                val motFrac = if (total > 0) absentFrac * (motivated.toFloat() / total.toFloat()) else 0f
                val unmotFrac = if (total > 0) absentFrac * (unmotivated.toFloat() / total.toFloat()) else 0f

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.surfaceElevated)
                ) {
                    if (presentFrac > 0.001f) {
                        Box(
                            modifier = Modifier
                                .weight(presentFrac)
                                .fillMaxHeight()
                                .background(colors.textPrimary.copy(alpha = 0.72f))
                        )
                    }
                    if (motFrac > 0.001f) {
                        Box(
                            modifier = Modifier
                                .weight(motFrac)
                                .fillMaxHeight()
                                .background(colors.textSecondary.copy(alpha = 0.42f))
                        )
                    }
                    if (unmotFrac > 0.001f) {
                        Box(
                            modifier = Modifier
                                .weight(unmotFrac)
                                .fillMaxHeight()
                                .background(colors.absenceUnmotivated)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Built-in clickable filter counter tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AbsenceFilterChip(
                        label = "Toate · $total",
                        selected = filterMode == AbsenceFilterMode.ALL,
                        accentColor = colors.textPrimary,
                        onClick = { onSelectFilterMode(AbsenceFilterMode.ALL) },
                        modifier = Modifier.weight(1f)
                    )
                    AbsenceFilterChip(
                        label = "Nemot. · $unmotivated",
                        selected = filterMode == AbsenceFilterMode.UNMOTIVATED,
                        accentColor = if (unmotivated > 0) colors.absenceUnmotivated else colors.textPrimary,
                        onClick = { onSelectFilterMode(AbsenceFilterMode.UNMOTIVATED) },
                        modifier = Modifier.weight(1f)
                    )
                    AbsenceFilterChip(
                        label = "Motivate · $motivated",
                        selected = filterMode == AbsenceFilterMode.MOTIVATED,
                        accentColor = colors.textPrimary,
                        onClick = { onSelectFilterMode(AbsenceFilterMode.MOTIVATED) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 2. Compact Control Row: Pe zile / Pe discipline + Single Cycle Sort Pill
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SegmentedGlassToggle(
                    options = listOf("Pe zile", "Pe discipline"),
                    selectedIndex = if (viewBySubject) 1 else 0,
                    onSelect = { viewBySubject = it == 1 },
                    modifier = Modifier.weight(1f)
                )

                if (!viewBySubject) {
                    val sortShortLabel = when (sortMode) {
                        AbsenceSortMode.NEWEST -> "↕ Noi"
                        AbsenceSortMode.OLDEST -> "↕ Vechi"
                        AbsenceSortMode.MOST_PER_DAY -> "↕ Multe/zi"
                    }
                    Box(
                        modifier = Modifier
                            .liquidGlass(
                                shape = RoundedCornerShape(12.dp),
                                backgroundColor = colors.surface
                            )
                            .clickable {
                                val nextIdx = (sortMode.ordinal + 1) % AbsenceSortMode.entries.size
                                onSelectSortMode(AbsenceSortMode.entries[nextIdx])
                            }
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = sortShortLabel,
                            color = colors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (!viewBySubject) {
            if (dayGroups.isEmpty()) {
                item {
                    SimeCard {
                        Text(
                            text = "Nu există absențe pentru filtrul selectat.",
                            color = colors.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                items(dayGroups, key = { it.dateDisplay }) { group ->
                    DayAbsenceCard(
                        group = group,
                        onJumpToDate = onJumpToScheduleDate
                    )
                }
            }
        } else {
            val filteredSubjects = snapshot.subjectAbsences
                .filter { item ->
                    val mot = item.sem1Motivated + item.sem2Motivated
                    val unmot = item.sem1Unmotivated + item.sem2Unmotivated
                    val tot = item.sem1Total + item.sem2Total
                    when (filterMode) {
                        AbsenceFilterMode.ALL -> tot > 0
                        AbsenceFilterMode.UNMOTIVATED -> unmot > 0
                        AbsenceFilterMode.MOTIVATED -> mot > 0
                    }
                }
                .sortedByDescending { item ->
                    when (filterMode) {
                        AbsenceFilterMode.ALL -> item.sem1Total + item.sem2Total
                        AbsenceFilterMode.UNMOTIVATED -> item.sem1Unmotivated + item.sem2Unmotivated
                        AbsenceFilterMode.MOTIVATED -> item.sem1Motivated + item.sem2Motivated
                    }
                }

            if (filteredSubjects.isEmpty()) {
                item {
                    SimeCard {
                        Text(
                            text = "Nu există absențe pe discipline pentru filtrul selectat.",
                            color = colors.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                items(filteredSubjects, key = { it.subject }) { item ->
                    SubjectAbsenceCard(
                        item = item,
                        dailyAbsences = snapshot.dailyAbsences,
                        filterMode = filterMode,
                        onJumpToDate = onJumpToScheduleDate
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun AbsenceFilterChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SimeTheme.colors
    val shape = RoundedCornerShape(8.dp)
    val bg = when {
        selected && colors.isDark -> Color.White.copy(alpha = 0.11f)
        selected && !colors.isDark -> colors.surfaceElevated
        else -> colors.surfaceElevated.copy(alpha = 0.45f)
    }
    val border = when {
        selected && colors.isDark -> Color.White.copy(alpha = 0.25f)
        selected && !colors.isDark -> colors.textPrimary.copy(alpha = 0.35f)
        else -> colors.border
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(bg, shape)
            .border(0.75.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) accentColor else colors.textMuted,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayAbsenceCard(
    group: DayAbsenceGroup,
    onJumpToDate: (String, String?) -> Unit
) {
    val colors = SimeTheme.colors
    val dateIso = GradeCalculator.parseDisplayDateToIso(group.dateDisplay)
    val dayOfWeekText = if (dateIso != null) {
        runCatching {
            LocalDate.parse(dateIso)
                .dayOfWeek
                .getDisplayName(TextStyle.FULL, Locale("ro", "MD"))
                .replaceFirstChar { it.uppercaseChar() }
        }.getOrNull()
    } else null

    val headerLabel = if (!dayOfWeekText.isNullOrBlank()) {
        "$dayOfWeekText, ${group.dateDisplay}"
    } else {
        group.dateDisplay
    }

    val summaryParts = buildList {
        if (group.unmotivatedCount > 0) add("${group.unmotivatedCount} nemot.")
        if (group.motivatedCount > 0) add("${group.motivatedCount} mot.")
    }.joinToString(" · ")

    SimeCard(contentPadding = PaddingValues(14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (dateIso != null) it.clickable { onJumpToDate(dateIso, null) } else it },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = headerLabel,
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (summaryParts.isNotEmpty()) {
                Text(
                    text = summaryParts,
                    color = if (group.unmotivatedCount > 0) colors.absenceUnmotivated else colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            group.items.forEach { abs ->
                val chipBorder = if (abs.isMotivated) {
                    colors.border
                } else {
                    colors.absenceUnmotivated.copy(alpha = 0.45f)
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceElevated, RoundedCornerShape(8.dp))
                        .border(0.75.dp, chipBorder, RoundedCornerShape(8.dp))
                        .let {
                            if (dateIso != null) it.clickable { onJumpToDate(dateIso, abs.subject) } else it
                        }
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusDot(if (abs.isMotivated) colors.textMuted else colors.absenceUnmotivated)
                    Text(
                        text = "Ora ${abs.periodNumber} · ${abs.subject}",
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubjectAbsenceCard(
    item: SubjectAbsenceSummary,
    dailyAbsences: List<DailyAbsence>,
    filterMode: AbsenceFilterMode,
    onJumpToDate: (String, String?) -> Unit
) {
    val colors = SimeTheme.colors
    var expanded by remember { mutableStateOf(false) }

    val mot = item.sem1Motivated + item.sem2Motivated
    val unmot = item.sem1Unmotivated + item.sem2Unmotivated
    val tot = item.sem1Total + item.sem2Total

    val subjectAbsences = remember(dailyAbsences, item.subject, filterMode, expanded) {
        if (!expanded) emptyList()
        else {
            val subjKey = GradeCalculator.normalizeSubject(item.subject)
            dailyAbsences
                .filter { abs ->
                    GradeCalculator.normalizeSubject(abs.subject) == subjKey &&
                        when (filterMode) {
                            AbsenceFilterMode.ALL -> true
                            AbsenceFilterMode.UNMOTIVATED -> !abs.isMotivated
                            AbsenceFilterMode.MOTIVATED -> abs.isMotivated
                        }
                }
                .sortedByDescending { it.dateEpochDay }
        }
    }

    SimeCard(
        contentPadding = PaddingValues(14.dp),
        onClick = { expanded = !expanded }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.subject,
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (expanded) "$tot total  ⌃" else "$tot total  ⌄",
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "$unmot nemotivate",
                color = if (unmot > 0) colors.absenceUnmotivated else colors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "·",
                color = colors.textMuted,
                fontSize = 11.sp
            )
            Text(
                text = "$mot motivate",
                color = colors.textSecondary,
                fontSize = 11.sp
            )
        }

        if (tot > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            val motFrac = mot.toFloat() / tot.toFloat()
            val unmotFrac = unmot.toFloat() / tot.toFloat()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(colors.surfaceElevated)
            ) {
                if (unmotFrac > 0.001f) {
                    Box(
                        modifier = Modifier
                            .weight(unmotFrac)
                            .fillMaxHeight()
                            .background(colors.absenceUnmotivated)
                    )
                }
                if (motFrac > 0.001f) {
                    Box(
                        modifier = Modifier
                            .weight(motFrac)
                            .fillMaxHeight()
                            .background(colors.textSecondary.copy(alpha = 0.5f))
                    )
                }
            }
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(10.dp))
            HairlineDivider()
            Spacer(modifier = Modifier.height(10.dp))
            if (subjectAbsences.isEmpty()) {
                Text(
                    text = "Nu există detalii zilnice sincronizate pentru această disciplină.",
                    color = colors.textMuted,
                    fontSize = 12.sp
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    subjectAbsences.forEach { abs ->
                        val dateIso = GradeCalculator.parseDisplayDateToIso(abs.date)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(colors.surfaceElevated, RoundedCornerShape(7.dp))
                                .border(
                                    0.75.dp,
                                    if (abs.isMotivated) colors.border else colors.absenceUnmotivated.copy(alpha = 0.45f),
                                    RoundedCornerShape(7.dp)
                                )
                                .let {
                                    if (dateIso != null) it.clickable { onJumpToDate(dateIso, item.subject) } else it
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            StatusDot(if (abs.isMotivated) colors.textMuted else colors.absenceUnmotivated)
                            Text(
                                text = "${GradeCalculator.formatDisplayToShort(abs.date)} · Ora ${abs.periodNumber}",
                                color = colors.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
