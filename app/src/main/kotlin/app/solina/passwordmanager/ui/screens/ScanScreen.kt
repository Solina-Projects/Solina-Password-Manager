package app.solina.passwordmanager.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.solina.passwordmanager.scan.QrDecoder
import app.solina.passwordmanager.ui.components.IconAction
import app.solina.passwordmanager.ui.theme.Solina
import app.solina.passwordmanager.ui.theme.SolinaIcons
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * اسکن QR. دوربین فقط وقتی این صفحه باز است روشن است و هیچ تصویری ذخیره یا ارسال نمی‌شود.
 * [onResult] فقط یک‌بار با متن QR صدا زده می‌شود.
 */
@Composable
fun ScanScreen(onResult: (String) -> Unit, onCancel: () -> Unit, onToast: (String) -> Unit) {
    val c = Solina.colors
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var denied by remember { mutableStateOf(false) }
    val done = remember { AtomicBoolean(false) }
    fun deliver(text: String) { if (done.compareAndSet(false, true)) onResult(text) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val text = runCatching { QrDecoder.decodeImage(ctx.contentResolver, uri) }.getOrNull()
            if (text != null) deliver(text) else onToast("در این عکس QR پیدا نشد")
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) CameraPreview(onText = ::deliver)

        Row(Modifier.statusBarsPadding().padding(top = 12.dp, start = 8.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(SolinaIcons.ChevronRight, "بازگشت", tint = Color.White, onClick = onCancel)
            Text("اسکن QR", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        if (granted) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(240.dp)
                    .border(3.dp, c.primary, RoundedCornerShape(24.dp)),
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!granted) {
                Text(
                    if (denied) "اجازهٔ دوربین داده نشد. می‌توانید به‌جای آن عکس یا اسکرین‌شات QR را انتخاب کنید."
                    else "برای اسکن QR به دوربین نیاز است.",
                    color = Color.White, fontSize = 15.sp, lineHeight = 26.sp, textAlign = TextAlign.Center,
                )
                if (denied) Text(
                    "دوباره اجازه بده",
                    color = c.primaryContainer, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(role = Role.Button) { permission.launch(Manifest.permission.CAMERA) }.padding(8.dp),
                )
            } else {
                Text("QR را داخل کادر بگیرید", color = Color.White, fontSize = 15.sp)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(c.primary)
                    .clickable(role = Role.Button) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                contentAlignment = Alignment.Center,
            ) { Text("انتخاب عکس QR از گالری", color = c.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
private fun CameraPreview(onText: (String) -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val view = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = future.get()
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { image ->
                try {
                    val plane = image.planes[0]
                    val buf = plane.buffer
                    val y = ByteArray(buf.remaining())
                    buf.get(y)
                    // سطرهای padding دار: عرض مفید image.width و گام سطر rowStride است.
                    val text = QrDecoder.decodeLuminance(y, image.width, image.height, plane.rowStride)
                    if (text != null) ContextCompat.getMainExecutor(ctx).execute { onText(text) }
                } finally {
                    image.close()
                }
            }
            try {
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                // دوربین در دسترس نیست؛ کاربر می‌تواند از گالری استفاده کند.
            }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
}
