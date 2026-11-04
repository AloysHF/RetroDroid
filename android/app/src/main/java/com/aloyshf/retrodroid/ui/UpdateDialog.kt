package com.aloyshf.retrodroid.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.aloyshf.retrodroid.R
import com.aloyshf.retrodroid.data.UpdateInfo

@Composable
fun UpdateAvailableDialog(
    info: UpdateInfo,
    currentVersion: String,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available_title)) },
        text = { Text(stringResource(R.string.update_available_body, info.version, currentVersion)) },
        confirmButton = {
            TextButton(
                onClick = {
                    runCatching { uriHandler.openUri(info.pageUrl) }
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.update_view))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_later))
            }
        },
    )
}
