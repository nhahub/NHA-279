package com.trailerly.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trailerly.R

@Composable
fun ApiKeyErrorDialog(
    errorMessage: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.api_key_error_title))
        },
        text = {
            Text(text = errorMessage + "\n\n" + stringResource(R.string.api_key_error_instructions))
        },
        confirmButton = {
            TextButton(onClick = {
                onRetry()
                onDismiss()
            }) {
                Text(text = stringResource(R.string.retry))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.close))
            }
        }
    )
}
