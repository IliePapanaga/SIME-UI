package md.sime.agenda

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import md.sime.agenda.model.ThemeMode
import md.sime.agenda.notifications.NotificationHelper
import md.sime.agenda.ui.AppTab
import md.sime.agenda.ui.MainViewModel
import md.sime.agenda.ui.components.HairlineDivider
import md.sime.agenda.ui.screens.AbsencesScreen
import md.sime.agenda.ui.screens.DossierSheet
import md.sime.agenda.ui.screens.GradesScreen
import md.sime.agenda.ui.screens.HomeScreen
import md.sime.agenda.ui.screens.LoginScreen
import md.sime.agenda.ui.screens.MessagesScreen
import md.sime.agenda.ui.screens.ScheduleScreen
import md.sime.agenda.ui.theme.SimeAgendaTheme
import md.sime.agenda.ui.theme.SimeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntentTab(intent)

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

            LaunchedEffect(themeMode) {
                val darkBars = themeMode != ThemeMode.LIGHT
                enableEdgeToEdge(
                    statusBarStyle = if (darkBars) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (darkBars) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                )
            }

            SimeAgendaTheme(themeMode = themeMode) {
                SimeAppRoot(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntentTab(intent)
    }

    private fun handleIntentTab(intent: Intent?) {
        val target = intent?.getStringExtra(NotificationHelper.EXTRA_OPEN_TAB)
        viewModel.handleDeepLinkTab(target)
    }
}

