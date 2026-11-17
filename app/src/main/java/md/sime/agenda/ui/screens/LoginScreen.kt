package md.sime.agenda.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import md.sime.agenda.data.SavedCredentials
import md.sime.agenda.model.UserType
import md.sime.agenda.ui.components.SegmentedGlassToggle
import md.sime.agenda.ui.components.SimeCard
import md.sime.agenda.ui.theme.SimeTheme

@Composable
fun LoginScreen(
    savedCredentials: SavedCredentials?,
    isLoading: Boolean,
    errorMessage: String?,
    onLogin: (username: String, password: String, userType: UserType, stayLoggedIn: Boolean) -> Unit,
    onOneTapLogin: () -> Unit
) {
    val colors = SimeTheme.colors
    var username by rememberSaveable { mutableStateOf(savedCredentials?.username.orEmpty()) }
    var password by rememberSaveable { mutableStateOf("") }
    var userType by rememberSaveable { mutableStateOf(savedCredentials?.userType ?: UserType.ELEV) }
    var stayLoggedIn by rememberSaveable { mutableStateOf(true) }
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "SIME  ·  MOLDOVA",
                color = colors.textMuted,
                fontSize = 10.sp,
                letterSpacing = 1.6.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Agenda",
                color = colors.textPrimary,
                fontSize = 30.sp,
                letterSpacing = (-0.5).sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Conectează-te la catalogul tău școlar",
                color = colors.textSecondary,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 1-Tap Login card when credentials are saved on device
            if (savedCredentials != null) {
                SimeCard(
                    isHighlighted = true,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Text(
                        text = "CONT SALVAT LOCAL",
                        color = colors.textMuted,
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${savedCredentials.username}  ·  ${savedCredentials.userType.labelRo}",
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onOneTapLogin,
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.textPrimary,
                            contentColor = colors.background
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colors.background
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Se conectează...")
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Intră cu o singură apăsare",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            SimeCard(contentPadding = PaddingValues(16.dp)) {
                Text(
                    text = if (savedCredentials != null) "SAU ALT CONT" else "AUTENTIFICARE",
                    color = colors.textMuted,
                    fontSize = 10.sp,
                    letterSpacing = 1.3.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))

                // User Type Segmented Switcher
                SegmentedGlassToggle(
                    options = listOf("Elev", "Părinte"),
                    selectedIndex = if (userType == UserType.ELEV) 0 else 1,
                    onSelect = { userType = if (it == 0) UserType.ELEV else UserType.PARINTE }
                )

                Spacer(modifier = Modifier.height(14.dp))

                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.textPrimary,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedLabelColor = colors.textSecondary,
                    unfocusedLabelColor = colors.textMuted,
                    cursorColor = colors.textPrimary,
                    focusedContainerColor = colors.background,
                    unfocusedContainerColor = colors.background
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Nume utilizator") },
                    singleLine = true,
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(10.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Parolă") },
                    singleLine = true,
                    enabled = !isLoading,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = "Arată/ascunde parola",
                                tint = colors.textSecondary
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onLogin(username, password, userType, stayLoggedIn) }
                    ),
                    shape = RoundedCornerShape(10.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Rămâi conectat automat",
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Datele sunt salvate criptat pe telefon",
                            color = colors.textMuted,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = stayLoggedIn,
                        onCheckedChange = { stayLoggedIn = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.background,
                            checkedTrackColor = colors.textPrimary,
                            uncheckedThumbColor = colors.textSecondary,
                            uncheckedTrackColor = colors.surfaceElevated
                        )
                    )
                }

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage,
                        color = colors.gradeLow,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (savedCredentials == null) {
                    Button(
                        onClick = { onLogin(username, password, userType, stayLoggedIn) },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.textPrimary,
                            contentColor = colors.background
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colors.background
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Se autentifică...")
                        } else {
                            Text(
                                text = "Autentificare",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { onLogin(username, password, userType, stayLoggedIn) },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.75.dp, colors.border),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.textPrimary
                        )
                    ) {
                        Text(
                            text = "Autentificare cu aceste date",
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Datele sunt preluate direct din agenda.sime.md și stocate exclusiv local pe dispozitivul tău.",
                color = colors.textMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}
