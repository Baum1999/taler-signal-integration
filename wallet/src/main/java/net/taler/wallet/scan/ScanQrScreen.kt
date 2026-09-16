/*
 * This file is part of GNU Taler
 * (C) 2026 Taler Systems S.A.
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

package net.taler.wallet.scan

import android.Manifest
import android.content.ClipboardManager
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import net.taler.lib.android.qr.QrCameraAnalyzer
import net.taler.wallet.R
import net.taler.wallet.WalletDestination
import net.taler.wallet.NavigateCallback
import net.taler.wallet.compose.GlobalScaffold
import net.taler.wallet.link.MultiUriAggregate
import net.taler.wallet.link.OwnUriTracker
import net.taler.wallet.link.ShareSummary
import net.taler.wallet.link.TalerUriExtractor
import net.taler.wallet.link.TalerUriKind
import net.taler.wallet.link.TalerUriParser
import net.taler.wallet.link.buildAggregate
import net.taler.wallet.main.MainViewModel

enum class ScanTab { SCAN_QR, ENTER_LINK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanQrScreen(
    model: MainViewModel,
    initialTab: ScanTab,
    onNavigate: NavigateCallback,
    onNavigateBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val pagerState = rememberPagerState(
        initialPage = initialTab.ordinal,
        pageCount = { ScanTab.entries.size },
    )

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == ScanTab.SCAN_QR.ordinal) {
            keyboardController?.hide()
        }
    }

    GlobalScaffold(
        model = model,
        title = { Text(stringResource(R.string.scan_qr_title)) },
        onNavigateBack = onNavigateBack,
        tabs = {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == ScanTab.SCAN_QR.ordinal,
                    onClick = { scope.launch { pagerState.animateScrollToPage(ScanTab.SCAN_QR.ordinal) } },
                    text = { Text(stringResource(R.string.scan_qr_tab)) },
                )
                Tab(
                    selected = pagerState.currentPage == ScanTab.ENTER_LINK.ordinal,
                    onClick = { scope.launch { pagerState.animateScrollToPage(ScanTab.ENTER_LINK.ordinal) } },
                    text = { Text(stringResource(R.string.enter_link_tab)) },
                )
            }
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) { page ->
            when (ScanTab.entries[page]) {
                ScanTab.SCAN_QR -> QrScannerTab(
                    onQrScanned = { uri ->
                        onNavigate(WalletDestination.HandleUri(uri), true)
                    },
                )
                ScanTab.ENTER_LINK -> EnterLinkTab(
                    model = model,
                    onUriSubmitted = { uri ->
                        onNavigate(WalletDestination.HandleUri(uri), true)
                    },
                )
            }
        }
    }
}

@Composable
private fun QrScannerTab(
    onQrScanned: (String) -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var flashEnabled by remember { mutableStateOf(false) }
    var zoomLevel by remember { mutableFloatStateOf(0f) }
    var scannedOnce by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    QrCameraAnalyzer.decodeQrFromBitmap(bitmap)?.let { result ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onQrScanned(result)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            var previewView by remember { mutableStateOf<PreviewView?>(null) }
            var analyzer by remember { mutableStateOf<QrCameraAnalyzer?>(null) }

            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    analyzer?.focusAtPoint(offset.x, offset.y)
                                }
                            },
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                previewView = this
                            }
                        },
                    )

                    ViewfinderOverlay(
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                    tonalElevation = 3.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 24.dp)
                            .padding(top = 20.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                FilledTonalIconButton(
                                    onClick = {
                                        flashEnabled = !flashEnabled
                                        analyzer?.enableTorch(flashEnabled)
                                    },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = if (flashEnabled)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = if (flashEnabled)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                ) {
                                    Icon(
                                        imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                        contentDescription = stringResource(
                                            if (flashEnabled) R.string.flash_off else R.string.flash_on
                                        ),
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = stringResource(if (flashEnabled) R.string.flash_off else R.string.flash_on),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                FilledTonalIconButton(
                                    onClick = { galleryLauncher.launch("image/*") },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = stringResource(R.string.scan_from_gallery),
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.scan_from_gallery),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = "%.1f×".format(1f + zoomLevel * 9f),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomOut,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Slider(
                                    value = zoomLevel,
                                    onValueChange = { value ->
                                        zoomLevel = value
                                        analyzer?.setLinearZoom(value)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp),
                                )
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            DisposableEffect(previewView) {
                val pv = previewView
                if (pv != null) {
                    val qa = QrCameraAnalyzer(
                        context = context,
                        lifecycleOwner = lifecycleOwner,
                        previewView = pv,
                        onQrDetected = { result ->
                            if (!scannedOnce) {
                                scannedOnce = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onQrScanned(result)
                            }
                        },
                        onCameraReady = { initialLinearZoom ->
                            zoomLevel = initialLinearZoom
                        },
                    )
                    analyzer = qa
                    qa.startCamera()
                }
                onDispose {
                    analyzer?.stopCamera()
                    analyzer = null
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.camera_permission_required),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.grant_permission))
                }
            }
        }
    }
}

@Composable
private fun ViewfinderOverlay(modifier: Modifier = Modifier) {
    val cornerColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scanAreaSize = size.minDimension * 0.7f
            val left = (size.width - scanAreaSize) / 2
            val top = (size.height - scanAreaSize) / 2
            val scanRect = Rect(left, top, left + scanAreaSize, top + scanAreaSize)

            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                size = size,
            )

            val clearPath = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        scanRect,
                        CornerRadius(16.dp.toPx()),
                    )
                )
            }
            drawPath(
                path = clearPath,
                color = Color.Transparent,
                blendMode = BlendMode.Clear,
            )

            val cornerRadius = 16.dp.toPx()
            val lineExtension = 24.dp.toPx()
            val strokeWidth = 5.dp.toPx()
            val strokeStyle = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            drawArc(
                color = cornerColor,
                startAngle = 180f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(scanRect.left, scanRect.top),
                size = Size(cornerRadius * 2, cornerRadius * 2),
                style = strokeStyle
            )
            drawLine(cornerColor,
                Offset(scanRect.left, scanRect.top + cornerRadius),
                Offset(scanRect.left, scanRect.top + cornerRadius + lineExtension), strokeWidth)
            drawLine(cornerColor,
                Offset(scanRect.left + cornerRadius, scanRect.top),
                Offset(scanRect.left + cornerRadius + lineExtension, scanRect.top), strokeWidth)

            drawArc(
                color = cornerColor,
                startAngle = 270f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(scanRect.right - cornerRadius * 2, scanRect.top),
                size = Size(cornerRadius * 2, cornerRadius * 2),
                style = strokeStyle
            )
            drawLine(cornerColor,
                Offset(scanRect.right, scanRect.top + cornerRadius),
                Offset(scanRect.right, scanRect.top + cornerRadius + lineExtension), strokeWidth)
            drawLine(cornerColor,
                Offset(scanRect.right - cornerRadius, scanRect.top),
                Offset(scanRect.right - cornerRadius - lineExtension, scanRect.top), strokeWidth)

            drawArc(
                color = cornerColor,
                startAngle = 90f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(scanRect.left, scanRect.bottom - cornerRadius * 2),
                size = Size(cornerRadius * 2, cornerRadius * 2),
                style = strokeStyle
            )
            drawLine(cornerColor,
                Offset(scanRect.left, scanRect.bottom - cornerRadius),
                Offset(scanRect.left, scanRect.bottom - cornerRadius - lineExtension), strokeWidth)
            drawLine(cornerColor,
                Offset(scanRect.left + cornerRadius, scanRect.bottom),
                Offset(scanRect.left + cornerRadius + lineExtension, scanRect.bottom), strokeWidth)

            drawArc(
                color = cornerColor,
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(scanRect.right - cornerRadius * 2, scanRect.bottom - cornerRadius * 2),
                size = Size(cornerRadius * 2, cornerRadius * 2),
                style = strokeStyle
            )
            drawLine(cornerColor,
                Offset(scanRect.right, scanRect.bottom - cornerRadius),
                Offset(scanRect.right, scanRect.bottom - cornerRadius - lineExtension), strokeWidth)
            drawLine(cornerColor,
                Offset(scanRect.right - cornerRadius, scanRect.bottom),
                Offset(scanRect.right - cornerRadius - lineExtension, scanRect.bottom), strokeWidth)
        }
    }
}

@Composable
private fun EnterLinkTab(
    model: MainViewModel,
    onUriSubmitted: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val invalidUriError = stringResource(R.string.uri_invalid)
    val focusRequester = remember { FocusRequester() }
    var uriText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var aggregate by remember { mutableStateOf<MultiUriAggregate?>(null) }
    val clipboard = context.getSystemService<ClipboardManager>()

    // previewer/ownUriChecker leben nur fuer die Lebensdauer dieses Screens -
    // die Sammelkarte (bei mehreren gefundenen URIs) braucht keinen
    // persistenten Zustand darueber hinaus, siehe MultiUriSummary.kt.
    val previewer = remember { model.newPaymentPreviewer() }
    val ownUriChecker = remember { OwnUriTracker(context) }

    // Erkennt auch eine Taler-URI, die nicht allein im Feld steht - z.B. mit
    // Begleittext davor ODER DAHINTER ("Zahlung: taler://..." bzw. der
    // Signal-Fork-Transkript-Kopie "taler://...\n\n<Erklaerungstext>") oder
    // als JSON-Wrapper ({"uri": "taler://..."}), siehe TalerUriExtractor.
    val isValidTalerUri = { uri: String -> TalerUriExtractor.extractAll(uri).isNotEmpty() }

    val getClipboardContents = {
        val item = clipboard?.primaryClip?.getItemAt(0)
        if (item?.text != null) {
            item.text.toString().trim()
        } else if (item?.uri != null) {
            item.uri.toString().trim()
        } else {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = uriText,
            onValueChange = {
                uriText = it
                error = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            label = { Text(stringResource(R.string.enter_uri_label)) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            placeholder = { Text(stringResource(R.string.enter_uri_prefix)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            trailingIcon = {
                IconButton(onClick = {
                    getClipboardContents()?.let { uriText = it }
                }) {
                    Icon(
                        Icons.Default.ContentPaste,
                        contentDescription = stringResource(R.string.paste),
                    )
                }
            },
        )

        Button(
            onClick = {
                val found = TalerUriExtractor.extractAll(uriText)
                when {
                    found.isEmpty() -> error = invalidUriError
                    found.size == 1 -> onUriSubmitted(found.single())
                    else -> scope.launch {
                        loading = true
                        aggregate = buildAggregate(found, previewer, ownUriChecker)
                        loading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = uriText.isNotBlank() && !loading,
        ) {
            Text(stringResource(R.string.open))
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        getClipboardContents()?.let { uri ->
            if (isValidTalerUri(uri)) {
                uriText = uri
            }
        }
    }

    aggregate?.let { agg ->
        MultiUriSummaryDialog(
            aggregate = agg,
            onAccept = { uri ->
                aggregate = null
                onUriSubmitted(uri)
            },
            onDismiss = { aggregate = null },
        )
    }
}

/**
 * Sammelkarte fuer mehrere in einer manuellen Einfuegung gefundene URIs (z.B.
 * ein per Signal-Transkript kopierter Gruppen-Split). Zeigt bewusst
 * "nicht mehr verfuegbar" statt eines erfundenen "X von N angenommen" - siehe
 * Begruendung in MultiUriSummary.kt.
 */
