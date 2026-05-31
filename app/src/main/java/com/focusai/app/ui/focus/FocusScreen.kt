package com.focusai.app.ui.focus

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusai.app.R
import com.focusai.app.data.prefs.InterceptionMode
import com.focusai.app.service.VisualSupervisionService
import com.focusai.app.ui.theme.Indigo500
import com.focusai.app.ui.theme.IndigoLight
import com.focusai.app.ui.theme.Ink200
import com.focusai.app.ui.theme.Ink500
import com.focusai.app.ui.theme.Teal600
import com.focusai.app.util.AccessibilityUtils
import com.focusai.app.util.OverlayPermissionHelper
import com.focusai.app.viewmodel.FocusViewModel
import com.focusai.app.viewmodel.MonitorableApp

/**
 * 首页：监督开关 + 无障碍状态 + 监督规则 + 模式选择 + 应用监控名单。
 */
@Composable
fun FocusScreen(viewModel: FocusViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val savedLabel = stringResource(R.string.rules_saved)
    val modeSavedLabel = stringResource(R.string.mode_saved)
    val permissionDeniedLabel = stringResource(R.string.visual_supervision_permission_denied)
    val overlayRequiredLabel = stringResource(R.string.overlay_permission_required)

    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { }

    // 系统级"允许录屏"对话框 Launcher：
    // 用户点击开关 → launch(...) → 系统弹窗 → 同意后回到 onResult，把凭据交给前台服务。
    val mediaProjectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            VisualSupervisionService.start(context, result.resultCode, data)
            viewModel.persistSupervisionEnabled(true)
        } else {
            Toast.makeText(context, permissionDeniedLabel, Toast.LENGTH_SHORT).show()
            viewModel.persistSupervisionEnabled(false)
        }
    }

    fun startSupervisionWithChecks() {
        if (needsOverlayPermission(uiState.selectedInterceptionMode) &&
            !OverlayPermissionHelper.canDrawOverlays(context)
        ) {
            Toast.makeText(context, overlayRequiredLabel, Toast.LENGTH_LONG).show()
            overlaySettingsLauncher.launch(OverlayPermissionHelper.createSettingsIntent(context))
            return
        }
        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshAccessibilityStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.rulesSavedMessage) {
        uiState.rulesSavedMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearRulesSavedMessage()
        }
    }
    LaunchedEffect(uiState.modeSavedMessage) {
        uiState.modeSavedMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearModeSavedMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(IndigoLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                    tint = Indigo500,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.focus_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        SupervisionCard(
            supervisionEnabled = uiState.supervisionEnabled,
            accessibilityGranted = uiState.accessibilityGranted,
            onToggle = { wantEnabled ->
                if (wantEnabled) {
                    startSupervisionWithChecks()
                } else {
                    VisualSupervisionService.stop(context)
                    viewModel.persistSupervisionEnabled(false)
                }
            }
        )

        AccessibilityCard(
            accessibilityGranted = uiState.accessibilityGranted,
            onOpenAccessibility = { AccessibilityUtils.openAccessibilitySettings(context) }
        )

        RulesCard(
            focusGoal = uiState.focusGoalDraft,
            forbiddenTags = uiState.forbiddenTagsDraft,
            dirty = uiState.rulesDirty,
            onFocusGoalChange = viewModel::updateFocusGoalDraft,
            onForbiddenTagsChange = viewModel::updateForbiddenTagsDraft,
            onSave = { viewModel.saveRules(savedLabel) }
        )

        ModeSelectionCard(
            selectedMode = uiState.selectedInterceptionMode,
            customCooldownText = uiState.customCooldownTextDraft,
            customCooldownSeconds = uiState.customCooldownSecondsDraft,
            dirty = uiState.modeDirty,
            onModeSelected = viewModel::updateInterceptionMode,
            onCooldownTextChange = viewModel::updateCooldownTextDraft,
            onCooldownSecondsChange = viewModel::updateCooldownSecondsDraft,
            onSave = {
                viewModel.saveInterceptionMode(modeSavedLabel)
                if (needsOverlayPermission(uiState.selectedInterceptionMode) &&
                    !OverlayPermissionHelper.canDrawOverlays(context)
                ) {
                    Toast.makeText(context, overlayRequiredLabel, Toast.LENGTH_LONG).show()
                    overlaySettingsLauncher.launch(OverlayPermissionHelper.createSettingsIntent(context))
                }
            }
        )

        AppMonitorCard(
            onManageClick = viewModel::openAppMonitorDialog
        )
        if (uiState.appMonitorDialogVisible) {
            AppMonitorPickerDialog(
                apps = uiState.monitorableApps,
                blacklist = uiState.appMonitorBlacklistDraft,
                searchQuery = uiState.appMonitorSearchQuery,
                onSearchChange = viewModel::updateAppMonitorSearchQuery,
                onDismiss = viewModel::closeAppMonitorDialog,
                onSetGroup = viewModel::setAppMonitorGroup
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Supervision toggle card
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun SupervisionCard(
    supervisionEnabled: Boolean,
    accessibilityGranted: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val cardBg by animateColorAsState(
        targetValue = if (supervisionEnabled) Indigo500 else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(400),
        label = "cardBg"
    )
    val cardElevation by animateDpAsState(
        targetValue = if (supervisionEnabled) 6.dp else 0.dp,
        animationSpec = tween(400),
        label = "cardElevation"
    )
    val textColor = if (supervisionEnabled) Color.White else MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(cardElevation, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (supervisionEnabled) {
                        stringResource(R.string.supervision_enabled)
                    } else {
                        stringResource(R.string.supervision_disabled)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (accessibilityGranted) {
                        stringResource(R.string.accessibility_granted)
                    } else {
                        stringResource(R.string.accessibility_not_granted)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (supervisionEnabled) Color.White.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = supervisionEnabled,
                onCheckedChange = onToggle,
                enabled = accessibilityGranted,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color.White.copy(alpha = 0.4f),
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Accessibility-only permission card
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun AccessibilityCard(
    accessibilityGranted: Boolean,
    onOpenAccessibility: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenAccessibility),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = Indigo500,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.perm_section_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            PermissionRow(
                icon = Icons.Outlined.Accessibility,
                iconTint = Indigo500,
                iconBg = IndigoLight,
                title = stringResource(R.string.open_accessibility_settings),
                subtitle = if (accessibilityGranted) {
                    stringResource(R.string.accessibility_granted)
                } else {
                    stringResource(R.string.accessibility_not_granted)
                },
                granted = accessibilityGranted
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    granted: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (granted) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = Teal600,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Rules card
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun RulesCard(
    focusGoal: String,
    forbiddenTags: String,
    dirty: Boolean,
    onFocusGoalChange: (String) -> Unit,
    onForbiddenTagsChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = null,
                    tint = Indigo500,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.rules_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.rules_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = focusGoal,
                onValueChange = onFocusGoalChange,
                label = { Text(stringResource(R.string.rules_focus_goal_label)) },
                placeholder = { Text(stringResource(R.string.rules_focus_goal_hint), color = Ink200) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Indigo500,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedLabelColor = Indigo500
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = forbiddenTags,
                onValueChange = onForbiddenTagsChange,
                label = { Text(stringResource(R.string.rules_forbidden_tags_label)) },
                placeholder = { Text(stringResource(R.string.rules_forbidden_tags_hint), color = Ink200) },
                supportingText = { Text(stringResource(R.string.rules_forbidden_tags_help), color = Ink500) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Indigo500,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedLabelColor = Indigo500
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            AnimatedVisibility(
                visible = dirty,
                enter = fadeIn(tween(200)) + expandVertically(),
                exit = fadeOut(tween(200)) + shrinkVertically()
            ) {
                Button(
                    onClick = onSave,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo500),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.rules_save), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ModeSelectionCard(
    selectedMode: InterceptionMode,
    customCooldownText: String,
    customCooldownSeconds: String,
    dirty: Boolean,
    onModeSelected: (InterceptionMode) -> Unit,
    onCooldownTextChange: (String) -> Unit,
    onCooldownSecondsChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.mode_selection_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.mode_selection_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            ModeOptionRow(
                text = stringResource(R.string.mode_option_instant_kill),
                selected = selectedMode == InterceptionMode.INSTANT_KILL,
                onSelect = { onModeSelected(InterceptionMode.INSTANT_KILL) }
            )
            ModeOptionRow(
                text = stringResource(R.string.mode_option_custom_timeout),
                selected = selectedMode == InterceptionMode.CUSTOM_TIMEOUT,
                onSelect = { onModeSelected(InterceptionMode.CUSTOM_TIMEOUT) }
            )
            ModeOptionRow(
                text = stringResource(R.string.mode_option_ai_persuasion),
                selected = selectedMode == InterceptionMode.AI_PERSUASION,
                onSelect = { onModeSelected(InterceptionMode.AI_PERSUASION) }
            )

            AnimatedVisibility(
                visible = selectedMode == InterceptionMode.CUSTOM_TIMEOUT,
                enter = fadeIn(tween(180)) + expandVertically(),
                exit = fadeOut(tween(180)) + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customCooldownText,
                        onValueChange = onCooldownTextChange,
                        label = { Text(stringResource(R.string.mode_custom_text_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customCooldownSeconds,
                        onValueChange = onCooldownSecondsChange,
                        label = { Text(stringResource(R.string.mode_custom_seconds_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        )
                    )
                }
            }

            AnimatedVisibility(
                visible = dirty,
                enter = fadeIn(tween(200)) + expandVertically(),
                exit = fadeOut(tween(200)) + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onSave,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo500),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.mode_save), color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeOptionRow(
    text: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AppMonitorCard(
    onManageClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.app_monitor_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onManageClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.app_monitor_manage_apps), color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

private fun needsOverlayPermission(mode: InterceptionMode): Boolean {
    return mode == InterceptionMode.CUSTOM_TIMEOUT || mode == InterceptionMode.AI_PERSUASION
}

@Composable
private fun AppMonitorPickerDialog(
    apps: List<MonitorableApp>,
    blacklist: Set<String>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSetGroup: (String, Boolean) -> Unit
) {
    val filtered = if (searchQuery.isBlank()) {
        apps
    } else {
        apps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_monitor_picker_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    label = { Text(stringResource(R.string.app_monitor_search_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppMonitorItemRow(
                            app = app,
                            isBlacklist = app.packageName in blacklist,
                            onSetGroup = onSetGroup
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.app_monitor_picker_done))
            }
        }
    )
}

@Composable
private fun AppMonitorItemRow(
    app: MonitorableApp,
    isBlacklist: Boolean,
    onSetGroup: (String, Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(packageName = app.packageName)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSetGroup(app.packageName, false) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isBlacklist) Indigo500 else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        text = stringResource(R.string.app_monitor_group_whitelist),
                        color = if (!isBlacklist) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
                Button(
                    onClick = { onSetGroup(app.packageName, true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBlacklist) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        text = stringResource(R.string.app_monitor_group_blacklist),
                        color = if (isBlacklist) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val iconSizePx = remember(density) { with(density) { 28.dp.roundToPx() } }
    val iconBitmap = remember(packageName, iconSizePx) {
        runCatching {
            context.packageManager
                .getApplicationIcon(packageName)
                .toBitmap(iconSizePx, iconSizePx)
                .asImageBitmap()
        }.getOrNull()
    }
    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap,
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
    } else {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(MaterialTheme.colorScheme.outlineVariant, CircleShape)
        )
    }
}
