package com.fpclient.android.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.MaterialTheme

/**
 * Theme wrapper for FitPub Wear (Iteration 9a).
 *
 * Wear Material is dark-by-default (black background), which suits OLED watch faces and means the
 * only thing worth overriding is the accent colour: FitPub green. The value is the launcher brand
 * green (#1E6F3E, see res/values/colors.xml) brightened to #2E9E52 so green text on the black
 * background clears WCAG AA contrast — the brand original only reaches ~3.3:1 at text sizes.
 */
@Composable
fun FitPubWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = MaterialTheme.colors.copy(
            primary = Color(0xFF2E9E52),
            primaryVariant = Color(0xFF1E6F3E),
        ),
        content = content,
    )
}
