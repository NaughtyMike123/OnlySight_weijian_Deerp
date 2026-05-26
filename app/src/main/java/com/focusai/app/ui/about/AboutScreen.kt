package com.focusai.app.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusai.app.R
import com.focusai.app.data.prefs.AppLanguage
import com.focusai.app.data.prefs.GITHUB_ISSUES_URL
import com.focusai.app.viewmodel.AboutViewModel

@Composable
fun AboutScreen(viewModel: AboutViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.about_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(text = stringResource(R.string.about_version, uiState.versionName))

        Button(
            onClick = viewModel::showDonateDialog,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.about_support_dev))
        }

        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_ISSUES_URL))
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.about_bug_feedback))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.about_language),
            fontWeight = FontWeight.SemiBold
        )

        LanguageOption(
            label = stringResource(R.string.lang_system),
            selected = uiState.language == AppLanguage.SYSTEM,
            onClick = { viewModel.setLanguage(AppLanguage.SYSTEM) }
        )
        LanguageOption(
            label = stringResource(R.string.lang_chinese),
            selected = uiState.language == AppLanguage.CHINESE,
            onClick = { viewModel.setLanguage(AppLanguage.CHINESE) }
        )
        LanguageOption(
            label = stringResource(R.string.lang_english),
            selected = uiState.language == AppLanguage.ENGLISH,
            onClick = { viewModel.setLanguage(AppLanguage.ENGLISH) }
        )
    }

    if (uiState.showDonateDialog) {
        AlertDialog(
            onDismissRequest = viewModel::hideDonateDialog,
            title = { Text(stringResource(R.string.donate_title)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = R.drawable.donate_qr),
                        contentDescription = stringResource(R.string.donate_qr_desc),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .padding(8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.donate_message),
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::hideDonateDialog) {
                    Text(stringResource(R.string.donate_close))
                }
            }
        )
    }
}

@Composable
private fun LanguageOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = !selected
    ) {
        Text(if (selected) "✓ $label" else label)
    }
}
