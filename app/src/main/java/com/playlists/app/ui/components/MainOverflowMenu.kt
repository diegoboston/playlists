package com.playlists.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
    showTakeImage: Boolean,
    showAiSearch: Boolean,
    showWebServer: Boolean,
    webServerActive: Boolean,
    onTakeImage: () -> Unit,
    onImportFromGallery: () -> Unit,
    onImportFromStorage: () -> Unit,
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
            if (showTakeImage) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_import_from_camera)) },
                    leadingIcon = {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        onTakeImage()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_import_from_gallery)) },
                leadingIcon = {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onImportFromGallery()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_import_from_storage)) },
                leadingIcon = {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onImportFromStorage()
                },
            )
            HorizontalDivider()
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
