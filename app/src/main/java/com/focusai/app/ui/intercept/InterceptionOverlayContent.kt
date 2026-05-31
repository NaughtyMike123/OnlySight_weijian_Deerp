package com.focusai.app.ui.intercept

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focusai.app.R
import com.focusai.app.data.prefs.InterceptionMode
import com.focusai.app.ui.theme.FocusAITheme
import com.focusai.app.ui.theme.Indigo500
import kotlinx.coroutines.delay

@Composable
fun InterceptionOverlayContent(
    reason: String,
    mode: InterceptionMode,
    customCooldownText: String,
    customCooldownSeconds: Int,
    imageBitmap: ImageBitmap?,
    onDismiss: () -> Unit
) {
    FocusAITheme {
        val safeSeconds = customCooldownSeconds.coerceIn(3, 120)
        var remain by remember(mode, safeSeconds) { mutableIntStateOf(safeSeconds) }
        var countdownFinished by remember(mode, safeSeconds) {
            mutableStateOf(mode != InterceptionMode.CUSTOM_TIMEOUT)
        }

        LaunchedEffect(mode, safeSeconds) {
            if (mode == InterceptionMode.CUSTOM_TIMEOUT) {
                while (remain > 0) {
                    delay(1_000L)
                    remain -= 1
                }
                countdownFinished = true
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(36.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            )

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = when (mode) {
                        InterceptionMode.CUSTOM_TIMEOUT -> stringResource(R.string.meta_intercept_custom_title)
                        InterceptionMode.AI_PERSUASION -> stringResource(R.string.meta_intercept_ai_title)
                        InterceptionMode.INSTANT_KILL -> stringResource(R.string.meta_intercept_ai_title)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .background(
                            color = Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = if (mode == InterceptionMode.CUSTOM_TIMEOUT) {
                            customCooldownText.ifBlank { reason }
                        } else {
                            reason
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
                if (mode == InterceptionMode.CUSTOM_TIMEOUT) {
                    Text(
                        text = LocalContext.current.getString(R.string.meta_intercept_countdown, remain),
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.size(width = 180.dp, height = 56.dp),
                        textAlign = TextAlign.Center
                    )
                    if (countdownFinished) {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Indigo500)
                        ) {
                            Text(
                                text = stringResource(R.string.meta_intercept_dismiss),
                                color = Color.White
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, Color.White),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text(text = stringResource(R.string.meta_intercept_confirm))
                    }
                }
            }
        }
    }
}
