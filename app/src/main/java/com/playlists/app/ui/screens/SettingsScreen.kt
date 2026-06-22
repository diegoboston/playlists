package com.playlists.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.playlists.app.R
import com.playlists.app.ui.AppUpdateUiState
import com.playlists.app.ui.PlaylistsViewModel
import com.playlists.app.util.AppPrefs
import com.playlists.app.util.AppUpdate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: PlaylistsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val updateState by viewModel.appUpdateState.collectAsStateWithLifecycle()
    val versionName = remember { AppUpdate.installedVersionName(context) }
    val updateInProgress = updateState is AppUpdateUiState.Checking ||
        updateState is AppUpdateUiState.Downloading
    var portText by remember {
        mutableStateOf(AppPrefs.getRemotePort(context).toString())
    }
    var pinText by remember {
        mutableStateOf(AppPrefs.getRemotePin(context))
    }
    var pinVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_remote_pin),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = pinText,
                onValueChange = { pinText = it.filter { ch -> ch.isDigit() }.take(4) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (pinVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                placeholder = { Text(AppPrefs.DEFAULT_REMOTE_PIN) },
                trailingIcon = {
                    IconButton(onClick = { pinVisible = !pinVisible }) {
                        Icon(
                            imageVector = if (pinVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = stringResource(
                                if (pinVisible) {
                                    R.string.settings_remote_pin_hide
                                } else {
                                    R.string.settings_remote_pin_show
                                },
                            ),
                        )
                    }
                },
            )
            Text(
                text = stringResource(R.string.settings_remote_pin_hint),
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
            Text(
                text = stringResource(R.string.settings_remote_port),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = portText,
                onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                placeholder = { Text(AppPrefs.DEFAULT_REMOTE_PORT.toString()) },
            )
            Text(
                text = stringResource(R.string.settings_remote_port_hint),
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
            Button(
                onClick = {
                    val port = portText.toIntOrNull()
                    if (port == null || port !in 1024..65535) {
                        Toast.makeText(context, R.string.settings_port_invalid, Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (!AppPrefs.isValidRemotePin(pinText)) {
                        Toast.makeText(context, R.string.settings_pin_invalid, Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    AppPrefs.setRemotePort(context, port)
                    AppPrefs.setRemotePin(context, pinText)
                    Toast.makeText(context, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save))
            }
            Text(
                text = stringResource(R.string.settings_app_version),
                modifier = Modifier.padding(top = 32.dp, bottom = 8.dp),
            )
            Text(
                text = stringResource(R.string.settings_app_version_value, versionName),
                modifier = Modifier.padding(bottom = 16.dp),
            )
            OutlinedButton(
                onClick = { viewModel.startAppUpdateDownload(context) },
                enabled = !updateInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_check_for_updates))
            }
        }
    }
}
