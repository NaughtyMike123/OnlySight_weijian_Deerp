package com.focusai.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusai.app.R
import com.focusai.app.viewmodel.SettingsUiState
import com.focusai.app.viewmodel.SettingsViewModel

private enum class SettingsSubPage {
    MENU,
    PREMIUM,
    CUSTOM_API,
    PROMPT
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val savedLabel = stringResource(R.string.settings_saved)
    val promptSavedLabel = stringResource(R.string.settings_prompt_saved)
    val premiumModeEnabledLabel = stringResource(R.string.settings_premium_mode_enabled)
    var subPage by remember { mutableStateOf(SettingsSubPage.MENU) }

    LaunchedEffect(uiState.savedMessage) {
        uiState.savedMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearSavedMessage()
        }
    }
    LaunchedEffect(uiState.promptSavedMessage) {
        uiState.promptSavedMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearPromptSavedMessage()
        }
    }
    LaunchedEffect(uiState.connectionMessage) {
        uiState.connectionMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(uiState.premiumConnectionMessage) {
        uiState.premiumConnectionMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(uiState.premiumErrorMessage) {
        uiState.premiumErrorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearPremiumError()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(20.dp))
        when (subPage) {
            SettingsSubPage.MENU -> {
                SettingsEntryCard(
                    title = stringResource(R.string.settings_entry_premium_mode),
                    subtitle = stringResource(R.string.settings_entry_premium_desc),
                    subtle = false,
                    onClick = { subPage = SettingsSubPage.PREMIUM }
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsEntryCard(
                    title = stringResource(R.string.settings_entry_custom_mode),
                    subtitle = stringResource(R.string.settings_entry_custom_desc),
                    subtle = false,
                    onClick = { subPage = SettingsSubPage.CUSTOM_API }
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsEntryCard(
                    title = stringResource(R.string.settings_entry_advanced_prompt),
                    subtitle = stringResource(R.string.settings_entry_advanced_prompt_desc),
                    subtle = true,
                    onClick = { subPage = SettingsSubPage.PROMPT }
                )
            }
            SettingsSubPage.PREMIUM -> {
                BackHeader(
                    title = stringResource(R.string.settings_entry_premium_mode),
                    onBack = { subPage = SettingsSubPage.MENU }
                )
                Spacer(modifier = Modifier.height(10.dp))
                PremiumModeSettingsContent(
                    uiState = uiState,
                    onActivationCodeChange = viewModel::updateActivationCode,
                    onActivate = { viewModel.activatePremium(premiumModeEnabledLabel) },
                    onTestConnection = viewModel::testPremiumConnection
                )
            }
            SettingsSubPage.CUSTOM_API -> {
                BackHeader(
                    title = stringResource(R.string.settings_entry_custom_mode),
                    onBack = { subPage = SettingsSubPage.MENU }
                )
                Spacer(modifier = Modifier.height(10.dp))
                CustomApiSettingsContent(
                    uiState = uiState,
                    onBaseUrlChange = viewModel::updateBaseUrl,
                    onApiKeyChange = viewModel::updateApiKey,
                    onModelChange = viewModel::updateModel,
                    onSave = { viewModel.saveSettings(savedLabel) },
                    onTestConnection = viewModel::testConnection
                )
            }
            SettingsSubPage.PROMPT -> {
                BackHeader(
                    title = stringResource(R.string.settings_prompt_title),
                    onBack = { subPage = SettingsSubPage.MENU }
                )
                Spacer(modifier = Modifier.height(10.dp))
                PromptSettingsContent(
                    uiState = uiState,
                    onToggleCustomPrompt = viewModel::toggleUseCustomPrompt,
                    onPromptDraftChange = viewModel::updatePromptTemplateDraft,
                    onSavePrompt = { viewModel.savePromptTemplate(promptSavedLabel) }
                )
            }
        }
    }
}

@Composable
private fun BackHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        TextButton(onClick = onBack) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = null)
            Text(text = title)
        }
    }
}

