package com.example.flutterdynamic

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.flutter.embedding.android.FlutterActivity
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 动态Flutter Activity
 * 启动时自动下载SO包并初始化Flutter，然后跳转到Flutter页面
 */
class DynamicFlutterActivity : AppCompatActivity() {
    
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    
    companion object {
        private const val TAG = "DynamicFlutterActivity"
        
        fun createIntent(context: Context): Intent {
            return Intent(context, DynamicFlutterActivity::class.java)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupLoadingUI()
        startDynamicLoading()
    }
    
    private fun setupLoadingUI() {
        // 创建加载界面
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(64, 64, 64, 64)
        }
        
        // Flutter Logo (使用文字代替)
        val logoText = TextView(this).apply {
            text = "🚀 Flutter"
            textSize = 32f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        
        // 状态文本
        statusText = TextView(this).apply {
            text = "正在初始化Flutter引擎..."
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        
        // 进度条
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            setPadding(0, 0, 0, 32)
        }
        
        // 提示文本
        val hintText = TextView(this).apply {
            text = "首次启动需要下载Flutter引擎\n请稍候..."
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            alpha = 0.7f
        }
        
        layout.addView(logoText)
        layout.addView(statusText)
        layout.addView(progressBar)
        layout.addView(hintText)
        
        setContentView(layout)
    }
    
    private fun startDynamicLoading() {
        lifecycleScope.launch {
            try {
                updateStatus("检查本地服务器连接...")
                
                // 检查FlutterManager是否已经初始化
                val engineGroup = FlutterManager.getEngineGroup()
                if (engineGroup != null) {
                    updateStatus("Flutter引擎已就绪")
                    launchFlutterPage()
                    return@launch
                }
                
                updateStatus("正在下载Flutter引擎...")
                
                // 初始化FlutterManager（这会触发SO下载和加载）
                FlutterManager.init(this@DynamicFlutterActivity)
                
                // 等待初始化完成
                kotlinx.coroutines.delay(3000)
                
                val newEngineGroup = FlutterManager.getEngineGroup()
                if (newEngineGroup != null) {
                    updateStatus("Flutter引擎初始化成功")
                    launchFlutterPage()
                } else {
                    updateStatus("Flutter引擎初始化失败")
                    showError("无法初始化Flutter引擎，请检查网络连接")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "动态加载Flutter失败", e)
                updateStatus("加载失败")
                showError("加载Flutter引擎时发生错误: ${e.message}")
            }
        }
    }
    
    private fun updateStatus(message: String) {
        runOnUiThread {
            statusText.text = message
            Log.i(TAG, message)
        }
    }
    
    private fun showError(message: String) {
        runOnUiThread {
            statusText.text = "❌ $message"
            progressBar.visibility = View.GONE
            
            // 添加重试按钮
            val retryButton = android.widget.Button(this).apply {
                text = "重试"
                setOnClickListener {
                    recreate() // 重新创建Activity
                }
            }
            
            val layout = statusText.parent as android.widget.LinearLayout
            layout.addView(retryButton)
        }
    }
    
    private fun launchFlutterPage() {
        runOnUiThread {
            try {
                updateStatus("启动Flutter页面...")
                
                // 启动Flutter Activity
                val flutterIntent = FlutterActivity.createDefaultIntent(this)
                startActivity(flutterIntent)
                
                // 关闭当前Activity
                finish()
                
            } catch (e: Exception) {
                Log.e(TAG, "启动Flutter页面失败", e)
                showError("启动Flutter页面失败: ${e.message}")
            }
        }
    }
}