package com.example.flutterdynamic

import android.content.Context
import android.util.Log
import com.example.flutterdynamic.mode.SoPackageInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/**
 * 动态SO辅助工具
 * 提供SO包管理的便捷方法
 */
object DynamicSoHelper {
    
    private const val TAG = "DynamicSoHelper"
    private const val SERVER_BASE_URL = "http://127.0.0.1:1234"
    
    /**
     * 检查本地服务器连接
     */
    suspend fun checkServerConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$SERVER_BASE_URL/api/status"
                val connection = URL(url).openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 10000
                connection.connect()
                true
            } catch (e: Exception) {
                Log.e(TAG, "服务器连接失败", e)
                false
            }
        }
    }
    
    /**
     * 获取可用的SO包列表
     */
    suspend fun getAvailableSoPackages(): List<SoPackageInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$SERVER_BASE_URL/api/so-packages"
                val connection = URL(url).openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 10000
                
                val response = connection.getInputStream().readBytes().toString(Charsets.UTF_8)
                val gson = Gson()
                val listType = object : TypeToken<List<SoPackageInfo>>() {}.type
                gson.fromJson(response, listType) ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "获取SO包列表失败", e)
                emptyList()
            }
        }
    }
    
    /**
     * 清理动态SO缓存
     */
    fun clearSoCache(context: Context) {
        SoPackageManager.clearSoCache(context)
    }
    
    /**
     * 获取SO包下载URL
     */
    fun getSoPackageDownloadUrl(fileName: String): String {
        return "$SERVER_BASE_URL/api/download/$fileName"
    }
    
    /**
     * 检查SO包是否已缓存
     */
    fun isSoPackageCached(context: Context, packageName: String): Boolean {
        return try {
            val cacheDir = when (packageName) {
                "libflutter" -> File(context.cacheDir, "server_flutter")
                "libapp" -> File(context.cacheDir, "server_app")
                else -> return false
            }
            
            cacheDir.exists() && cacheDir.listFiles()?.isNotEmpty() == true
        } catch (e: Exception) {
            Log.e(TAG, "检查SO包缓存失败", e)
            false
        }
    }
    
    /**
     * 获取缓存的SO包信息
     */
    fun getCachedSoPackageInfo(context: Context, packageName: String): SoPackageInfo? {
        return try {
            val cacheDir = when (packageName) {
                "libflutter" -> File(context.cacheDir, "server_flutter")
                "libapp" -> File(context.cacheDir, "server_app")
                else -> return null
            }
            
            SoPackageManager.getSoPackageInfo(cacheDir)
        } catch (e: Exception) {
            Log.e(TAG, "获取缓存SO包信息失败", e)
            null
        }
    }
    
    /**
     * 获取服务器状态
     */
    suspend fun getServerStatus(): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$SERVER_BASE_URL/api/status"
                val connection = URL(url).openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 10000
                
                val response = connection.getInputStream().readBytes().toString(Charsets.UTF_8)
                val gson = Gson()
                val mapType = object : TypeToken<Map<String, Any>>() {}.type
                gson.fromJson(response, mapType) ?: emptyMap()
            } catch (e: Exception) {
                Log.e(TAG, "获取服务器状态失败", e)
                mapOf("error" to e.message.orEmpty())
            }
        }
    }
    
    /**
     * 测试SO包下载
     */
    suspend fun testSoPackageDownload(context: Context, fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = getSoPackageDownloadUrl(fileName)
                val connection = URL(url).openConnection()
                connection.connectTimeout = 10000
                connection.readTimeout = 30000
                
                val tempFile = File(context.cacheDir, "test_$fileName")
                
                connection.getInputStream().use { input ->
                    java.io.FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                val success = tempFile.exists() && tempFile.length() > 0
                tempFile.delete() // 清理测试文件
                
                Log.i(TAG, "测试下载 $fileName: ${if (success) "成功" else "失败"}")
                success
            } catch (e: Exception) {
                Log.e(TAG, "测试下载失败: $fileName", e)
                false
            }
        }
    }
    
    /**
     * 获取应用版本
     */
    fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            Log.e(TAG, "获取应用版本失败", e)
            "1.0.0"
        }
    }
}