@Composable
private fun MultiUriSummaryDialog(
    aggregate: MultiUriAggregate,
    onAccept: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.multi_uri_title, aggregate.total)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                aggregate.totalAmount?.let { total ->
                    Text(
                        stringResource(
                            R.string.multi_uri_total_amount,
                            total.amountStr,
                            total.currency,
                        )
                    )
                }
                if (aggregate.unavailable > 0) {
                    Text(stringResource(R.string.multi_uri_unavailable, aggregate.unavailable, aggregate.total))
                }
                Text(
                    if (aggregate.acceptedByMe > 0) {
                        pluralStringResource(
                            R.plurals.multi_uri_accepted_by_me_some,
                            aggregate.acceptedByMe,
                            aggregate.acceptedByMe,
                        )
                    } else {
                        stringResource(R.string.multi_uri_accepted_by_me_none)
                    }
                )
                if (aggregate.acceptedByMe > 0) {
                    Text(
                        stringResource(R.string.multi_uri_already_accepted_warning),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                aggregate.open.forEach { share ->
                    TextButton(onClick = { onAccept(share.uri) }) {
                        Text(shareLabel(share))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/** Typ/Exchange-Host/ID-Suffix statt der rohen URI - dieselbe Phishing-Schutz-Logik wie Signals Karte (Exchange-Hostname sichtbar). */
private fun shareLabel(share: ShareSummary): String {
    val kind = TalerUriParser.classify(share.uri)
    val kindLabel = when (kind) {
        TalerUriKind.PAY_PUSH -> "Push"
        TalerUriKind.PAY_PULL -> "Pull"
        TalerUriKind.PAY -> "Pay"
        TalerUriKind.WITHDRAW -> "Withdraw"
        TalerUriKind.REFUND -> "Refund"
        null -> "?"
    }
    val host = share.preview?.exchangeBaseUrl ?: runCatching { share.uri.toUri().host }.getOrNull()
    val suffix = share.uri.takeLast(6)
    return listOfNotNull(kindLabel, host, "…$suffix").joinToString(" · ")
}