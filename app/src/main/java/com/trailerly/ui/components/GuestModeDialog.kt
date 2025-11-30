package com.trailerly.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trailerly.R

/**
 * Dialog shown to guest users when they try to access premium features.
 * Prompts them to sign in or continue as guest.
 */
@Composable
fun GuestModeDialog(
    onSignInClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Sign In Required",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = "This feature requires a user account. Would you like to sign in or create an account?",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onSignInClick) {
                Text(
                    text = stringResource(R.string.sign_in),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.continue_as_guest),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    )
}
