package com.playlists.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import com.playlists.app.R
import com.playlists.app.util.AppPrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var portText by remember {
        mutableStateOf(AppPrefs.getRemotePort(context).toString())
    }

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
                    AppPrefs.setRemotePort(context, port)
                    Toast.makeText(context, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}
