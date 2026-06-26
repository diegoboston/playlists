package com.playlists.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.playlists.app.data.FileType
import com.playlists.app.ui.PdfHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SongMediaViewer(
    file: File,
    fileType: FileType,
    modifier: Modifier = Modifier,
    pdfPageIndex: Int? = null,
    enableZoom: Boolean = true,
    onPdfPageChanged: ((page: Int, pageCount: Int) -> Unit)? = null,
) {
    when (fileType) {
        FileType.IMAGE -> ZoomableImage(file = file, modifier = modifier, enableZoom = enableZoom)
        FileType.PDF -> if (pdfPageIndex != null) {
            SinglePdfPageViewer(
                file = file,
                pageIndex = pdfPageIndex,
                modifier = modifier,
                enableZoom = enableZoom,
            )
        } else {
            PdfPagerViewer(
                file = file,
                modifier = modifier,
                enableZoom = enableZoom,
                onPageChanged = onPdfPageChanged,
            )
        }
    }
}

@Composable
private fun ZoomableImage(
    file: File,
    modifier: Modifier = Modifier,
    enableZoom: Boolean = true,
) {
    var scale by remember(file) { mutableFloatStateOf(1f) }
    var offset by remember(file) { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (enableZoom) {
                    Modifier.pointerInput(file) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offset += pan
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(file)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (enableZoom) {
                        Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

@Composable
private fun SinglePdfPageViewer(
    file: File,
    pageIndex: Int,
    modifier: Modifier = Modifier,
    enableZoom: Boolean = true,
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerSize = it },
    ) {
        PdfPageImage(
            file = file,
            pageIndex = pageIndex,
            width = containerSize.width.coerceAtLeast(1),
            enableZoom = enableZoom,
        )
    }
}

@Composable
private fun PdfPagerViewer(
    file: File,
    modifier: Modifier = Modifier,
    enableZoom: Boolean = true,
    onPageChanged: ((page: Int, pageCount: Int) -> Unit)? = null,
) {
    var pageCount by remember(file) { mutableStateOf(0) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val pagerState = rememberPagerState(pageCount = { pageCount.coerceAtLeast(1) })

    LaunchedEffect(file) {
        pageCount = withContext(Dispatchers.IO) { PdfHelper.pageCount(file) }
    }

    LaunchedEffect(pagerState.currentPage, pageCount) {
        if (pageCount > 0) {
            onPageChanged?.invoke(pagerState.currentPage, pageCount)
        }
    }

    if (pageCount <= 0) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
        )
        return
    }

    if (pageCount == 1) {
        SinglePdfPageViewer(
            file = file,
            pageIndex = 0,
            modifier = modifier,
            enableZoom = enableZoom,
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerSize = it },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            PdfPageImage(
                file = file,
                pageIndex = page,
                width = containerSize.width.coerceAtLeast(1),
                enableZoom = false,
            )
        }
        Text(
            text = "${pagerState.currentPage + 1} / $pageCount",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun PdfPageImage(
    file: File,
    pageIndex: Int,
    width: Int,
    enableZoom: Boolean = true,
) {
    var bitmap by remember(file, pageIndex, width) { mutableStateOf<Bitmap?>(null) }
    var scale by remember(file, pageIndex) { mutableFloatStateOf(1f) }
    var offset by remember(file, pageIndex) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(file, pageIndex, width) {
        if (width <= 0) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            PdfHelper.renderPage(file, pageIndex, width)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (enableZoom) {
                    Modifier.pointerInput(file, pageIndex) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offset += pan
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (enableZoom) {
                            Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}
