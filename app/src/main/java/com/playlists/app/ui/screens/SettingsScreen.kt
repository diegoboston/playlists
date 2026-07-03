package com.playlists.app.ui.screens

import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.playlists.app.R
import com.playlists.app.ai.OpenAiClient
import com.playlists.app.remote.TunnelRedirectClient
import com.playlists.app.ui.AppUpdateUiState
import com.playlists.app.ui.PlaylistsViewModel
import com.playlists.app.util.AiCredentialStore
import com.playlists.app.util.AppIcon
import com.playlists.app.util.AppIconManager
import com.playlists.app.util.AppPrefs
import com.playlists.app.util.AppUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.playlists.app.util.StageManagerStorage

private sealed interface FieldValidationStatus {
    data object Unknown : FieldValidationStatus
    data object Testing : FieldValidationStatus
    data object Valid : FieldValidationStatus
    data class Invalid(val message: String) : FieldValidationStatus
}

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
    var codeText by remember {
        mutableStateOf(AppPrefs.getRemoteCode(context).toString())
    }
    var workersSubdomainText by remember {
        mutableStateOf(AppPrefs.getTunnelRedirectSubdomain(context).orEmpty())
    }
    var writeSecretText by remember {
        mutableStateOf(AppPrefs.getTunnelRedirectSecret(context).orEmpty())
    }
    var writeSecretVisible by remember { mutableStateOf(false) }
    var writeSecretStatus by remember { mutableStateOf<FieldValidationStatus>(FieldValidationStatus.Unknown) }
    var openAiKeyText by remember {
        mutableStateOf(AiCredentialStore.getOpenAiApiKey(context).orEmpty())
    }
    var openAiKeyVisible by remember { mutableStateOf(false) }
    var openAiKeyStatus by remember { mutableStateOf<FieldValidationStatus>(FieldValidationStatus.Unknown) }
    var librarySizeLabel by remember { mutableStateOf<String?>(null) }
    var selectedAppIcon by remember { mutableStateOf(AppIconManager.getSelected(context)) }

    LaunchedEffect(Unit) {
        librarySizeLabel = withContext(Dispatchers.IO) {
            Formatter.formatFileSize(context, StageManagerStorage.librarySizeBytes())
        }
    }

    LaunchedEffect(openAiKeyText) {
        val key = openAiKeyText.trim()
        if (key.isEmpty()) {
            openAiKeyStatus = FieldValidationStatus.Unknown
            return@LaunchedEffect
        }
        openAiKeyStatus = FieldValidationStatus.Testing
        delay(600)
        if (openAiKeyText.trim() != key) return@LaunchedEffect
        val status = withContext(Dispatchers.IO) {
            runCatching { OpenAiClient(key).validateApiKey() }
                .fold(
                    onSuccess = { FieldValidationStatus.Valid },
                    onFailure = { FieldValidationStatus.Invalid(it.message ?: "Failed") },
                )
        }
        if (openAiKeyText.trim() == key) {
            openAiKeyStatus = status
        }
    }

    LaunchedEffect(workersSubdomainText, writeSecretText) {
        val subdomain = workersSubdomainText.trim()
        val secret = writeSecretText.trim()
        if (secret.isEmpty() || !AppPrefs.isValidWorkersSubdomain(subdomain)) {
            writeSecretStatus = FieldValidationStatus.Unknown
            return@LaunchedEffect
        }
        writeSecretStatus = FieldValidationStatus.Testing
        delay(600)
        if (workersSubdomainText.trim() != subdomain || writeSecretText.trim() != secret) {
            return@LaunchedEffect
        }
        val workerBase = TunnelRedirectClient.buildWorkerBaseUrl(subdomain)
        val status = withContext(Dispatchers.IO) {
            TunnelRedirectClient.validateWriteSecret(workerBase, secret)
                .fold(
                    onSuccess = { FieldValidationStatus.Valid },
                    onFailure = { FieldValidationStatus.Invalid(it.message ?: "Failed") },
                )
        }
        if (workersSubdomainText.trim() == subdomain && writeSecretText.trim() == secret) {
            writeSecretStatus = status
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
                text = stringResource(R.string.settings_stable_redirect),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
            )
            OutlinedTextField(
                value = workersSubdomainText,
                onValueChange = { workersSubdomainText = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.settings_stable_subdomain)) },
                placeholder = { Text(stringResource(R.string.settings_stable_subdomain_hint)) },
                supportingText = { Text(stringResource(R.string.settings_stable_subdomain_supporting)) },
            )
            OutlinedTextField(
                value = writeSecretText,
                onValueChange = { writeSecretText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                singleLine = true,
                visualTransformation = if (writeSecretVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                label = { Text(stringResource(R.string.settings_stable_secret)) },
                placeholder = { Text(stringResource(R.string.settings_stable_secret_hint)) },
                trailingIcon = {
                    SensitiveFieldTrailing(
                        visible = writeSecretVisible,
                        onToggleVisibility = { writeSecretVisible = !writeSecretVisible },
                        status = writeSecretStatus,
                        validContentDescription = stringResource(R.string.settings_stable_secret_valid),
                        invalidContentDescription = stringResource(R.string.settings_stable_secret_invalid),
                    )
                },
            )
            when (val status = writeSecretStatus) {
                is FieldValidationStatus.Invalid -> {
                    Text(
                        text = status.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                FieldValidationStatus.Valid -> Unit
                FieldValidationStatus.Testing -> {
                    Text(
                        text = stringResource(R.string.settings_stable_secret_testing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                FieldValidationStatus.Unknown -> Unit
            }
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
                visualTransformation = if (openAiKeyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                placeholder = { Text(stringResource(R.string.settings_openai_api_key_hint)) },
                trailingIcon = {
                    SensitiveFieldTrailing(
                        visible = openAiKeyVisible,
                        onToggleVisibility = { openAiKeyVisible = !openAiKeyVisible },
                        status = openAiKeyStatus,
                        validContentDescription = stringResource(R.string.settings_openai_key_valid),
                        invalidContentDescription = stringResource(R.string.settings_openai_key_invalid),
                    )
                },
            )
            when (val status = openAiKeyStatus) {
                is FieldValidationStatus.Invalid -> {
                    Text(
                        text = status.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                FieldValidationStatus.Valid -> Unit
                FieldValidationStatus.Testing -> {
                    Text(
                        text = stringResource(R.string.settings_openai_key_testing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                FieldValidationStatus.Unknown -> Unit
            }
            Text(
                text = stringResource(R.string.settings_app_icon),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
            AppIconPicker(
                selected = selectedAppIcon,
                onSelect = { icon ->
                    if (icon == selectedAppIcon) return@AppIconPicker
                    AppIconManager.setSelected(context, icon)
                    selectedAppIcon = icon
                    Toast.makeText(context, R.string.settings_app_icon_changed, Toast.LENGTH_SHORT).show()
                },
            )
            Button(
                onClick = {
                    if (!AppPrefs.isValidRemoteCode(codeText)) {
                        Toast.makeText(context, R.string.settings_remote_code_invalid, Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (!AppPrefs.isValidWorkersSubdomain(workersSubdomainText)) {
                        Toast.makeText(context, R.string.settings_stable_subdomain_invalid, Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    AppPrefs.setRemoteCode(context, codeText.toInt())
                    AppPrefs.setTunnelRedirect(
                        context,
                        subdomain = workersSubdomainText,
                        secret = writeSecretText,
                    )
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
            SettingsStatusCard(
                versionName = versionName,
                librarySizeLabel = librarySizeLabel,
                updateState = updateState,
                updateInProgress = updateInProgress,
                onCheckForUpdates = { viewModel.startAppUpdateDownload(context) },
                modifier = Modifier.padding(top = 32.dp),
            )
        }
    }
}

@Composable
private fun SettingsStatusCard(
    versionName: String,
    librarySizeLabel: String?,
    updateState: AppUpdateUiState?,
    updateInProgress: Boolean,
    onCheckForUpdates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SettingsStatusRow(
                label = stringResource(R.string.settings_app_version),
                value = stringResource(R.string.settings_app_version_value, versionName),
            )
            SettingsStatusRow(
                label = stringResource(R.string.settings_updates),
                modifier = Modifier.padding(top = 12.dp),
                trailing = {
                    Column(horizontalAlignment = Alignment.End) {
                        OutlinedButton(
                            onClick = onCheckForUpdates,
                            enabled = !updateInProgress,
                        ) {
                            if (updateInProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(stringResource(R.string.settings_check_for_updates))
                            }
                        }
                        updateStatusDetail(updateState)?.let { detail ->
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                },
            )
            SettingsStatusRow(
                label = stringResource(R.string.settings_storage),
                value = librarySizeLabel ?: stringResource(R.string.settings_storage_calculating),
                valueColor = if (librarySizeLabel == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun updateStatusDetail(updateState: AppUpdateUiState?): String? =
    when (updateState) {
        is AppUpdateUiState.Checking -> stringResource(R.string.update_app_checking)
        is AppUpdateUiState.Downloading -> {
            val progress = updateState.progress
            if (progress != null) {
                stringResource(R.string.update_app_downloading_percent, (progress * 100).toInt())
            } else {
                stringResource(R.string.update_app_downloading)
            }
        }
        is AppUpdateUiState.UpToDate -> stringResource(
            R.string.update_app_up_to_date,
            updateState.versionName,
        )
        is AppUpdateUiState.ReadyToInstall -> stringResource(
            R.string.update_app_ready,
            updateState.versionName,
        )
        is AppUpdateUiState.Failed -> stringResource(
            R.string.update_app_failed,
            updateState.message,
        )
        null -> null
    }

@Composable
private fun SettingsStatusRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(end = 12.dp),
        )
        when {
            trailing != null -> {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    trailing()
                }
            }
            value != null -> {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = valueColor,
                )
            }
        }
    }
}

@Composable
private fun AppIconPicker(
    selected: AppIcon,
    onSelect: (AppIcon) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        AppIconOption(
            label = stringResource(R.string.settings_app_icon_default),
            previewRes = R.drawable.ic_launcher_foreground,
            selected = selected == AppIcon.Default,
            onClick = { onSelect(AppIcon.Default) },
        )
        AppIconOption(
            label = stringResource(R.string.settings_app_icon_alt),
            previewRes = R.drawable.ic_launcher_alt_foreground,
            selected = selected == AppIcon.Alt,
            onClick = { onSelect(AppIcon.Alt) },
        )
    }
}

@Composable
private fun AppIconOption(
    label: String,
    previewRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = borderColor,
                    shape = shape,
                )
                .clip(shape)
                .background(Color.White),
        ) {
            Image(
                painter = painterResource(previewRes),
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SensitiveFieldTrailing(
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    status: FieldValidationStatus,
    validContentDescription: String,
    invalidContentDescription: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FieldValidationStatusIcon(
            status = status,
            validContentDescription = validContentDescription,
            invalidContentDescription = invalidContentDescription,
        )
        IconButton(onClick = onToggleVisibility) {
            Icon(
                imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = stringResource(
                    if (visible) R.string.settings_hide_secret else R.string.settings_show_secret,
                ),
            )
        }
    }
}

@Composable
private fun FieldValidationStatusIcon(
    status: FieldValidationStatus,
    validContentDescription: String,
    invalidContentDescription: String,
) {
    when (status) {
        FieldValidationStatus.Testing -> {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
        }
        FieldValidationStatus.Valid -> {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = validContentDescription,
                tint = Color(0xFF2E7D32),
            )
        }
        is FieldValidationStatus.Invalid -> {
            Icon(
                Icons.Default.Error,
                contentDescription = invalidContentDescription,
                tint = MaterialTheme.colorScheme.error,
            )
        }
        FieldValidationStatus.Unknown -> Unit
    }
}
