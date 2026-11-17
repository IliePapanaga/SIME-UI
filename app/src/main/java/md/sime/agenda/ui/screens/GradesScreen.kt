package md.sime.agenda.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import md.sime.agenda.model.DatedGrade
import md.sime.agenda.model.GradeCalculator
import md.sime.agenda.model.PortalSnapshot
import md.sime.agenda.model.SubjectGrades
import md.sime.agenda.ui.components.PillToggle
import md.sime.agenda.ui.components.SimeCard
import md.sime.agenda.ui.theme.SimeTheme
import md.sime.agenda.ui.theme.gradeColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GradesScreen(
    snapshot: PortalSnapshot?,
    selectedSemester: Int,
    simulatedMarks: Map<String, List<Int>>,
    liveOverallAverage: Double?,
    onSelectSemester: (Int) -> Unit,
    onAddSimulatedMark: (subject: String, mark: Int) -> Unit,
    onRemoveLastSimulatedMark: (subject: String) -> Unit,
    onResetSimulatedMarks: (subject: String?) -> Unit,
    onJumpToScheduleDate: (dateIso: String, subject: String) -> Unit
) {
    val colors = SimeTheme.colors
    var isSimulatorMode by remember { mutableStateOf(false) }

    if (snapshot == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background),
            contentAlignment = Alignment.Center
        ) {
            Text("Se încarcă notele...", color = colors.textSecondary, fontSize = 14.sp)
        }
        return
    }

    val rawSubjects = if (selectedSemester == 1) snapshot.sem1Grades else snapshot.sem2Grades
    val subjects = rawSubjects.map { sub ->
        GradeCalculator.applySimulatedMarks(sub, simulatedMarks[sub.subject].orEmpty())
    }
    val hasAnySimulated = simulatedMarks.values.any { it.isNotEmpty() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Semester Switcher + Live Overall Average Summary
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PillToggle(
                        label = "Semestrul I",
                        selected = selectedSemester == 1,
                        onClick = { onSelectSemester(1) },
                        modifier = Modifier.weight(1f)
                    )
                    PillToggle(
                        label = "Semestrul II",
                        selected = selectedSemester == 2,
                        onClick = { onSelectSemester(2) },
                        modifier = Modifier.weight(1f)
                    )
                }

                SimeCard(
                    borderColor = if (hasAnySimulated) colors.accent else colors.border,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (hasAnySimulated) "Media generală (simulare activă)" else "Media generală (live)",
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val gradedCount = subjects.count { it.liveAverage != null }
                            val totalGrades = subjects.sumOf { it.numericGrades.size }
                            Text(
                                text = "$totalGrades note • $gradedCount discipline cu medie",
                                color = colors.textMuted,
                                fontSize = 12.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = GradeCalculator.formatAverage(liveOverallAverage),
                                color = gradeColor(liveOverallAverage?.toInt(), colors),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSimulatorMode) "Simulator: apasă +10..+5 pe o disciplină"
                            else "Apasă pe o notă pentru a deschide lecția",
                            color = colors.textMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (hasAnySimulated) {
                                Text(
                                    text = "Resetează",
                                    color = colors.gradeLow,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onResetSimulatedMarks(null) }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSimulatorMode) colors.accent.copy(alpha = 0.16f) else colors.surfaceElevated,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSimulatorMode) colors.accent else colors.border
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { isSimulatorMode = !isSimulatorMode }
                            ) {
                                Text(
                                    text = if (isSimulatorMode) "Închide simulator" else "Simulare medie",
                                    color = if (isSimulatorMode) colors.accent else colors.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (subjects.isEmpty()) {
            item {
                SimeCard {
                    Text(
                        text = "Nu sunt evaluări pentru Semestrul $selectedSemester.",
                        color = colors.textSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            items(subjects, key = { "${it.semester}_${it.subject}" }) { subject ->
                SubjectGradesCard(
                    subject = subject,
                    isSimExpanded = isSimulatorMode,
                    hasSimulated = simulatedMarks[subject.subject].orEmpty().isNotEmpty(),
                    onAddSimulatedMark = { mark -> onAddSimulatedMark(subject.subject, mark) },
                    onResetSubject = { onResetSimulatedMarks(subject.subject) },
                    onGradeClick = { dg ->
                        if (dg.isSimulated) {
                            onRemoveLastSimulatedMark(subject.subject)
                        } else if (!dg.dateIso.isNullOrBlank()) {
                            onJumpToScheduleDate(dg.dateIso, subject.subject)
                        }
                    }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubjectGradesCard(
    subject: SubjectGrades,
    isSimExpanded: Boolean,
    hasSimulated: Boolean,
    onAddSimulatedMark: (Int) -> Unit,
    onResetSubject: () -> Unit,
    onGradeClick: (DatedGrade) -> Unit
) {
    val colors = SimeTheme.colors

    SimeCard(
        borderColor = if (hasSimulated) colors.accent.copy(alpha = 0.7f) else colors.border,
        contentPadding = PaddingValues(14.dp)
    ) {
        // Header row: Subject + live average
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subject.subject,
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (!subject.tezaRaw.isNullOrBlank() || !subject.officialAverage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    val meta = buildList {
                        if (!subject.tezaRaw.isNullOrBlank()) add("Teză: ${subject.tezaRaw}")
                        if (!subject.officialAverage.isNullOrBlank()) add("Medie oficială: ${subject.officialAverage}")
                    }.joinToString(" • ")
                    Text(
                        text = meta,
                        color = colors.textMuted,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = GradeCalculator.formatAverage(subject.liveAverage),
                color = gradeColor(subject.liveAverage?.toInt(), colors),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Grade Chips with dates
        if (subject.datedGrades.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                subject.datedGrades.forEach { dg ->
                    val col = if (dg.isSimulated) colors.accent else gradeColor(dg.numericValue, colors)
                    val isClickable = dg.isSimulated || !dg.dateIso.isNullOrBlank()

                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (dg.isSimulated) colors.accent.copy(alpha = 0.14f)
                                else col.copy(alpha = 0.12f)
                            )
                            .border(
                                1.dp,
                                if (dg.isSimulated) colors.accent else col.copy(alpha = 0.4f),
                                RoundedCornerShape(8.dp)
                            )
                            .let {
                                if (isClickable) it.clickable { onGradeClick(dg) } else it
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = dg.rawText,
                            color = col,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val dateLabel = when {
                            dg.isSimulated -> "Sim."
                            !dg.dateDisplay.isNullOrBlank() -> GradeCalculator.formatDisplayToShort(dg.dateDisplay)
                            else -> null
                        }
                        if (!dateLabel.isNullOrBlank()) {
                            Text(
                                text = dateLabel,
                                color = if (dg.isSimulated) colors.accent else colors.textSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Nicio notă înregistrată",
                color = colors.textMuted,
                fontSize = 12.sp
            )
        }

        // Simple Live Grade Simulator Row
        if (isSimExpanded) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceElevated, RoundedCornerShape(8.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10, 9, 8, 7, 6, 5).forEach { mark ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.background,
                            border = BorderStroke(1.dp, colors.border),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onAddSimulatedMark(mark) }
                        ) {
                            Text(
                                text = "+$mark",
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                if (hasSimulated) {
                    Text(
                        text = "Șterge",
                        color = colors.gradeLow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onResetSubject)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
