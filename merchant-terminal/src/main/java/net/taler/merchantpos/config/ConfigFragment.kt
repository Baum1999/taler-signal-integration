/*
 * This file is part of GNU Taler
 * (C) 2020 Taler Systems S.A.
 *
 * GNU Taler is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3, or (at your option) any later version.
 *
 * GNU Taler is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * GNU Taler; see the file COPYING.  If not, see <http://www.gnu.org/licenses/>
 */

package net.taler.merchantpos.config

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.Image
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.taler.common.TokenDuration
import net.taler.lib.android.ChallengeCancelledException
import net.taler.lib.android.handleChallengeResponse
import net.taler.merchantpos.MainActivity
import net.taler.merchantpos.MainViewModel
import net.taler.merchantpos.R
import net.taler.merchantpos.compose.PosTheme
import net.taler.merchantpos.showPosError
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.remember
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode.Companion.Unauthorized

private enum class ConfigMode { Manual, Qr }

class ConfigFragment : Fragment() {

    private val model: MainViewModel by activityViewModels()
    private val configManager by lazy { model.configManager }

    private var awaitingConfigUpdate = false
    private var mode by mutableStateOf(ConfigMode.Manual)
    private var merchantUrlText by mutableStateOf("")
    private var usernameText by mutableStateOf("")
    private var tokenText by mutableStateOf("")
    private var saveToken by mutableStateOf(true)
    private var isSubmitting by mutableStateOf(false)
    private var isQrLoading by mutableStateOf(false)
    private var previewView: PreviewView? = null

    private val cameraExecutor by lazy {
        ContextCompat.getMainExecutor(requireContext())
    }