@Composable
private fun SimeAppRoot(viewModel: MainViewModel) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val savedCredentials by viewModel.savedCredentials.collectAsStateWithLifecycle()
    val loginLoading by viewModel.loginLoading.collectAsStateWithLifecycle()
    val loginError by viewModel.loginError.collectAsStateWithLifecycle()

    if (!isLoggedIn) {
        LoginScreen(
            savedCredentials = savedCredentials,
            isLoading = loginLoading,
            errorMessage = loginError,
            onLogin = viewModel::login,
            onOneTapLogin = viewModel::oneTapLogin
        )
        return
    }

    val context = LocalContext.current
    var hasNotifPermission by remember {
        mutableStateOf(NotificationHelper.hasNotificationPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val scheduleEvents by viewModel.scheduleEvents.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncError by viewModel.syncError.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val completedHomeworkIds by viewModel.completedHomeworkIds.collectAsStateWithLifecycle()
    val selectedScheduleDateIso by viewModel.selectedScheduleDateIso.collectAsStateWithLifecycle()
    val highlightedSubject by viewModel.highlightedLessonSubject.collectAsStateWithLifecycle()
    val selectedSemester by viewModel.selectedSemester.collectAsStateWithLifecycle()
    val simulatedMarks by viewModel.simulatedMarks.collectAsStateWithLifecycle()
    val liveOverallAverage by viewModel.liveOverallAverage.collectAsStateWithLifecycle()
    val absenceSortMode by viewModel.absenceSortMode.collectAsStateWithLifecycle()
    val absenceFilterMode by viewModel.absenceFilterMode.collectAsStateWithLifecycle()
    val lessonDetailState by viewModel.lessonDetailState.collectAsStateWithLifecycle()
    val showDossierSheet by viewModel.showDossierSheet.collectAsStateWithLifecycle()

    val colors = SimeTheme.colors

    Scaffold(
        containerColor = colors.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedTab.label,
                            color = colors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val sub = snapshot?.profile?.let { p ->
                            listOf(p.shortName, p.className).filter { it.isNotBlank() }.joinToString(" • ")
                        }.orEmpty()
                        if (sub.isNotBlank()) {
                            Text(
                                text = sub,
                                color = colors.textMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colors.accent
                            )
                        } else {
                            IconButton(
                                onClick = { viewModel.refresh(prefetchCalendar = true) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = "Actualizează",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        val initials = snapshot?.profile?.initials?.ifBlank { "AE" } ?: "AE"
                        val classLabel = snapshot?.profile?.className.orEmpty()
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.surface,
                            border = BorderStroke(1.dp, colors.border),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.openDossierSheet() }
                        ) {
                            Text(
                                text = if (classLabel.isNotBlank()) "$initials • $classLabel" else initials,
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                if (!syncError.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.gradeLow.copy(alpha = 0.12f))
                            .clickable { viewModel.dismissSyncError() }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = syncError ?: "",
                            color = colors.gradeLow,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Închide",
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                HairlineDivider()
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .navigationBarsPadding()
            ) {
                HairlineDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppTab.entries.forEach { tab ->
                        val selected = selectedTab == tab
                        val icon = when (tab) {
                            AppTab.HOME -> Icons.Outlined.Home
                            AppTab.SCHEDULE -> Icons.Outlined.CalendarToday
                            AppTab.GRADES -> Icons.Outlined.School
                            AppTab.ABSENCES -> Icons.Outlined.EventBusy
                            AppTab.MESSAGES -> Icons.Outlined.MailOutline
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { viewModel.selectTab(tab) }
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = tab.label,
                                tint = if (selected) colors.textPrimary else colors.textMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = tab.label,
                                color = if (selected) colors.textPrimary else colors.textMuted,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                AppTab.HOME -> HomeScreen(
                    snapshot = snapshot,
                    scheduleEvents = scheduleEvents,
                    liveOverallAverage = liveOverallAverage,
                    completedHomeworkIds = completedHomeworkIds,
                    onToggleHomework = viewModel::toggleHomeworkDone,
                    onJumpToScheduleDate = { dateIso, subj ->
                        viewModel.jumpToScheduleDate(dateIso, subj, openDetailAutomatically = subj != null)
                    },
                    onOpenGrades = { viewModel.selectTab(AppTab.GRADES) },
                    onOpenAbsences = { viewModel.selectTab(AppTab.ABSENCES) },
                    onOpenLessonDetail = viewModel::openLessonDetail
                )
                AppTab.SCHEDULE -> ScheduleScreen(
                    scheduleEvents = scheduleEvents,
                    selectedDateIso = selectedScheduleDateIso,
                    highlightedSubject = highlightedSubject,
                    lessonDetailState = lessonDetailState,
                    onSelectDate = viewModel::selectScheduleDate,
                    onOpenLessonDetail = viewModel::openLessonDetail,
                    onDismissLessonDetail = viewModel::closeLessonDetail
                )
                AppTab.GRADES -> GradesScreen(
                    snapshot = snapshot,
                    selectedSemester = selectedSemester,
                    simulatedMarks = simulatedMarks,
                    liveOverallAverage = liveOverallAverage,
                    onSelectSemester = viewModel::selectSemester,
                    onAddSimulatedMark = viewModel::addSimulatedMark,
                    onRemoveLastSimulatedMark = viewModel::removeLastSimulatedMark,
                    onResetSimulatedMarks = viewModel::resetSimulatedMarks,
                    onJumpToScheduleDate = { dateIso, subj ->
                        viewModel.jumpToScheduleDate(dateIso, subj, openDetailAutomatically = true)
                    }
                )
                AppTab.ABSENCES -> AbsencesScreen(
                    snapshot = snapshot,
                    sortMode = absenceSortMode,
                    filterMode = absenceFilterMode,
                    onSelectSortMode = viewModel::setAbsenceSortMode,
                    onSelectFilterMode = viewModel::setAbsenceFilterMode,
                    onJumpToScheduleDate = { dateIso, subj ->
                        viewModel.jumpToScheduleDate(dateIso, subj, openDetailAutomatically = false)
                    }
                )
                AppTab.MESSAGES -> MessagesScreen(
                    snapshot = snapshot,
                    hasNotificationPermission = hasNotifPermission,
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onJumpToScheduleDate = { dateIso, subj ->
                        viewModel.jumpToScheduleDate(dateIso, subj, openDetailAutomatically = subj != null)
                    }
                )
            }
        }
    }

    if (showDossierSheet) {
        DossierSheet(
            snapshot = snapshot,
            currentThemeMode = themeMode,
            onSelectTheme = viewModel::setThemeMode,
            onSwitchStudent = viewModel::switchStudent,
            onTestNotification = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.triggerTestNotification()
                }
            },
            onLogoutKeepQuickLogin = { viewModel.logout(forgetSavedCredentials = false) },
            onLogoutForgetAll = { viewModel.logout(forgetSavedCredentials = true) },
            onDismiss = viewModel::closeDossierSheet
        )
    }
}
