package md.sime.agenda.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import md.sime.agenda.model.AppUpdateInfo
import md.sime.agenda.model.PortalSnapshot
import md.sime.agenda.model.ThemeMode
import md.sime.agenda.notifications.AppUpdateChecker
import md.sime.agenda.ui.components.HairlineDivider
import md.sime.agenda.ui.components.SegmentedGlassToggle
import md.sime.agenda.ui.components.SimeCard
import md.sime.agenda.ui.theme.SimeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DossierSheet(
    snapshot: PortalSnapshot?,
    currentThemeMode: ThemeMode,
    availableUpdate: AppUpdateInfo? = null,
    onSelectTheme: (ThemeMode) -> Unit,
    onSwitchStudent: (String) -> Unit,
    onTestNotification: () -> Unit,
    onLogoutKeepQuickLogin: () -> Unit,
    onLogoutForgetAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SimeTheme.colors
    val context = LocalContext.current
    var teacherQuery by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        contentColor = colors.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Student Header
            if (snapshot != null) {
                val p = snapshot.profile
                Column {
                    Text(
                        text = p.fullName.ifBlank { "Elev" },
                        color = colors.textPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (p.schoolName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = p.schoolName,
                            color = colors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                // Dossier Details
                SimeCard(
                    backgroundColor = colors.background,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    DossierRow("Clasa", p.className)
                    DossierRow("Diriginte", p.homeroomTeacher)
                    DossierRow("Cod permanent", p.permanentCode)
                    DossierRow("Data nașterii", p.birthDate)
                    if (p.schoolAddress.isNotBlank()) DossierRow("Adresa școlii", p.schoolAddress)
                    if (p.schoolPhone.isNotBlank()) DossierRow("Telefon", p.schoolPhone)
                }
            }

            // Theme Mode Switcher
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "TEMĂ VIZUALĂ",
                    color = colors.textMuted,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.SemiBold
                )
                SegmentedGlassToggle(
                    options = ThemeMode.entries.map { it.label },
                    selectedIndex = ThemeMode.entries.indexOf(currentThemeMode).coerceAtLeast(0),
                    onSelect = { onSelectTheme(ThemeMode.entries[it]) }
                )
            }

            // GitHub release update
            val updateUrl = availableUpdate?.downloadUrl ?: AppUpdateChecker.APK_LATEST_URL
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl)))
                    }
                },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                    0.75.dp,
                    if (availableUpdate != null) colors.gradeLowMid else colors.border
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (availableUpdate != null) colors.gradeLowMid else colors.textPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                val label = if (availableUpdate != null) {
                    val ver = if (availableUpdate.versionName == "nouă") "" else " v${availableUpdate.versionName}"
                    "Instalează actualizarea$ver"
                } else {
                    "Verifică / Descarcă actualizare (GitHub)"
                }
                Text(label)
            }

            // Notifications test
            OutlinedButton(
                onClick = onTestNotification,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(0.75.dp, colors.border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Testează notificarea pentru Mesaje")
            }

            // Teachers Directory
            if (snapshot != null && snapshot.teachers.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "DISCIPLINE ȘI PROFESORI",
                        color = colors.textMuted,
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = teacherQuery,
                        onValueChange = { teacherQuery = it },
                        placeholder = { Text("Caută disciplină sau profesor", fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.textPrimary,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedContainerColor = colors.background,
                            unfocusedContainerColor = colors.background
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val filtered = snapshot.teachers.filter {
                        teacherQuery.isBlank() ||
                            it.subject.contains(teacherQuery, ignoreCase = true) ||
                            it.teacherName.contains(teacherQuery, ignoreCase = true)
                    }

                    SimeCard(
                        backgroundColor = colors.background,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        filtered.forEachIndexed { idx, row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = row.subject,
                                    color = colors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = row.teacherName,
                                    color = colors.textSecondary,
                                    fontSize = 13.sp
                                )
                            }
                            if (idx < filtered.lastIndex) HairlineDivider()
                        }
                    }
                }
            }

            // Account Logout Actions
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onLogoutKeepQuickLogin,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, colors.border),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Deconectare (păstrează login rapid)")
                }

                Text(
                    text = "Șterge datele și parola salvată",
                    color = colors.gradeLow,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable(onClick = onLogoutForgetAll)
                        .padding(vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun DossierRow(label: String, value: String) {
    val colors = SimeTheme.colors
    if (value.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = colors.textMuted, fontSize = 13.sp)
        Text(text = value, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
