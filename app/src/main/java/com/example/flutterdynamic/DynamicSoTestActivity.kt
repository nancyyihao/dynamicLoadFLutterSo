package com.example.flutterdynamic

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.flutterdynamic.mode.SoPackageInfo
import com.google.gson.reflect.TypeToken

class DynamicSoTestActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var testButton: Button
    private lateinit var cleanButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dynamic_so_test)

        initViews()
        setupClickListeners()
        checkPermissions()
    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        testButton = findViewById(R.id.testButton)
        cleanButton = findViewById(R.id.cleanButton)
    }

    private fun setupClickListeners() {
        testButton.setOnClickListener {
            runTests()
        }

        cleanButton.setOnClickListener {
            cleanCache()
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                arrayOf(Manifest.permission.INTERNET), 1001)
        }
    }

    private fun runTests() {
        lifecycleScope.launch {
            updateStatus("开始测试动态SO加载功能...\n")
            
            // 测试服务器连接
            updateStatus("1. 测试服务器连接...")
            val serverConnected = testServerConnection()
            updateStatus(if (serverConnected) "✅ 服务器连接成功\n" else "❌ 服务器连接失败\n")
            
            if (!serverConnected) {
                updateStatus("请确保本地服务器正在运行并且ADB端口转发已设置\n")
                return@launch
            }
            
            // 测试SO包列表
            updateStatus("2. 获取SO包列表...")
            val soPackages = getSoPackageList()
            updateStatus("✅ 找到 ${soPackages.size} 个SO包\n")
            
            soPackages.forEach { packageInfo ->
                updateStatus("  - ${packageInfo.fileName} (${packageInfo.version})\n")
            }
            
            // 测试下载功能
            if (soPackages.isNotEmpty()) {
                updateStatus("3. 测试下载功能...")
                val firstPackage = soPackages.first()
                val downloadSuccess = testDownload(firstPackage.fileName)
                updateStatus(if (downloadSuccess) "✅ 下载测试成功\n" else "❌ 下载测试失败\n")
            }
            
            updateStatus("测试完成！\n")
        }
    }

    private fun cleanCache() {
        lifecycleScope.launch {
            updateStatus("清理缓存中...\n")
            
            try {
                // 清理缓存目录
                val cacheDir = cacheDir
                val flutterCache = java.io.File(cacheDir, "server_flutter")
                val appCache = java.io.File(cacheDir, "server_app")
                
                if (flutterCache.exists()) {
                    flutterCache.deleteRecursively()
                    updateStatus("✅ 清理Flutter缓存\n")
                }
                
                if (appCache.exists()) {
                    appCache.deleteRecursively()
                    updateStatus("✅ 清理App缓存\n")
                }
                
                // 清理临时文件
                cacheDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("temp_")) {
                        file.delete()
                        updateStatus("✅ 清理临时文件: ${file.name}\n")
                    }
                }
                
                updateStatus("缓存清理完成！\n")
            } catch (e: Exception) {
                updateStatus("❌ 清理失败: ${e.message}\n")
            }
        }
    }

    private suspend fun testServerConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = java.net.URL("http://192.168.1.2:1234/api/status")
                val connection = url.openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.connect()
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    private suspend fun getSoPackageList(): List<SoPackageInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val url = java.net.URL("http://192.168.1.2:1234/api/so-packages")
                val connection = url.openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 10000
                
                val response = connection.getInputStream().readBytes().toString(Charsets.UTF_8)
                val gson = com.google.gson.Gson()
                val listType = object : TypeToken<List<SoPackageInfo>>() {}.type
                gson.fromJson<List<SoPackageInfo>>(response, listType) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private suspend fun testDownload(fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = java.net.URL("http://192.168.1.2:1234/api/download/$fileName")
                val connection = url.openConnection()
                connection.connectTimeout = 10000
                connection.readTimeout = 30000
                
                val tempFile = java.io.File(cacheDir, "test_$fileName")
                connection.getInputStream().use { input ->
                    java.io.FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                val success = tempFile.exists() && tempFile.length() > 0
                tempFile.delete() // 清理测试文件
                success
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            statusText.append(message)
        }
    }
}