package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DateSectionHeader(
    title: String,
    subtitle: String? = null,
    photoCount: Int? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                letterSpacing = (-0.3).sp
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        if (photoCount != null && photoCount > 0) {
            Text(
                text = "$photoCount ${if (photoCount == 1) "photo" else "photos"}",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
        }
    }
}
