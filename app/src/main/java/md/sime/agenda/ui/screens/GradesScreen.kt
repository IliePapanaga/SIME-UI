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
import md.sime.agenda.ui.components.HairlineDivider
import md.sime.agenda.ui.components.SegmentedGlassToggle
import md.sime.agenda.ui.components.SimeCard
import md.sime.agenda.ui.components.liquidGlass
import md.sime.agenda.ui.theme.SimeTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GradesScreen(
    snapshot: PortalSnapshot?,
    selectedSemester: Int,
    simulatedMarks: Map<String, List<Int>>,
    simulatedQualifiers: Map<String, List<String>> = emptyMap(),
    liveOverallAverage: Double?,
    onSelectSemester: (Int) -> Unit,
    onAddSimulatedMark: (subject: String, mark: Int) -> Unit,
    onRemoveLastSimulatedMark: (subject: String) -> Unit,
    onAddSimulatedQualifier: (subject: String, qualifier: String) -> Unit = { _, _ -> },
    onRemoveLastSimulatedQualifier: (subject: String) -> Unit = {},
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
        GradeCalculator.applySimulatedMarks(
            subject = sub,
            simulatedMarks = simulatedMarks[sub.subject].orEmpty(),
            simulatedQualifiers = simulatedQualifiers[sub.subject].orEmpty()
        )
    }
    val hasAnySimulated = simulatedMarks.values.any { it.isNotEmpty() } ||
        simulatedQualifiers.values.any { it.isNotEmpty() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Semester Switcher + Portfolio Overall Average Summary
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SegmentedGlassToggle(
                    options = listOf("Semestrul I", "Semestrul II"),
                    selectedIndex = if (selectedSemester == 1) 0 else 1,
                    onSelect = { onSelectSemester(it + 1) }
                )

                SimeCard(
                    isHighlighted = hasAnySimulated,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (hasAnySimulated) "MEDIA GENERALĂ · SIMULARE" else "MEDIA GENERALĂ · LIVE",
                                color = colors.textMuted,
                                fontSize = 10.sp,
                                letterSpacing = 1.3.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val gradedCount = subjects.count { it.liveAverage != null }
                            val totalGrades = subjects.sumOf { it.numericGrades.size }
                            Text(
                                text = "$totalGrades evaluări  ·  $gradedCount discipline cu medie",
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = GradeCalculator.formatAverage(liveOverallAverage),
                            color = if (hasAnySimulated) colors.gradeLowMid else colors.textPrimary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Light
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HairlineDivider()
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSimulatorMode) "Simulator: adaugă note sau calificative"
                            else "Apasă pe o notă pentru a deschide lecția",
                            color = colors.textMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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
                            Box(
                                modifier = Modifier
                                    .liquidGlass(
                                        shape = RoundedCornerShape(7.dp),
                                        backgroundColor = colors.surfaceElevated,
                                        isHighlighted = isSimulatorMode
                                    )
                                    .clickable { isSimulatorMode = !isSimulatorMode }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = if (isSimulatorMode) "Închide simulator" else "Simulare medie",
                                    color = if (isSimulatorMode) colors.gradeLowMid else colors.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
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
                val hasSimSubject = simulatedMarks[subject.subject].orEmpty().isNotEmpty() ||
                    simulatedQualifiers[subject.subject].orEmpty().isNotEmpty()
                SubjectGradesCard(
                    subject = subject,
                    isSimExpanded = isSimulatorMode,
                    hasSimulated = hasSimSubject,
                    onAddSimulatedMark = { mark -> onAddSimulatedMark(subject.subject, mark) },
                    onAddSimulatedQualifier = { qual -> onAddSimulatedQualifier(subject.subject, qual) },
                    onResetSubject = { onResetSimulatedMarks(subject.subject) },
                    onGradeClick = { dg ->
                        if (dg.isSimulated) {
                            if (dg.numericValue == null) {
                                onRemoveLastSimulatedQualifier(subject.subject)
                            } else {
                                onRemoveLastSimulatedMark(subject.subject)
                            }
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
    onAddSimulatedQualifier: (String) -> Unit,
    onResetSubject: () -> Unit,
    onGradeClick: (DatedGrade) -> Unit
) {
    val colors = SimeTheme.colors
    val isQualifier = GradeCalculator.isQualifierSubject(subject)
    val qualifierSummary = if (isQualifier) {
        GradeCalculator.calculateQualifierSummary(subject.qualifiers, subject.officialAverage)
    } else {
        null
    }

    SimeCard(
        isHighlighted = hasSimulated,
        borderColor = if (hasSimulated) colors.gradeLowMid.copy(alpha = 0.65f) else colors.border,
        contentPadding = PaddingValues(14.dp)
    ) {
        // Header row: Subject + live average / qualifier summary
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
                    }.joinToString("  ·  ")
                    Text(
                        text = meta,
                        color = colors.textMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            if (isQualifier) {
                Text(
                    text = qualifierSummary ?: "—",
                    color = if (hasSimulated) colors.gradeLowMid else colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = GradeCalculator.formatAverage(subject.liveAverage),
                    color = if (hasSimulated) colors.gradeLowMid else colors.textPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Precision Monochrome Glass Keycaps
        if (subject.datedGrades.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            HairlineDivider()
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                subject.datedGrades.forEach { dg ->
                    val isClickable = dg.isSimulated || !dg.dateIso.isNullOrBlank()
                    val keycapBorder = if (dg.isSimulated) colors.gradeLowMid else colors.border
                    val valueColor = if (dg.isSimulated) colors.gradeLowMid else colors.textPrimary
                    val dateLabel = when {
                        dg.isSimulated -> "Sim."
                        !dg.dateDisplay.isNullOrBlank() -> GradeCalculator.formatDisplayToShort(dg.dateDisplay)
                        else -> null
                    }

                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceElevated, RoundedCornerShape(8.dp))
                            .border(0.75.dp, keycapBorder, RoundedCornerShape(8.dp))
                            .let {
                                if (isClickable) it.clickable { onGradeClick(dg) } else it
                            }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = dg.rawText,
                            color = valueColor,
                            fontSize = if (dg.rawText.length > 3) 13.sp else 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!dateLabel.isNullOrBlank()) {
                            Text(
                                text = dateLabel,
                                color = if (dg.isSimulated) colors.gradeLowMid else colors.textMuted,
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
                text = if (isQualifier) "Niciun calificativ înregistrat" else "Nicio notă înregistrată",
                color = colors.textMuted,
                fontSize = 12.sp
            )
        }

        // Live Simulator Row (Qualifiers for descriptor subjects, Numeric marks otherwise)
        if (isSimExpanded) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceElevated, RoundedCornerShape(8.dp))
                    .border(0.75.dp, colors.border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isQualifier) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Foarte bine", "Bine", "Suficient").forEach { qualifier ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.background,
                                border = BorderStroke(0.75.dp, colors.border),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onAddSimulatedQualifier(qualifier) }
                            ) {
                                Text(
                                    text = "+$qualifier",
                                    color = colors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(10, 9, 8, 7, 6, 5).forEach { mark ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.background,
                                border = BorderStroke(0.75.dp, colors.border),
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
                }
                if (hasSimulated) {
                    Spacer(modifier = Modifier.width(6.dp))
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