    private val qrReader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.CHARACTER_SET to "UTF-8",
            ),
        )
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?,
    ) = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        initializeState(savedInstanceState == null)
        setContent {
            ConfigScreen(
                mode = mode,
                merchantUrl = merchantUrlText,
                username = usernameText,
                token = tokenText,
                saveToken = saveToken,
                isSubmitting = isSubmitting,
                isQrLoading = isQrLoading,
                onModeChanged = {
                    mode = it
                    if (it == ConfigMode.Qr) requestCameraIfNeeded() else stopCamera()
                },
                onMerchantUrlChanged = { merchantUrlText = it },
                onUsernameChanged = { usernameText = it },
                onTokenChanged = { tokenText = it },
                onSaveTokenChanged = { saveToken = it },
                previewContent = {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            PreviewView(context).also {
                                it.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                it.scaleType = PreviewView.ScaleType.FIT_CENTER
                                previewView = it
                                if (mode == ConfigMode.Qr) {
                                    requestCameraIfNeeded()
                                }
                            }
                        },
                        update = { view ->
                            view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            view.scaleType = PreviewView.ScaleType.FIT_CENTER
                            previewView = view
                            if (mode == ConfigMode.Qr) {
                                requestCameraIfNeeded()
                            }
                        },
                    )
                },
                onConnect = ::submitManualConfig,
            )
        }
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        configManager.configUpdateResult.observe(viewLifecycleOwner) { result ->
            onConfigUpdate(result)
        }
    }

    override fun onResume() {
        super.onResume()
        if (mode == ConfigMode.Qr) {
            requestCameraIfNeeded()
        }
    }

    override fun onDestroyView() {
        stopCamera()
        previewView = null
        super.onDestroyView()
    }

    private fun initializeState(isInitialization: Boolean) {
        val cfg = configManager.config
        if (isInitialization) {
            merchantUrlText = NEW_CONFIG_URL_DEMO
            saveToken = cfg.savePassword()
            if (cfg is Config.New && cfg.merchantUrl.isNotBlank()) {
                merchantUrlText = cfg.merchantUrl
                sanitizeMerchantUrlAndUpdateFields()
            }
        }
    }

    private fun submitManualConfig() {
        lifecycleScope.launch {
            isSubmitting = true
            val baseUrl = sanitizeMerchantUrlAndUpdateFields()
            val username = usernameText.trim()
            val initialSecret = tokenText.trim()
            val duration = TokenDuration.Forever

            val limitedToken = try {
                fetchLimitedAccessTokenWithMfa(baseUrl, username, initialSecret, duration)
            } catch (_: ChallengeCancelledException) {
                isSubmitting = false
                return@launch
            } catch (e: Exception) {
                isSubmitting = false
                Log.e("ConfigFragment", "Error fetching limited token: ${e.message}")
                val errorRes = if (e is ClientRequestException && e.response.status == Unauthorized) {
                    R.string.config_auth_error
                } else {
                    R.string.config_error_network
                }
                requireActivity().showPosError(errorRes)
                return@launch
            }

            val configUrl = "$baseUrl/instances/$username"
            val config = Config.New(
                merchantUrl = configUrl,
                accessToken = limitedToken,
                savePassword = saveToken,
            )
            awaitingConfigUpdate = true
            configManager.fetchConfig(config, true)
        }
    }

    private fun sanitizeMerchantUrlAndUpdateFields(): String {
        val rawInput = merchantUrlText.trim()
        if (rawInput.isEmpty()) return ""

        val normalizedInput = if (rawInput.startsWith("http://") || rawInput.startsWith("https://")) {
            rawInput
        } else {
            "https://$rawInput"
        }

        val uri = normalizedInput.toUri()
        val host = uri.host.orEmpty()
        val port = if (uri.port != -1) ":${uri.port}" else ""
        val baseHost = "$host$port"
        val segments = uri.pathSegments
        if (segments.size >= 2 && segments[0].equals("instances", true)) {
            usernameText = segments[1]
        }
        merchantUrlText = baseHost
        return if (baseHost.isBlank()) "" else "https://$baseHost"
    }

    private fun onConfigUpdate(result: ConfigUpdateResult?) {
        if (!awaitingConfigUpdate) return
        when (result) {
            null -> Unit
            is ConfigUpdateResult.Error -> {
                awaitingConfigUpdate = false
                isSubmitting = false
                requireActivity().showPosError(result.msg)
            }

            is ConfigUpdateResult.Success -> {
                awaitingConfigUpdate = false
                isSubmitting = false
                Toast.makeText(
                    requireContext(),
                    getString(R.string.config_changed, result.currency),
                    Toast.LENGTH_LONG,
                ).show()
                (requireActivity() as MainActivity).navigateToInitialOrderScreen()
            }
        }
    }

    private suspend fun fetchLimitedAccessTokenWithMfa(
        baseUrl: String,
        username: String,
        initialSecret: String,
        duration: TokenDuration,
    ): String {
        var challengeIds: List<String> = emptyList()
        while (true) {
            try {
                return withContext(Dispatchers.IO) {
                    configManager.fetchLimitedAccessToken(
                        baseUrl,
                        username,
                        initialSecret,
                        duration,
                        challengeIds,
                    )
                }
            } catch (e: ChallengeRequiredException) {
                val solvedIds = handleChallengeResponse(
                    e.challengeResponse.challenges,
                    e.challengeResponse.combiAnd,
                    onRequestChallenge = { challengeId ->
                        withContext(Dispatchers.IO) {
                            configManager.requestChallenge(baseUrl, username, challengeId)
                        }
                    },
                    onConfirmChallenge = { challengeId, tan ->
                        withContext(Dispatchers.IO) {
                            configManager.confirmChallenge(baseUrl, username, challengeId, tan)
                        }
                    },
                )
                if (solvedIds.isEmpty()) throw ChallengeCancelledException()
                challengeIds = solvedIds
            }
        }
    }

    private val requestCameraPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else Toast.makeText(
                requireContext(),
                R.string.config_fragment_camera_needed_text,
                Toast.LENGTH_SHORT,
            ).show()
        }

    private fun requestCameraIfNeeded() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestCameraPerm.launch(Manifest.permission.CAMERA)
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        val previewTarget = previewView ?: return
        val providerFuture = ProcessCameraProvider.getInstance(requireContext())
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewTarget.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { useCase ->
                    useCase.setAnalyzer(cameraExecutor) { proxy ->
                        val mediaImage = proxy.image
                        if (mediaImage != null) {
                            val nv21 = yuv420888ToNv21(mediaImage)
                            val width = mediaImage.width
                            val height = mediaImage.height
                            val source = PlanarYUVLuminanceSource(
                                nv21,
                                width,
                                height,
                                0,
                                0,
                                width,
                                height,
                                false,
                            )
                            val rotated = when (proxy.imageInfo.rotationDegrees) {
                                90, 270 -> source.rotateCounterClockwise()
                                else -> source
                            }
                            val bitmap = BinaryBitmap(HybridBinarizer(rotated))
                            try {
                                val result = qrReader.decodeWithState(bitmap)
                                onQrDecoded(result.text)
                            } catch (_: NotFoundException) {
                            } finally {
                                proxy.close()
                            }
                        } else {
                            proxy.close()
                        }
                    }
                }
            provider.unbindAll()
            provider.bindToLifecycle(
                viewLifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
        }, cameraExecutor)
    }

    private fun yuv420888ToNv21(image: Image): ByteArray {
        val yPlane = image.planes[0].buffer
        val uPlane = image.planes[1].buffer
        val vPlane = image.planes[2].buffer
        val ySize = yPlane.remaining()
        val uSize = uPlane.remaining()
        val vSize = vPlane.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yPlane.get(nv21, 0, ySize)
        vPlane.get(nv21, ySize, vSize)
        uPlane.get(nv21, ySize + vSize, uSize)
        return nv21
    }

    private fun onQrDecoded(raw: String) {
        if (!raw.startsWith("taler-pos://")) return
        stopCamera()
        isQrLoading = true
        val intent = Intent(Intent.ACTION_VIEW, raw.toUri())
        (requireActivity() as MainActivity).handleSetupIntent(intent)
    }

    private fun stopCamera() {
        try {
            ProcessCameraProvider.getInstance(requireContext()).get().unbindAll()
        } catch (_: Exception) {
        }
    }
}

