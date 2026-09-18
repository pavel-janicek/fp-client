package com.fpclient.android.ui.record

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fpclient.android.data.dto.ActivityTypes
import com.fpclient.android.recording.RecordingState
import com.fpclient.android.recording.TrackRecordingBus
import com.fpclient.android.recording.TrackRecordingController
import com.fpclient.android.util.Format

/**
 * App-wide "recording in progress" banner (Iteration 8c), shown by MainScaffold above
 * whichever tab is open whenever a session is active. It answers the app-wide guard
 * question ("is something already recording?") everywhere, shows the live elapsed time
 * and chosen activity, offers a quick pause/resume, and tapping it jumps straight into
 * the live recording screen.
 */
@Composable
fun RecordingBanner(onOpen: () -> Unit) {
    val session by TrackRecordingBus.session.collectAsState()
    val s = session ?: return
    val context = LocalContext.current
    val now = rememberTicker()
    val paused = s.state == RecordingState.PAUSED

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(ActivityTypes.icon(s.activityType), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (paused) "Recording paused" else "Recording ${activityLabel(s.activityType)}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${Format.duration(s.elapsedAt(now) / 1000)} · tap to open",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            IconButton(onClick = {
                when (s.state) {
                    RecordingState.RECORDING -> TrackRecordingController.pause(context)
                    RecordingState.PAUSED -> TrackRecordingController.resume(context)
                }
            }) {
                Icon(
                    if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (paused) "Resume recording" else "Pause recording",
                )
            }
        }
    }
}
