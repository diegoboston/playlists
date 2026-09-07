package com.playlists.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.playlists.app.R

@Composable
fun MainOverflowMenu(
    showAiSearch: Boolean,
    showWebServer: Boolean,
    webServerActive: Boolean,
    onScanImage: () -> Unit,
    onImportFile: () -> Unit,
    onAiSearch: () -> Unit,
    onWebServer: () -> Unit,
    onPiano: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Box {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = stringResource(R.string.main_menu),
                )
                if (webServerActive) {
                    RemotePlayPulseDot(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = 2.dp),
                    )
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_scan_image)) },
                leadingIcon = {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onScanImage()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_import_file)) },
                leadingIcon = {
                    Icon(Icons.Default.Add, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onImportFile()
                },
            )
            if (showAiSearch) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_ai_song_search)) },
                    leadingIcon = {
                        Icon(Icons.Default.Bolt, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        onAiSearch()
                    },
                )
            }
            if (showWebServer) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_web_server)) },
                    leadingIcon = {
                        RemotePlayWifiIcon(active = webServerActive, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        onWebServer()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.piano_keyboard_label)) },
                leadingIcon = {
                    Icon(Icons.Default.Piano, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onPiano()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings)) },
                leadingIcon = {
                    Icon(Icons.Default.Settings, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onSettings()
                },
            )
        }
    }
}