@Composable
private fun ConfigScreen(
    mode: ConfigMode,
    merchantUrl: String,
    username: String,
    token: String,
    saveToken: Boolean,
    isSubmitting: Boolean,
    isQrLoading: Boolean,
    onModeChanged: (ConfigMode) -> Unit,
    onMerchantUrlChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onTokenChanged: (String) -> Unit,
    onSaveTokenChanged: (Boolean) -> Unit,
    previewContent: @Composable () -> Unit,
    onConnect: () -> Unit,
) {
    PosTheme {
        val isTabletLayout = LocalConfiguration.current.smallestScreenWidthDp >= 720
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        val formListState = rememberLazyListState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RowButtons(
                    mode = mode,
                    onManual = { onModeChanged(ConfigMode.Manual) },
                    onQr = { onModeChanged(ConfigMode.Qr) },
                )
                if (mode == ConfigMode.Manual) {
                    ManualConfigScreen(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        merchantUrl = merchantUrl,
                        username = username,
                        token = token,
                        saveToken = saveToken,
                        isSubmitting = isSubmitting,
                        formListState = formListState,
                        onMerchantUrlChanged = onMerchantUrlChanged,
                        onUsernameChanged = onUsernameChanged,
                        onTokenChanged = onTokenChanged,
                        onSaveTokenChanged = onSaveTokenChanged,
                        onConnect = onConnect,
                        focusManager = focusManager,
                        keyboardController = keyboardController,
                    )
                } else {
                    if (isTabletLayout) {
                        TabletQrConfigScreen(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            isQrLoading = isQrLoading,
                            previewContent = previewContent,
                        )
                    } else {
                        PhoneQrConfigScreen(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            isQrLoading = isQrLoading,
                            previewContent = previewContent,
                        )
                    }
                }
            }

            if (isSubmitting) {
                Dialog(onDismissRequest = {}) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = 6.dp,
                    ) {
                        Box(
                            modifier = Modifier.padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualConfigScreen(
    modifier: Modifier,
    merchantUrl: String,
    username: String,
    token: String,
    saveToken: Boolean,
    isSubmitting: Boolean,
    formListState: androidx.compose.foundation.lazy.LazyListState,
    onMerchantUrlChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onTokenChanged: (String) -> Unit,
    onSaveTokenChanged: (Boolean) -> Unit,
    onConnect: () -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?,
) {
    LazyColumn(
        modifier = modifier,
        state = formListState,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            OutlinedTextField(
                value = merchantUrl,
                onValueChange = onMerchantUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.config_merchant_url)) },
                prefix = { Text("https://") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
            )
        }
        item {
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.config_username)) },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
            )
        }
        item {
            var passwordVisible by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = token,
                onValueChange = onTokenChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.config_password)) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = null,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    },
                ),
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = saveToken,
                        onCheckedChange = { onSaveTokenChanged(it) },
                    )
                    Text(stringResource(R.string.config_save_password))
                }
                Button(
                    onClick = onConnect,
                    enabled = !isSubmitting,
                ) {
                    Text(stringResource(R.string.config_ok))
                }
            }
        }
    }
}

@Composable
private fun TabletQrConfigScreen(
    modifier: Modifier,
    isQrLoading: Boolean,
    previewContent: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val previewSize = minOf(maxWidth * 0.7f, maxHeight * 0.7f)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(previewSize)
                    .clipToBounds(),
            ) {
                previewContent()
            }
            Text(stringResource(R.string.scan_qr_hint))
            if (isQrLoading) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun PhoneQrConfigScreen(
    modifier: Modifier,
    isQrLoading: Boolean,
    previewContent: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier,
    ) {
        val previewSize = minOf(maxWidth * 0.8f, maxHeight * 0.6f)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(previewSize)
                    .clipToBounds(),
            ) {
                previewContent()
            }
            Text(stringResource(R.string.scan_qr_hint))
            if (isQrLoading) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun RowButtons(
    mode: ConfigMode,
    onManual: () -> Unit,
    onQr: () -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth(),
    ) {
        SegmentedButton(
            selected = mode == ConfigMode.Manual,
            onClick = onManual,
            icon = {},
            shape = SegmentedButtonDefaults.itemShape(
                index = 0,
                count = 2,
            ),
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.config_manual_label))
        }
        SegmentedButton(
            selected = mode == ConfigMode.Qr,
            onClick = onQr,
            icon = {},
            shape = SegmentedButtonDefaults.itemShape(
                index = 1,
                count = 2,
            ),
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.config_qr_label))
        }
    }
}
