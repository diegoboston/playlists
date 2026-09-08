package com.playlists.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.playlists.app.R
import com.playlists.app.util.FitRect
import com.playlists.app.util.LocalFileImport
import com.playlists.app.util.PageGeometry
import com.playlists.app.util.PagePoint
import com.playlists.app.util.PageQuad
import com.playlists.app.util.PageWarper
import com.playlists.app.util.PendingPageAdjust
import com.playlists.app.util.ScanImportOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustPageScreen(
    pending: PendingPageAdjust,
    onCancel: () -> Unit,
    onFinished: (ScanImportOutcome) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = remember(pending.filePath) { File(pending.filePath) }
    val imageSize = remember(pending.filePath) { PageWarper.orientedSize(file) }
    var quad by remember(pending.filePath) { mutableStateOf(PageQuad.normalizedInset()) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var working by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val handleHitPx = with(density) { 48.dp.toPx() }

    LaunchedEffect(pending.filePath) {
        quad = withContext(Dispatchers.IO) { PageWarper.suggestNormalizedQuad(file) }
    }

    fun finish(warpQuad: PageQuad?) {
        if (working) return
        working = true
        scope.launch {
            try {
                val outcome = withContext(Dispatchers.IO) {
                    if (warpQuad != null) {
                        if (!PageWarper.warpToFile(file, warpQuad)) {
                            return@withContext null
                        }
                    }
                    val existing = pending.existingImport
                    if (existing == null) {
                        LocalFileImport.finishStoredCapture(context, file)
                    } else {
                        LocalFileImport.addStoredPage(file, existing)
                    }
                }
                if (outcome == null) {
                    Toast.makeText(context, R.string.adjust_page_warp_failed, Toast.LENGTH_LONG).show()
                    working = false
                    return@launch
                }
                onFinished(outcome)
            } catch (_: Throwable) {
                Toast.makeText(context, R.string.adjust_page_warp_failed, Toast.LENGTH_LONG).show()
                working = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.adjust_page_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel, enabled = !working) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
                    .onSizeChanged { viewSize = it },
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(file)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                val size = imageSize
                if (size != null && viewSize.width > 0 && viewSize.height > 0) {
                    val fit = PageGeometry.contentFit(
                        size.first,
                        size.second,
                        viewSize.width,
                        viewSize.height,
                    )
                    PageQuadOverlay(
                        quad = quad,
                        fit = fit,
                        handleHitPx = handleHitPx,
                        enabled = !working,
                        onQuadChange = { quad = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (working) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(40.dp))
                            Text(
                                text = stringResource(R.string.adjust_page_working),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { finish(warpQuad = null) },
                    enabled = !working,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.adjust_page_use_original))
                }
                Button(
                    onClick = { finish(warpQuad = quad.clamped()) },
                    enabled = !working,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.adjust_page_use_page))
                }
            }
        }
    }
}

@Composable
private fun PageQuadOverlay(
    quad: PageQuad,
    fit: FitRect,
    handleHitPx: Float,
    enabled: Boolean,
    onQuadChange: (PageQuad) -> Unit,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.primary
    val latestQuad by rememberUpdatedState(quad)
    val latestOnChange by rememberUpdatedState(onQuadChange)
    var dragging by remember { mutableIntStateOf(-1) }
    Canvas(
        modifier = modifier.pointerInput(fit, enabled) {
            if (!enabled) return@pointerInput
            detectDragGestures(
                onDragStart = { start ->
                    dragging = nearestHandle(latestQuad, fit, start, handleHitPx)
                },
                onDragEnd = { dragging = -1 },
                onDragCancel = { dragging = -1 },
                onDrag = { change, _ ->
                    val index = dragging
                    if (index < 0) return@detectDragGestures
                    change.consume()
                    val point = PagePoint(
                        PageGeometry.fromViewX(change.position.x, fit),
                        PageGeometry.fromViewY(change.position.y, fit),
                    )
                    latestOnChange(latestQuad.withPoint(index, point.clamped()))
                },
            )
        },
    ) {
        val viewPoints = quad.points().map { point ->
            Offset(PageGeometry.toViewX(point.x, fit), PageGeometry.toViewY(point.y, fit))
        }
        val dim = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, size.width, size.height))
            moveTo(viewPoints[0].x, viewPoints[0].y)
            lineTo(viewPoints[1].x, viewPoints[1].y)
            lineTo(viewPoints[2].x, viewPoints[2].y)
            lineTo(viewPoints[3].x, viewPoints[3].y)
            close()
        }
        drawPath(dim, Color.Black.copy(alpha = 0.45f))
        val stroke = Path().apply {
            moveTo(viewPoints[0].x, viewPoints[0].y)
            lineTo(viewPoints[1].x, viewPoints[1].y)
            lineTo(viewPoints[2].x, viewPoints[2].y)
            lineTo(viewPoints[3].x, viewPoints[3].y)
            close()
        }
        drawPath(
            stroke,
            color = outline,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
        viewPoints.forEach { point ->
            drawCircle(color = Color.White, radius = 11.dp.toPx(), center = point)
            drawCircle(color = outline, radius = 11.dp.toPx(), center = point, style = Stroke(3.dp.toPx()))
        }
    }
}

private fun nearestHandle(
    quad: PageQuad,
    fit: FitRect,
    start: Offset,
    hitPx: Float,
): Int {
    var best = -1
    var bestDist = hitPx
    quad.points().forEachIndexed { index, point ->
        val x = PageGeometry.toViewX(point.x, fit)
        val y = PageGeometry.toViewY(point.y, fit)
        val dist = hypot(start.x - x, start.y - y)
        if (dist <= bestDist) {
            bestDist = dist
            best = index
        }
    }
    return best
}
