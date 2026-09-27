package com.fpclient.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fpclient.android.util.UrlBuilder

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp)) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorState(
    message: String?,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    buttonLabel: String = "Retry",
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.error,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message ?: "Something went wrong",
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                Text(buttonLabel)
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: (@Composable () -> Unit)? = null,
    title: String,
    body: String? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon?.invoke()
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun UserAvatar(
    avatarUrl: String?,
    displayName: String?,
    serverUrl: String,
    size: Int = 40,
) {
    val context = LocalContext.current
    val resolved = UrlBuilder.avatar(serverUrl, avatarUrl)
    if (resolved != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(resolved)
                .crossfade(true)
                .build(),
            contentDescription = displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp).clip(CircleShape),
        )
    } else {
        Surface(
            modifier = Modifier.size(size.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = displayName,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(size.dp / 4),
            )
        }
    }
}

@Composable
fun MetricItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A row of statistic tiles: a big value with its label underneath.
 *
 * The tiles are laid out with `weight(1f)`, which makes them consume *all* the free space —
 * which in turn makes `Arrangement.SpaceEvenly` a no-op that was silently doing nothing. The
 * first and last tile therefore sat hard against the edges of whatever contained them, which
 * is what made the numbers look crammed to the left. The spacing is now explicit: real outer
 * padding, and a real gap between tiles.
 */
@Composable
fun StatRow(
    items: List<Pair<String, String>>,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        // Gaps between tiles, which weight(1f) alone does not provide.
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((label, value) in items) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(value, style = MaterialTheme.typography.titleMedium)
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}