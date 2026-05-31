package com.focusai.app.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusai.app.R
import com.focusai.app.data.db.InterceptionEntity
import com.focusai.app.util.TimeFormatter
import com.focusai.app.viewmodel.StatsViewModel

/**
 * 统计页：今日被打断次数 + 最近 10 条打断记录。
 * 视觉版没有专注时长统计，旧番茄钟相关卡片已删除。
 */
@Composable
fun StatsScreen(viewModel: StatsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedRecord by remember { mutableStateOf<InterceptionEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = stringResource(R.string.stats_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(20.dp))

        StatCard(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.stats_interceptions),
            value = uiState.interceptionCount.toString()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.stats_recent_interceptions),
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.recentInterceptions.isEmpty()) {
            Text(
                text = stringResource(R.string.stats_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.recentInterceptions) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedRecord = item }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = TimeFormatter.formatTimestamp(item.timestamp))
                            if (item.aiReason.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.aiReason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedRecord?.let { record ->
        AlertDialog(
            onDismissRequest = { selectedRecord = null },
            title = { Text(stringResource(R.string.stats_record_detail_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailLine(stringResource(R.string.stats_record_time), TimeFormatter.formatTimestamp(record.timestamp))
                    DetailLine(stringResource(R.string.stats_record_package), record.packageName.ifBlank { "-" })
                    DetailLine(stringResource(R.string.stats_record_reason_detail), record.aiReason.ifBlank { "-" })
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedRecord = null }) {
                    Text(stringResource(R.string.donate_close))
                }
            }
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Text(text = "$label$value", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
