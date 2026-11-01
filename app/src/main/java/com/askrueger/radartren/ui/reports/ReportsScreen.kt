package com.askrueger.radartren.ui.reports

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.askrueger.radartren.R
import com.askrueger.radartren.data.local.ReportEntity
import com.askrueger.radartren.domain.PeriodType
import com.askrueger.radartren.domain.ReportPeriod
import com.askrueger.radartren.domain.SPAIN_ZONE
import com.askrueger.radartren.ui.common.periodTitle
import java.time.Instant

@Composable
fun ReportsScreen(reports: List<ReportEntity>, uriOf: (ReportEntity) -> Uri, modifier: Modifier = Modifier) {
    if (reports.isEmpty()) {
        Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.reports_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(reports, key = ReportEntity::id) { report -> ReportCard(report, uriOf) }
    }
}

@Composable
private fun ReportCard(report: ReportEntity, uriOf: (ReportEntity) -> Uri) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val start = Instant.ofEpochSecond(report.startEpoch).atZone(SPAIN_ZONE).toLocalDate()
    Card(Modifier.fillMaxWidth().clickable { open(context, uriOf(report)) }) {
        Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        when (report.type) {
                            PeriodType.DAY -> R.string.report_daily_title
                            PeriodType.WEEK -> R.string.report_weekly_title
                            PeriodType.MONTH -> R.string.report_monthly_title
                            PeriodType.YEAR -> R.string.report_yearly_title
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(periodTitle(ReportPeriod(report.type, start), locale), fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.reports_coverage, (report.coverage * 100).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { share(context, uriOf(report)) }) {
                Icon(Icons.Default.Share, stringResource(R.string.action_share))
            }
        }
    }
}

private fun open(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.reports_no_viewer, Toast.LENGTH_LONG).show()
    }
}

fun share(context: Context, uri: Uri, type: String = "application/pdf") {
    val intent = Intent(Intent.ACTION_SEND)
        .setType(type)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, null))
}
