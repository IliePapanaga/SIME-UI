package md.sime.agenda.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import md.sime.agenda.model.GradeCalculator
import md.sime.agenda.model.PortalSnapshot
import md.sime.agenda.ui.components.HairlineDivider
import md.sime.agenda.ui.components.SimeCard
import md.sime.agenda.ui.theme.SimeTheme

@Composable
fun MessagesScreen(
    snapshot: PortalSnapshot?,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onJumpToScheduleDate: (dateIso: String, subject: String?) -> Unit
) {
    val colors = SimeTheme.colors

    if (snapshot == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background),
            contentAlignment = Alignment.Center
        ) {
            Text("Se încarcă mesajele...", color = colors.textSecondary, fontSize = 14.sp)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!hasNotificationPermission) {
            item {
                SimeCard {
                    Text(
                        text = "NOTIFICĂRI DEZACTIVATE",
                        color = colors.textMuted,
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Activează notificările pentru a primi alerte instant când un profesor îți scrie.",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onRequestNotificationPermission,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.75.dp, colors.border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary)
                    ) {
                        Text("Activează notificările")
                    }
                }
            }
        }

        if (snapshot.messages.isEmpty()) {
            item {
                SimeCard {
                    Text(
                        text = "Nu ai niciun mesaj de la profesori.",
                        color = colors.textSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            items(snapshot.messages, key = { it.id }) { msg ->
                val dateIso = GradeCalculator.parseRomanianDateToIso(msg.date)
                val openAction: (() -> Unit)? = if (dateIso != null) {
                    { onJumpToScheduleDate(dateIso, msg.subject.ifBlank { null }) }
                } else {
                    null
                }
                val kicker = buildList {
                    if (msg.subject.isNotBlank()) add(msg.subject.uppercase())
                    if (msg.date.isNotBlank()) add(msg.date.uppercase())
                }.joinToString("  ·  ")

                SimeCard(
                    contentPadding = PaddingValues(14.dp),
                    onClick = openAction
                ) {
                    if (kicker.isNotBlank()) {
                        Text(
                            text = kicker,
                            color = colors.textMuted,
                            fontSize = 10.sp,
                            letterSpacing = 1.1.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = msg.professor,
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = msg.text,
                        color = colors.textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    if (!dateIso.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HairlineDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Deschide lecția (${GradeCalculator.formatIsoToShortDisplay(dateIso)})  ›",
                            color = colors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}