@Composable
private fun SettingsEntryCard(
    title: String,
    subtitle: String,
    subtle: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (subtle) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = title,
                style = if (subtle) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                fontWeight = if (subtle) FontWeight.Medium else FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = if (subtle) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PremiumModeSettingsContent(
    uiState: SettingsUiState,
    onActivationCodeChange: (String) -> Unit,
    onActivate: () -> Unit,
    onTestConnection: () -> Unit
) {
    val showActivationForm = !uiState.premiumActive || uiState.premiumExpired

    if (uiState.premiumExpired) {
        Text(
            text = stringResource(R.string.settings_premium_status_expired),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    if (uiState.premiumActive) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_premium_status_active),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formatPremiumExpiry(uiState.premiumExpiresAtMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showActivationForm) {
        OutlinedTextField(
            value = uiState.activationCodeDraft,
            onValueChange = onActivationCodeChange,
            label = { Text(stringResource(R.string.settings_premium_activation_code)) },
            placeholder = { Text(stringResource(R.string.settings_premium_activation_code_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !uiState.premiumActivating
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_premium_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onActivate,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.premiumActivating
        ) {
            if (uiState.premiumActivating) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            } else {
                Text(stringResource(R.string.settings_premium_activate))
            }
        }
    }

    if (uiState.premiumActive) {
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onTestConnection,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.premiumTestingConnection
        ) {
            if (uiState.premiumTestingConnection) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            } else {
                Text(stringResource(R.string.settings_test_connection))
            }
        }
    }
}

@Composable
private fun formatPremiumExpiry(expiresAtMillis: Long?): String {
    return if (expiresAtMillis == null) {
        stringResource(R.string.settings_premium_expires_never)
    } else {
        val formatted = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(expiresAtMillis))
        stringResource(R.string.settings_premium_expires_at, formatted)
    }
}

@Composable
private fun CustomApiSettingsContent(
    uiState: SettingsUiState,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onSave: () -> Unit,
    onTestConnection: () -> Unit
) {
    OutlinedTextField(
        value = uiState.baseUrl,
        onValueChange = onBaseUrlChange,
        label = { Text(stringResource(R.string.settings_base_url)) },
        placeholder = { Text(stringResource(R.string.settings_base_url_hint)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = uiState.apiKey,
        onValueChange = onApiKeyChange,
        label = { Text(stringResource(R.string.settings_api_key)) },
        placeholder = { Text(stringResource(R.string.settings_api_key_hint)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = uiState.model,
        onValueChange = onModelChange,
        label = { Text(stringResource(R.string.settings_model)) },
        placeholder = { Text(stringResource(R.string.settings_model_hint)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.settings_byok_notice),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(24.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onSave,
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.settings_save))
        }
        OutlinedButton(
            onClick = onTestConnection,
            modifier = Modifier.weight(1f),
            enabled = !uiState.testingConnection
        ) {
            if (uiState.testingConnection) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            } else {
                Text(stringResource(R.string.settings_test_connection))
            }
        }
    }
    uiState.connectionMessage?.let { msg ->
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = msg,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PromptSettingsContent(
    uiState: SettingsUiState,
    onToggleCustomPrompt: (Boolean) -> Unit,
    onPromptDraftChange: (String) -> Unit,
    onSavePrompt: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_prompt_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.settings_prompt_enable_custom))
                Switch(
                    checked = uiState.useCustomPromptTemplate,
                    onCheckedChange = onToggleCustomPrompt
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.promptTemplateDraft,
                onValueChange = onPromptDraftChange,
                label = { Text(stringResource(R.string.settings_prompt_editor_label)) },
                placeholder = { Text(stringResource(R.string.settings_prompt_editor_hint)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.useCustomPromptTemplate,
                minLines = 6,
                maxLines = 12
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_prompt_preview),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.promptPreview,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                maxLines = 12,
                readOnly = true,
                enabled = true
            )

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onSavePrompt,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_prompt_save))
            }
        }
    }
}
