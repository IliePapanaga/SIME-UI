package md.sime.agenda.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import md.sime.agenda.model.AbsenceFilterMode
import md.sime.agenda.model.AbsenceSortMode
import md.sime.agenda.model.DayAbsenceGroup
import md.sime.agenda.model.GradeCalculator
import md.sime.agenda.model.PortalSnapshot
import md.sime.agenda.model.SubjectAbsenceSummary
import md.sime.agenda.ui.components.HairlineDivider
import md.sime.agenda.ui.components.PillToggle
import md.sime.agenda.ui.components.SegmentedGlassToggle
import md.sime.agenda.ui.components.SimeCard
import md.sime.agenda.ui.components.VerticalHairlineDivider
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
        // Unified 4-Column Executive Liquid Glass Strip
        item {
            SimeCard(contentPadding = PaddingValues(0.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    AbsenceMetricCell(
                        label = "TOTAL",
                        value = total.toString(),
                        valueColor = colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    VerticalHairlineDivider()
                    AbsenceMetricCell(
                        label = "MOTIVATE",
                        value = motivated.toString(),
                        valueColor = colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    VerticalHairlineDivider()
                    AbsenceMetricCell(
                        label = "NEMOTIVATE",
                        value = unmotivated.toString(),
                        valueColor = if (unmotivated > 0) colors.absenceUnmotivated else colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    VerticalHairlineDivider()
                    AbsenceMetricCell(
                        label = "PREZENȚĂ",
                        value = "${snapshot.presencePercent}%",
                        valueColor = colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // View switcher: By Days vs By Subjects
        item {
            SegmentedGlassToggle(
                options = listOf("Pe zile", "Pe discipline"),
                selectedIndex = if (viewBySubject) 1 else 0,
                onSelect = { viewBySubject = it == 1 }
            )
        }

        if (!viewBySubject) {
            // Sort & Filter pills
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AbsenceSortMode.entries.forEach { mode ->
                            PillToggle(
                                label = mode.label,
                                selected = sortMode == mode,
                                onClick = { onSelectSortMode(mode) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AbsenceFilterMode.entries.forEach { mode ->
                            PillToggle(
                                label = mode.label,
                                selected = filterMode == mode,
                                onClick = { onSelectFilterMode(mode) }
                            )
                        }
                    }
                }
            }

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
            val sortedSubjects = snapshot.subjectAbsences.sortedByDescending { it.sem1Total + it.sem2Total }
            if (sortedSubjects.isEmpty()) {
                item {
                    SimeCard {
                        Text(
                            text = "Nu există absențe pe discipline.",
                            color = colors.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                items(sortedSubjects, key = { it.subject }) { item ->
                    SubjectAbsenceCard(item)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun AbsenceMetricCell(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val colors = SimeTheme.colors
    Column(
        modifier = modifier.padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            color = colors.textMuted,
            fontSize = 9.sp,
            letterSpacing = 1.0.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Light
        )
    }
}

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

    SimeCard(contentPadding = PaddingValues(0.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = dateIso != null) {
                    if (dateIso != null) onJumpToDate(dateIso, null)
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
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
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = summaryParts,
                    color = if (group.unmotivatedCount > 0) colors.absenceUnmotivated else colors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        HairlineDivider()

        group.items.forEachIndexed { idx, abs ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = dateIso != null) {
                        if (dateIso != null) onJumpToDate(dateIso, abs.subject)
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ora ${abs.periodNumber}",
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.width(48.dp)
                )
                Text(
                    text = abs.subject,
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (abs.isMotivated) "Motivat" else "Nemotivat",
                    color = if (abs.isMotivated) colors.textSecondary else colors.absenceUnmotivated,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            if (idx < group.items.lastIndex) {
                HairlineDivider()
            }
        }
    }
}

@Composable
private fun SubjectAbsenceCard(item: SubjectAbsenceSummary) {
    val colors = SimeTheme.colors
    val mot = item.sem1Motivated + item.sem2Motivated
    val unmot = item.sem1Unmotivated + item.sem2Unmotivated
    val tot = item.sem1Total + item.sem2Total

    SimeCard(contentPadding = PaddingValues(14.dp)) {
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
                text = "$tot total",
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Motivate: $mot",
                color = colors.textSecondary,
                fontSize = 12.sp
            )
            Text(
                text = "Nemotivate: $unmot",
                color = if (unmot > 0) colors.absenceUnmotivated else colors.textMuted,
                fontSize = 12.sp
            )
        }
    }
}
