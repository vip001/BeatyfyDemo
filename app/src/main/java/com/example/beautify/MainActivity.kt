package com.example.beautify

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.example.beautify.face.BeautyFaceAdapter
import com.example.beautify.face.FaceEngine
import com.example.beautify.face.SampleImages
import com.example.beautify.ui.theme.BeautifyDemoTheme
import com.example.beautify.BeautyDefaults
import com.example.beautify.BeautyPanelParams
import com.example.beautify.StillBeauty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            BeautifyDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BeautyScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun BeautyScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf("准备初始化…") }
    var busy by remember { mutableStateOf(false) }
    var smooth by remember { mutableFloatStateOf(BeautyDefaults.SMOOTH) }

    DisposableEffect(Unit) {
        StillBeauty.faceDetector = BeautyFaceAdapter
        onDispose {
            StillBeauty.faceDetector = null
            FaceEngine.terminate()
        }
    }

    fun runBeauty() {
        val src = sourceBitmap ?: return
        busy = true
        status = "磨皮处理中…"
        scope.launch {
            val out = withContext(Dispatchers.Default) {
                val params = BeautyPanelParams.defaults().apply {
                    this.smooth = smooth
                }
                StillBeauty.process(context, src, params)
            }
            displayBitmap?.takeIf {
                it !== sourceBitmap && it !== out && !it.isRecycled
            }?.recycle()
            displayBitmap = out
            busy = false
            status = if (out === src) {
                "磨皮未生效（无人脸/强度过低/GL 失败）"
            } else {
                "磨皮完成 强度=${(smooth * 100).roundToInt()}"
            }
        }
    }

    fun setBitmap(src: Bitmap) {
        displayBitmap?.takeIf {
            it !== sourceBitmap && it !== src && !it.isRecycled
        }?.recycle()
        sourceBitmap?.takeIf { it !== src && !it.isRecycled }?.recycle()
        sourceBitmap = src
        displayBitmap = src
        status = "已载入 ${src.width}x${src.height}，点「磨皮」处理"
        busy = false
    }

    LaunchedEffect(Unit) {
        busy = true
        val ok = withContext(Dispatchers.Default) {
            FaceEngine.ensureLaunched(context)
        }
        if (!ok) {
            busy = false
            status = "InspireFace GlobalLaunch 失败"
            return@LaunchedEffect
        }
        status = "已加载 model=${FaceEngine.modelName()}，载入样例…"
        val sample = withContext(Dispatchers.IO) { SampleImages.loadSdkSample(context) }
        if (sample != null) {
            setBitmap(sample)
        } else {
            busy = false
            status = "样例图缺失，请从相册导入"
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        status = "解码相册图片…"
        scope.launch {
            val decoded = withContext(Dispatchers.IO) {
                decodeBitmapUri(context, uri)
            }
            if (decoded == null) {
                busy = false
                status = "相册图片解码失败"
            } else {
                setBitmap(decoded)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("BeautifyDemo · 磨皮", style = MaterialTheme.typography.titleLarge)
        Text(status, style = MaterialTheme.typography.bodyMedium)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            val bmp = displayBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else if (busy) {
                CircularProgressIndicator()
            } else {
                Text("无图像", color = Color.White)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        val sample = withContext(Dispatchers.IO) {
                            SampleImages.loadSdkSample(context)
                        }
                        if (sample != null) setBitmap(sample) else {
                            busy = false
                            status = "无法加载 SDK 样例"
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) { Text("SDK 样例") }

            Button(
                onClick = { runBeauty() },
                enabled = !busy && sourceBitmap != null,
                modifier = Modifier.weight(1f)
            ) { Text("磨皮") }

            Button(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) { Text("从相册导入") }
        }

        Text("磨皮 ${(smooth * 100).roundToInt()}")
        Slider(
            value = smooth,
            onValueChange = { smooth = it },
            valueRange = 0f..1f,
            enabled = !busy
        )

        Spacer(modifier.height(8.dp))
    }
}

private fun decodeBitmapUri(context: android.content.Context, uri: Uri): Bitmap? {
    return runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val maxSide = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        while (sample < 64 && maxSide / (sample * 2) >= 1600) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        val orientation = resolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
        val rotated = applyExif(raw, orientation)
        if (rotated !== raw && !raw.isRecycled) raw.recycle()
        rotated
    }.getOrNull()
}

private fun applyExif(src: Bitmap, orientation: Int): Bitmap {
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (degrees == 0f) return src
    val m = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}
