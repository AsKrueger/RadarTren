package com.askrueger.radartren.ui.help

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.askrueger.radartren.R
import com.askrueger.radartren.domain.DelayLevel
import com.askrueger.radartren.ui.common.delayColorHex

/** Renfe's customer line, as published in the agency.txt of its official GTFS. */
private const val RENFE_PHONE = "912320320"
private const val EMERGENCY_PHONE = "112"

/** A short guide written for people who have never used an app like this one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        HelpContent()
    }
}

@Composable
private fun HelpContent() {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(stringResource(R.string.help_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Section(R.string.help_dots_title, R.string.help_dots_body) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Swatch(DelayLevel.ON_TIME, R.string.legend_on_time)
                Swatch(DelayLevel.MINOR, R.string.legend_minor)
                Swatch(DelayLevel.MODERATE, R.string.legend_moderate)
                Swatch(DelayLevel.SEVERE, R.string.legend_severe)
            }
        }
        Section(R.string.help_rings_title, R.string.help_rings_body)
        Section(R.string.help_search_title, R.string.help_search_body)
        Section(R.string.help_alerts_title, R.string.help_alerts_body)
        Section(R.string.help_trouble_title, R.string.help_trouble_body) {
            OutlinedButton(onClick = { dial(context, RENFE_PHONE) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Call, null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.help_call_renfe))
            }
        }
        Button(
            onClick = { dial(context, EMERGENCY_PHONE) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Icon(Icons.Default.LocalHospital, null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.help_call_112), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Section(title: Int, body: Int, extra: @Composable () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(2.dp))
        extra()
    }
}

@Composable
private fun Swatch(level: DelayLevel, label: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(16.dp).background(Color(android.graphics.Color.parseColor(delayColorHex(level))), CircleShape))
        Spacer(Modifier.size(10.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Opens the dialer with the number typed in. Calling still needs the person's own tap. */
private fun dial(context: Context, number: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$number".toUri()))
    } catch (_: ActivityNotFoundException) {
        // Tablets without telephony: the number stays visible on the button.
    }
}
