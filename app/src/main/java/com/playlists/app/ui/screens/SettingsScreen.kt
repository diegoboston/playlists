package com.playlists.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.playlists.app.R
import com.playlists.app.ai.OpenAiClient
import com.playlists.app.ui.AppUpdateUiState
import com.playlists.app.ui.PlaylistsViewModel
import com.playlists.app.ui.components.AppUpdateBanner
import com.playlists.app.util.AiCredentialStore
import com.playlists.app.util.AppPrefs
import com.playlists.app.util.AppUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.playlists.app.util.StageManagerStorage
import java.io.File

private sealed interface OpenAiKeyStatus {
    data object Unknown : OpenAiKeyStatus
    data object Testing : OpenAiKeyStatus
    data object Valid : OpenAiKeyStatus
    data class Invalid(val message: String) : OpenAiKeyStatus
}

private const val OPENAI_BILLING_URL =
    "https://platform.openai.com/settings/organization/billing/overview"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: PlaylistsViewModel,
    onBack: () -> Unit,
    onInstallUpdate: (File) -> Unit = {},
) {
    val context = LocalContext.current
    val updateState by viewModel.appUpdateState.collectAsStateWithLifecycle()
    val versionName = remember { AppUpdate.installedVersionName(context) }
    val updateInProgress = updateState is AppUpdateUiState.Checking ||
        updateState is AppUpdateUiState.Downloading
    var codeText by remember {
        mutableStateOf(AppPrefs.getRemoteCode(context).toString())
    }
    var openAiKeyText by remember {
        mutableStateOf(AiCredentialStore.getOpenAiApiKey(context).orEmpty())
    }
    var openAiKeyStatus by remember { mutableStateOf<OpenAiKeyStatus>(OpenAiKeyStatus.Unknown) }
    var librarySizeLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        librarySizeLabel = withContext(Dispatchers.IO) {
            Formatter.formatFileSize(context, StageManagerStorage.librarySizeBytes())
        }
    }

    LaunchedEffect(openAiKeyText) {
        val key = openAiKeyText.trim()
        if (key.isEmpty()) {
            openAiKeyStatus = OpenAiKeyStatus.Unknown
            return@LaunchedEffect
        }
        openAiKeyStatus = OpenAiKeyStatus.Testing
        delay(600)
        val client = OpenAiClient(key)
        openAiKeyStatus = withContext(Dispatchers.IO) {
            runCatching { client.validateApiKey() }
                .fold(
                    onSuccess = { OpenAiKeyStatus.Valid },
                    onFailure = { OpenAiKeyStatus.Invalid(it.message ?: "Failed") },
                )
        }
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.settings_remote_pin),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = stringResource(R.string.settings_remote_pin_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = codeText,
                onValueChange = { codeText = it.filter { ch -> ch.isDigit() }.take(5) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Text(
                text = stringResource(R.string.settings_openai_api_key),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
            OutlinedTextField(
                value = openAiKeyText,
                onValueChange = { openAiKeyText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                placeholder = { Text(stringResource(R.string.settings_openai_api_key_hint)) },
                trailingIcon = {
                    OpenAiKeyStatusIcon(status = openAiKeyStatus)
                },
            )
            when (val status = openAiKeyStatus) {
                is OpenAiKeyStatus.Invalid -> {
                    Text(
                        text = status.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                OpenAiKeyStatus.Valid -> Unit
                OpenAiKeyStatus.Testing -> {
                    Text(
                        text = stringResource(R.string.settings_openai_key_testing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                OpenAiKeyStatus.Unknown -> Unit
            }
            Text(
                text = stringResource(R.string.settings_openai_billing),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                ),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(OPENAI_BILLING_URL)),
                        )
                    },
            )
            Button(
                onClick = {
                    if (!AppPrefs.isValidRemoteCode(codeText)) {
                        Toast.makeText(context, R.string.settings_remote_code_invalid, Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    AppPrefs.setRemoteCode(context, codeText.toInt())
                    AiCredentialStore.setOpenAiApiKey(context, openAiKeyText)
                    Toast.makeText(context, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            ) {
                Text(stringResource(R.string.save))
            }
            Text(
                text = stringResource(R.string.settings_app_version),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 32.dp, bottom = 8.dp),
            )
            Text(
                text = stringResource(R.string.settings_app_version_value, versionName),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            OutlinedButton(
                onClick = { viewModel.startAppUpdateDownload(context) },
                enabled = !updateInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_check_for_updates))
            }
            updateState?.let { state ->
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    AppUpdateBanner(
                        state = state,
                        onDismiss = { viewModel.clearAppUpdateState() },
                        onInstall = onInstallUpdate,
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_storage),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 32.dp, bottom = 8.dp),
            )
            Text(
                text = librarySizeLabel ?: stringResource(R.string.settings_storage_calculating),
                style = MaterialTheme.typography.bodyLarge,
                color = if (librarySizeLabel == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun OpenAiKeyStatusIcon(status: OpenAiKeyStatus) {
    when (status) {
        OpenAiKeyStatus.Testing -> {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
        }
        OpenAiKeyStatus.Valid -> {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = stringResource(R.string.settings_openai_key_valid),
                tint = Color(0xFF2E7D32),
            )
        }
        is OpenAiKeyStatus.Invalid -> {
            Icon(
                Icons.Default.Error,
                contentDescription = stringResource(R.string.settings_openai_key_invalid),
                tint = MaterialTheme.colorScheme.error,
            )
        }
        OpenAiKeyStatus.Unknown -> Unit
    }
}
