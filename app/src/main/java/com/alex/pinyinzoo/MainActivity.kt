package com.alex.pinyinzoo

import android.Manifest
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.alex.pinyinzoo.ui.AppRoot
import com.alex.pinyinzoo.ui.theme.PinyinZooTheme
import com.alex.pinyinzoo.ui.theme.ZooColors

/**
 * 应用入口。
 *
 * 屏幕适配策略：
 * - 强制横屏（Manifest 已声明，这里再做一次保证）
 * - 全屏沉浸：隐藏状态栏/导航栏，MIUI 会偷偷恢复，所以用 WindowInsetsController + 重复手势
 * - 不跟随系统字体缩放：Compose 的 Typography 已固定字号，这里再屏蔽 fontScale
 */
class MainActivity : ComponentActivity() {

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
        onMicResult?.invoke(granted)
    }

    /** 麦克风授权结果回调（由 Compose 层注册） */
    private var onMicResult: ((Boolean) -> Unit)? = null

    @Volatile
    private var micGranted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 强制横屏 + 保持常亮（孩子可能长时间看，但导航栏隐藏后不会误触）
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        // 屏蔽系统字体缩放：把 fontScale 固定为 1，避免大字体撑爆横屏布局
        applyFontScaleHack()

        hideSystemBars()

        setContent {
            PinyinZooTheme {
                Surface(color = ZooColors.Cream, modifier = Modifier.fillMaxSize()) {
                    AppRoot(
                        onRequestMic = { callback -> requestMic(callback) },
                        onExit = { finish() },
                    )
                }
            }
        }
    }

    /** 隐藏状态栏与导航栏，并设置沉浸式手势 */
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 部分 ROM（MIUI/EMUI）在失去焦点后会恢复状态栏，这里重新隐藏
        if (hasFocus) hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    /**
     * 字体缩放规避：反射修改 Configuration 的 fontScale。
     * 不修改会让 22sp 变成 30sp，横屏 407dp 高度必然溢出。
     */
    private fun applyFontScaleHack() {
        try {
            val config = resources.configuration
            val clazz = config.javaClass
            val field = clazz.getDeclaredField("fontScale")
            field.isAccessible = true
            field.setFloat(config, 1.0f)
            @Suppress("DEPRECATION")
            resources.updateConfiguration(config, resources.displayMetrics)
        } catch (_: Exception) {
            // 失败不影响主流程，Compose 端还有固定 sp 字号兜底
        }
    }

    /** 请求麦克风权限（结果回传给 Compose 层） */
    fun requestMic(callback: (Boolean) -> Unit) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            micGranted = true
            callback(true)
            return
        }
        onMicResult = callback
        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun hasMicPermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
}
