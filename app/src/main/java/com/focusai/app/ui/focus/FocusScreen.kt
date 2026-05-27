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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusai.app.R
import com.focusai.app.service.VisualSupervisionService
import com.focusai.app.ui.theme.Indigo500
import com.focusai.app.ui.theme.IndigoLight
import com.focusai.app.ui.theme.Ink200
import com.focusai.app.ui.theme.Ink500
import com.focusai.app.ui.theme.Teal600
import com.focusai.app.util.AccessibilityUtils
import com.focusai.app.viewmodel.FocusViewModel

/**
 * 首页：监督开关 + 无障碍状态 + 监督规则草稿三件事。
 * 番茄钟、应用名单等与监督主线无关的功能已删除。
 */
@Composable
fun FocusScreen(viewModel: FocusViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val savedLabel = stringResource(R.string.rules_saved)
    val permissionDeniedLabel = stringResource(R.string.visual_supervision_permission_denied)

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
                    screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
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
        modifier = Modifier.fillMaxWidth(),
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
                granted = accessibilityGranted,
                onClick = onOpenAccessibility
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
    granted: Boolean,
    onClick: () -> Unit
) {
    val rowModifier = if (granted) {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    }

    Row(
        modifier = rowModifier,
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
            TextButton(
                onClick = onClick,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.open_accessibility_settings), style = MaterialTheme.typography.labelSmall)
            }
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
