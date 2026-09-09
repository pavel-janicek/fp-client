package com.fpclient.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fpclient.android.data.dto.ActivityTypes
import com.fpclient.android.data.dto.TimelineActivityDto
import com.fpclient.android.util.Format
import com.fpclient.android.util.UrlBuilder

@Composable
fun ActivityCard(
    activity: TimelineActivityDto,
    serverUrl: String,
    unitSystem: String,
    onClick: () -> Unit,
    onAuthorClick: ((String) -> Unit)? = null,
) {
    val authorHandle = activity.fullHandle ?: activity.username?.let { "@$it" }
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = ActivityTypes.icon(activity.activityType), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activity.title ?: Format.uppercaseFirst(activity.activityType),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append(activity.displayName ?: activity.username ?: "")
                            val date = Format.relative(activity.startedAt ?: activity.createdAt)
                            if (date.isNotBlank()) append(" · $date")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = if (onAuthorClick != null && authorHandle != null) Modifier.clickable { onAuthorClick(authorHandle) } else Modifier,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val mapUrl = activity.mapImageUrl?.let { UrlBuilder.avatar(serverUrl, it) }
            if (mapUrl != null) {
                Spacer(Modifier.height(10.dp))
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(mapUrl).crossfade(true).build(),
                    contentDescription = activity.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp)),
                )
                } else if (activity.simplifiedTrack != null) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(12.dp)).background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer),
                        ),
                    ),
                ) {
                    Text(
                        text = "🗺️ ${Format.uppercaseFirst(activity.activityType)}",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            if (!activity.description.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = activity.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricItem("Distance", Format.distance(activity.totalDistance, unitSystem))
                MetricItem("Time", Format.duration(activity.movingTimeSeconds ?: activity.totalDurationSeconds))
                MetricItem(
                    "Pace",
                    Format.pace(
                        activity.metrics?.averagePaceSeconds,
                        activity.metrics?.averageSpeed,
                        activity.movingTimeSeconds ?: activity.totalDurationSeconds,
                        activity.totalDistance,
                        unitSystem,
                    ),
                )
                MetricItem("Elev.", Format.elevation(activity.elevationGain, unitSystem))
            }
            val reactions = activity.reactionCounts
            if (!reactions.isNullOrEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    reactions.forEach { (emoji, count) ->
                        if (count > 0) {
                            Text(
                                text = "$emoji $count",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${activity.commentsCount ?: 0} 💬",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}