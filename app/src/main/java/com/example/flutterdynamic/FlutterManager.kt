package com.example.flutterdynamic

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import com.example.flutterdynamic.download.DownloadConfig
import com.example.flutterdynamic.download.DownloadManager
import com.example.flutterdynamic.download.IDownloadListener
import com.example.flutterdynamic.mode.FlutterConfig
import com.example.flutterdynamic.mode.SoPackageInfo
import com.example.flutterdynamic.util.MD5Util
import com.example.flutterdynamic.util.fromJsonProxy
import com.google.gson.Gson
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.FlutterEngineGroup
import io.flutter.embedding.engine.FlutterJNI
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object FlutterManager {

    private const val TAG = "FlutterManager"
    private var engineGroup: FlutterEngineGroup? = null

    fun init(context: Context) {
        // 使用Thread来避免NetworkOnMainThreadException
        Thread {
            try {
                // 直接从本地服务器加载SO包
                val success = loadFromLocalServerSync(context)
                if (!success) {
                    // 如果本地服务器失败，回退到assets配置
                    MainScope().launch {
                        loadFromAssetsConfig(context)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Flutter初始化失败", e)
                // 如果本地服务器失败，回退到assets配置
                MainScope().launch {
                    loadFromAssetsConfig(context)
                }
            }
        }.start()
    }

    /**
     * 从本地服务器加载SO文件（同步版本）
     */
    private fun loadFromLocalServerSync(context: Context): Boolean {
        val localServerUrl = "http://192.168.1.2:1235"
        Log.i(TAG, "开始从本地服务器加载SO: $localServerUrl")
        
        try {
            // 获取SO包列表
            val soListUrl = "$localServerUrl/api/so-packages"
            val soPackages = fetchSoPackageListSync(soListUrl)
            
            if (soPackages.isEmpty()) {
                Log.w(TAG, "本地服务器没有可用的SO包")
                return false
            }
            
            val appVersion = getAppVersion(context)
            
            // 查找兼容的Flutter SO包
            val flutterPackage = findCompatiblePackage(soPackages, "libflutter", appVersion)
            val appPackage = findCompatiblePackage(soPackages, "libapp", appVersion)
            
            if (flutterPackage == null) {
                Log.w(TAG, "未找到兼容的Flutter SO包版本")
                return false
            }
            
            if (appPackage == null) {
                Log.w(TAG, "未找到兼容的App SO包版本")
                return false
            }
            
            Log.i(TAG, "找到Flutter SO包: ${flutterPackage.fileName}, 版本: ${flutterPackage.version}")
            Log.i(TAG, "找到App SO包: ${appPackage.fileName}, 版本: ${appPackage.version}")
            
            // 下载Flutter SO包
            val flutterZipFile = downloadSoPackageFromServerSync(
                context, 
                "$localServerUrl${flutterPackage.url}",
                flutterPackage.fileName
            )
            
            // 下载App SO包
            val appZipFile = downloadSoPackageFromServerSync(
                context,
                "$localServerUrl${appPackage.url}",
                appPackage.fileName
            )
            
            if (flutterZipFile == null) {
                Log.e(TAG, "从本地服务器下载Flutter SO包失败")
                return false
            }
            
            if (appZipFile == null) {
                Log.e(TAG, "从本地服务器下载App SO包失败")
                flutterZipFile.delete()
                return false
            }
            
            // 解压并验证Flutter SO包
            val flutterExtractDir = File(context.cacheDir, "server_flutter")
            val flutterInfo = SoPackageManager.extractAndVerifySoPackage(
                context, flutterZipFile, flutterExtractDir
            )
            
            // 解压并验证App SO包
            val appExtractDir = File(context.cacheDir, "server_app")
            val appInfo = SoPackageManager.extractAndVerifySoPackage(
                context, appZipFile, appExtractDir
            )
            
            if (flutterInfo != null && appInfo != null) {
                val flutterSoFile = File(flutterExtractDir, flutterInfo.fileName)
                val appSoFile = File(appExtractDir, appInfo.fileName)
                
                if (flutterSoFile.exists() && appSoFile.exists()) {
                    // Flutter初始化必须在主线程执行
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        try {
                            // 使用实际的app SO路径
                            loadAndInitFlutter(context, flutterExtractDir, appSoFile.absolutePath)
                            Log.i(TAG, "从本地服务器加载双SO成功 - Flutter版本: ${flutterInfo.version}, App版本: ${appInfo.version}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Flutter初始化失败", e)
                        }
                    }
                    
                    // 清理下载的ZIP文件
                    flutterZipFile.delete()
                    appZipFile.delete()
                    
                    return true
                } else {
                    Log.e(TAG, "SO文件不存在 - Flutter: ${flutterSoFile.exists()}, App: ${appSoFile.exists()}")
                }
            } else {
                Log.e(TAG, "SO包解压或验证失败 - Flutter: ${flutterInfo != null}, App: ${appInfo != null}")
            }
            
            // 清理失败的文件
            flutterZipFile.delete()
            appZipFile.delete()
            
        } catch (e: Exception) {
            Log.e(TAG, "从本地服务器加载SO失败", e)
        }
        
        return false
    }

    /**
     * 从assets配置加载SO文件（原有逻辑）
     */
    private suspend fun loadFromAssetsConfig(context: Context) {
        val flutterSoUrl = context.assets.open("flutterso.json").readBytes().decodeToString()
        val flutterConfig = Gson().fromJsonProxy(flutterSoUrl, FlutterConfig::class.java) ?: return
        
        // 检查版本兼容性
        val appVersion = getAppVersion(context)
        if (!SoPackageManager.isVersionCompatible(
                flutterConfig.flutterSoVersion,
                appVersion,
                flutterConfig.minAppVersion,
                flutterConfig.maxAppVersion
            )) {
            Log.w(TAG, "Flutter SO版本不兼容，当前App版本: $appVersion")
            return
        }

        Log.i(TAG, "开始从assets配置下载SO文件")
        Log.i(TAG, "Flutter SO URL: ${flutterConfig.flutterSoUrl}")
        Log.i(TAG, "App SO URL: ${flutterConfig.appSoUrl}")

        val libFlutterSOSaveDir = context.getDir("libflutter", Context.MODE_PRIVATE)
        
        // 下载Flutter SO
        val libFlutterResult = downloadDynamicSO(context, DownloadConfig(
            flutterConfig.flutterSoUrl,
            libFlutterSOSaveDir.absolutePath
        ).apply {
            fileName = "libflutter.so"
            expectedMd5 = flutterConfig.flutterSoMd5
            expectedSize = flutterConfig.flutterSoSize
        })
        
        // 下载App SO
        val libAppResult = downloadDynamicSO(context, DownloadConfig(
            flutterConfig.appSoUrl,
            context.getDir("libapp", Context.MODE_PRIVATE).absolutePath
        ).apply {
            fileName = "libapp.so"
            expectedMd5 = flutterConfig.appSoMd5
            expectedSize = flutterConfig.appSoSize
        })

        //下载完成，动态加载，并初始化 FlutterEngineGroup
        if (!TextUtils.isEmpty(libFlutterResult) && !TextUtils.isEmpty(libAppResult)){
            Log.i(TAG, "两个SO文件下载成功，开始初始化Flutter")
            loadAndInitFlutter(context, libFlutterSOSaveDir, libAppResult!!)
        } else {
            Log.e(TAG, "SO文件下载失败 - Flutter: ${libFlutterResult != null}, App: ${libAppResult != null}")
        }
    }

    private suspend fun downloadDynamicSO(context: Context, downloadConfig: DownloadConfig): String? {
        return suspendCoroutine { continuation ->
            var startTime = System.currentTimeMillis()
            DownloadManager.instance.start(
                context,
                downloadConfig,
                object : IDownloadListener {
                    override fun onStart(url: String?, contentLength: Long) {
                        super.onStart(url, contentLength)
                        startTime = System.currentTimeMillis()
                    }

                    override fun onSuccess(url: String?, savePath: Uri?) {
                        super.onSuccess(url, savePath)
                        
                        // 验证下载的文件
                        val downloadedFile = File(savePath?.path ?: "")
                        if (downloadedFile.exists()) {
                            val actualMd5 = MD5Util.getFileMD5(downloadedFile)
                            val actualSize = downloadedFile.length()
                            
                            if (downloadConfig.expectedMd5.isNotEmpty() && 
                                actualMd5 != downloadConfig.expectedMd5) {
                                Log.e(TAG, "文件MD5校验失败: 期望=${downloadConfig.expectedMd5}, 实际=$actualMd5")
                                downloadedFile.delete()
                                continuation.resume(null)
                                return
                            }
                            
                            if (downloadConfig.expectedSize > 0 && 
                                actualSize != downloadConfig.expectedSize) {
                                Log.e(TAG, "文件大小校验失败: 期望=${downloadConfig.expectedSize}, 实际=$actualSize")
                                downloadedFile.delete()
                                continuation.resume(null)
                                return
                            }
                        }
                        
                        Log.i(TAG, "下载成功[$url] -> ${downloadConfig.fileName} & 耗时-> ${System.currentTimeMillis() - startTime}")
                        continuation.resume(savePath?.path)
                    }

                    override fun onFailed(url: String?, throwable: Throwable) {
                        super.onFailed(url, throwable)
                        Log.e(TAG, "下载失败[${downloadConfig.fileName}] -> $throwable & 耗时-> ${System.currentTimeMillis() - startTime}")
                        continuation.resume(null)
                    }
                })
        }
    }

    private fun loadAndInitFlutter(context: Context, flutterSOSaveDir: File, appSOSavePath: String) {
        TinkerLoadLibrary.installNativeLibraryPath(context.classLoader, flutterSOSaveDir)
        
        if (appSOSavePath.isNotEmpty()) {
            // 有app SO文件时使用自定义JNI
            FlutterInjector.setInstance(
                FlutterInjector.Builder()
                .setFlutterJNIFactory(CustomFlutterJNI.CustomFactory(appSOSavePath))
                .build())
        } else {
            // 只有Flutter SO时使用默认配置
            Log.i(TAG, "使用默认Flutter配置（无app SO）")
        }
        
        engineGroup = FlutterEngineGroup(context)
        Log.i(TAG, "FlutterEngineGroup初始化完成")
    }

    private fun fetchSoPackageListSync(url: String): List<SoPackageInfo> {
        return try {
            Log.i(TAG, "正在请求SO包列表: $url")
            
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "FlutterDynamic/1.0")
            connection.setRequestProperty("Connection", "close")
            
            Log.i(TAG, "开始连接服务器...")
            connection.connect()
            
            val responseCode = connection.responseCode
            Log.i(TAG, "HTTP响应码: $responseCode")
            
            if (responseCode == 200) {
                val inputStream = connection.inputStream
                val response = inputStream.readBytes().toString(Charsets.UTF_8)
                inputStream.close()
                
                Log.i(TAG, "服务器响应长度: ${response.length} 字符")
                Log.i(TAG, "服务器响应内容: ${response.take(200)}...")
                
                val gson = com.google.gson.Gson()
                val listType = object : com.google.gson.reflect.TypeToken<List<SoPackageInfo>>() {}.type
                val packages = gson.fromJson<List<SoPackageInfo>>(response, listType) ?: emptyList()
                Log.i(TAG, "成功解析到${packages.size}个SO包")
                
                connection.disconnect()
                packages
            } else {
                Log.e(TAG, "HTTP请求失败，响应码: $responseCode")
                connection.disconnect()
                emptyList()
            }
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "连接服务器失败: ${e.message}")
            emptyList()
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "连接超时: ${e.message}")
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "获取SO包列表失败: ${e.javaClass.simpleName} - ${e.message}", e)
            emptyList()
        }
    }

    private suspend fun fetchSoPackageList(url: String): List<SoPackageInfo> {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "正在请求SO包列表: $url")
                
                // 使用更简单的HTTP实现
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "FlutterDynamic/1.0")
                connection.setRequestProperty("Connection", "close")
                
                Log.i(TAG, "开始连接服务器...")
                connection.connect()
                
                val responseCode = connection.responseCode
                Log.i(TAG, "HTTP响应码: $responseCode")
                
                if (responseCode == 200) {
                    val inputStream = connection.inputStream
                    val response = inputStream.readBytes().toString(Charsets.UTF_8)
                    inputStream.close()
                    
                    Log.i(TAG, "服务器响应长度: ${response.length} 字符")
                    Log.i(TAG, "服务器响应内容: ${response.take(200)}...")
                    
                    val gson = com.google.gson.Gson()
                    val listType = object : com.google.gson.reflect.TypeToken<List<SoPackageInfo>>() {}.type
                    val packages = gson.fromJson<List<SoPackageInfo>>(response, listType) ?: emptyList()
                    Log.i(TAG, "成功解析到${packages.size}个SO包")
                    
                    connection.disconnect()
                    return@withContext packages
                } else {
                    Log.e(TAG, "HTTP请求失败，响应码: $responseCode")
                    // 尝试读取错误响应
                    try {
                        val errorStream = connection.errorStream
                        if (errorStream != null) {
                            val errorResponse = errorStream.readBytes().toString(Charsets.UTF_8)
                            Log.e(TAG, "错误响应: $errorResponse")
                            errorStream.close()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "读取错误响应失败: ${e.message}")
                    }
                    connection.disconnect()
                    return@withContext emptyList()
                }
            } catch (e: java.net.ConnectException) {
                Log.e(TAG, "连接服务器失败: ${e.message}")
                emptyList<SoPackageInfo>()
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "连接超时: ${e.message}")
                emptyList<SoPackageInfo>()
            } catch (e: Exception) {
                Log.e(TAG, "获取SO包列表失败: ${e.javaClass.simpleName} - ${e.message}", e)
                emptyList<SoPackageInfo>()
            }
        }
    }
    
    private fun findCompatiblePackage(
        packages: List<SoPackageInfo>,
        packagePrefix: String,
        appVersion: String
    ): SoPackageInfo? {
        return packages
            .filter { it.fileName.startsWith(packagePrefix) }
            .maxByOrNull { parseVersionCode(it.version) }
    }
    
    private fun downloadSoPackageFromServerSync(
        context: Context,
        downloadUrl: String,
        fileName: String
    ): File? {
        return try {
            Log.i(TAG, "开始下载SO包: $downloadUrl")
            val tempFile = File(context.cacheDir, "temp_$fileName")
            
            val connection = java.net.URL(downloadUrl).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 60000
            connection.setRequestProperty("User-Agent", "FlutterDynamic/1.0")
            
            val responseCode = connection.responseCode
            Log.i(TAG, "下载响应码: $responseCode")
            
            if (responseCode == 200) {
                connection.inputStream.use { input ->
                    java.io.FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytes = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead
                        }
                        Log.i(TAG, "下载进度: ${totalBytes} bytes")
                    }
                }
                
                Log.i(TAG, "从服务器下载完成: $fileName, 大小: ${tempFile.length()} bytes")
                tempFile
            } else {
                Log.e(TAG, "下载失败，HTTP响应码: $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "下载SO包失败: $fileName - ${e.message}", e)
            null
        }
    }

    private suspend fun downloadSoPackageFromServer(
        context: Context,
        downloadUrl: String,
        fileName: String
    ): File? {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "开始下载SO包: $downloadUrl")
                val tempFile = File(context.cacheDir, "temp_$fileName")
                
                val connection = java.net.URL(downloadUrl).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 60000
                connection.setRequestProperty("User-Agent", "FlutterDynamic/1.0")
                
                val responseCode = connection.responseCode
                Log.i(TAG, "下载响应码: $responseCode")
                
                if (responseCode == 200) {
                    connection.inputStream.use { input ->
                        java.io.FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalBytes = 0L
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytes += bytesRead
                            }
                            Log.i(TAG, "下载进度: ${totalBytes} bytes")
                        }
                    }
                    
                    Log.i(TAG, "从服务器下载完成: $fileName, 大小: ${tempFile.length()} bytes")
                    tempFile
                } else {
                    Log.e(TAG, "下载失败，HTTP响应码: $responseCode")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "下载SO包失败: $fileName - ${e.message}", e)
                null
            }
        }
    }
    
    private fun parseVersionCode(version: String): Int {
        return try {
            version.replace(".", "").toInt()
        } catch (e: Exception) {
            0
        }
    }

    private fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0.0"
        }
    }

    fun getEngineGroup(): FlutterEngineGroup? = engineGroup
}

class CustomFlutterJNI(private val appSOSavePath: String) : FlutterJNI(){
    override fun init(
        context: Context,
        args: Array<out String>,
        bundlePath: String?,
        appStoragePath: String,
        engineCachesPath: String,
        initTimeMillis: Long
    ) {
        val hookArgs = args.toMutableList().run {
            add("--aot-shared-library-name=$appSOSavePath")
            toTypedArray()
        }
        super.init(context, hookArgs, bundlePath, appStoragePath, engineCachesPath, initTimeMillis)
    }

    class CustomFactory(private val appSOSavePath: String) : Factory(){
        override fun provideFlutterJNI(): FlutterJNI {
            return CustomFlutterJNI(appSOSavePath)
        }
    }
}