package com.playlists.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.playlists.app.R
import com.playlists.app.ui.PianoKeyboard
import com.playlists.app.ui.PianoVolume
import com.playlists.app.ui.rememberPianoSoundEngine

@Composable
fun PianoDialog(onDismiss: () -> Unit) {
    val engine = rememberPianoSoundEngine()
    LaunchedEffect(engine) {
        engine.setGain(PianoVolume.MAX_GAIN)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.piano_keyboard_label)) },
        text = {
            PianoKeyboard(
                embedded = true,
                engine = engine,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}
