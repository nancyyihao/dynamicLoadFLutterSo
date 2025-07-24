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
        val localServerUrl = "http://localhost:1234"
        Log.i(TAG, "开始从本地服务器加载SO: $localServerUrl")
        
        try {
            // 获取设备ABI
            val deviceAbi = getDeviceAbi()
            Log.i(TAG, "设备ABI: $deviceAbi")
            
            // 获取SO包列表
            val soListUrl = "$localServerUrl/api/so-packages"
            val soPackages = fetchSoPackageListSync(soListUrl)
            
            if (soPackages.isEmpty()) {
                Log.w(TAG, "本地服务器没有可用的SO包")
                return false
            }
            
            val appVersion = getAppVersion(context)
            
            // 查找兼容的Flutter SO包（根据设备架构）
            val flutterPackage = findCompatiblePackage(soPackages, "libflutter", appVersion, deviceAbi)
            val appPackage = findCompatiblePackage(soPackages, "libapp", appVersion, deviceAbi)
            
            if (flutterPackage == null) {
                Log.w(TAG, "未找到兼容的Flutter SO包版本（架构: $deviceAbi）")
                return false
            }
            
            if (appPackage == null) {
                Log.w(TAG, "未找到兼容的App SO包版本（架构: $deviceAbi）")
                return false
            }
            
            Log.i(TAG, "找到Flutter SO包: ${flutterPackage.fileName}, 版本: ${flutterPackage.version}, 架构: ${flutterPackage.abi}")
            Log.i(TAG, "找到App SO包: ${appPackage.fileName}, 版本: ${appPackage.version}, 架构: ${appPackage.abi}")
            
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
     * 从assets配置加载SO文件
     */
    private suspend fun loadFromAssetsConfig(context: Context) {
        try {
            // 获取设备ABI
            val deviceAbi = getDeviceAbi()
            Log.i(TAG, "设备ABI: $deviceAbi")
            
            // 加载Flutter SO配置
            val flutterSoConfig = try {
                context.assets.open("flutterso.json").readBytes().decodeToString()
            } catch (e: Exception) {
                Log.w(TAG, "无法加载flutterso.json: ${e.message}")
                null
            }
            
            // 加载App SO配置
            val appSoConfig = try {
                context.assets.open("appso.json").readBytes().decodeToString()
            } catch (e: Exception) {
                Log.w(TAG, "无法加载appso.json: ${e.message}")
                null
            }
            
            // 如果新配置文件不存在，尝试使用旧版配置
            if (flutterSoConfig == null && appSoConfig == null) {
                Log.w(TAG, "未找到任何配置文件，尝试使用旧版配置")
                loadFromLegacyConfig(context)
                return
            }
            
            var flutterConfig: FlutterConfig? = null
            var appConfig: FlutterConfig? = null
            
            // 解析Flutter SO配置
            if (flutterSoConfig != null) {
                flutterConfig = Gson().fromJsonProxy(flutterSoConfig, FlutterConfig::class.java)
                if (flutterConfig == null) {
                    Log.e(TAG, "解析flutterso.json失败")
                }
            }
            
            // 解析App SO配置
            if (appSoConfig != null) {
                appConfig = Gson().fromJsonProxy(appSoConfig, FlutterConfig::class.java)
                if (appConfig == null) {
                    Log.e(TAG, "解析appso.json失败")
                }
            }
            
            // 检查是否至少有一个配置可用
            if (flutterConfig == null && appConfig == null) {
                Log.e(TAG, "所有配置文件解析失败")
                return
            }
            
            // 检查版本兼容性
            val appVersion = getAppVersion(context)
            
            var flutterAbiConfig: AbiConfigInfo? = null
            var appAbiConfig: AbiConfigInfo? = null
            
            // 处理Flutter SO配置
            if (flutterConfig != null) {
                if (!SoPackageManager.isVersionCompatible(
                        flutterConfig.flutterSoVersion ?: "",
                        appVersion,
                        flutterConfig.minAppVersion,
                        flutterConfig.maxAppVersion
                    )) {
                    Log.w(TAG, "Flutter SO版本不兼容，当前App版本: $appVersion")
                } else {
                    flutterAbiConfig = getAbiConfig(flutterConfig, deviceAbi)
                    if (flutterAbiConfig == null) {
                        Log.w(TAG, "未找到Flutter SO的设备架构($deviceAbi)配置")
                    }
                }
            }
            
            // 处理App SO配置
            if (appConfig != null) {
                if (!SoPackageManager.isVersionCompatible(
                        appConfig.appSoVersion ?: "",
                        appVersion,
                        appConfig.minAppVersion,
                        appConfig.maxAppVersion
                    )) {
                    Log.w(TAG, "App SO版本不兼容，当前App版本: $appVersion")
                } else {
                    appAbiConfig = getAbiConfig(appConfig, deviceAbi)
                    if (appAbiConfig == null) {
                        Log.w(TAG, "未找到App SO的设备架构($deviceAbi)配置")
                    }
                }
            }
            
            // 如果都没有找到合适的配置，尝试旧版配置
            if (flutterAbiConfig == null && appAbiConfig == null) {
                Log.w(TAG, "未找到任何兼容的SO配置，尝试使用旧版配置")
                loadFromLegacyConfig(context)
                return
            }
            
            Log.i(TAG, "开始从assets配置下载SO文件")
            if (flutterAbiConfig != null) {
                Log.i(TAG, "Flutter SO URL: ${flutterAbiConfig.url}")
            }
            if (appAbiConfig != null) {
                Log.i(TAG, "App SO URL: ${appAbiConfig.url}")
            }
            
            val libFlutterSOSaveDir = context.getDir("libflutter", Context.MODE_PRIVATE)
            var libFlutterResult: String? = null
            var libAppResult: String? = null
            
            // 下载Flutter SO（如果配置存在）
            if (flutterAbiConfig != null) {
                libFlutterResult = downloadDynamicSO(context, DownloadConfig(
                    flutterAbiConfig.url,
                    libFlutterSOSaveDir.absolutePath
                ).apply {
                    fileName = "libflutter.so"
                    expectedMd5 = flutterAbiConfig.md5
                    expectedSize = flutterAbiConfig.size
                })
            }
            
            // 下载App SO（如果配置存在）
            if (appAbiConfig != null) {
                libAppResult = downloadDynamicSO(context, DownloadConfig(
                    appAbiConfig.url,
                    context.getDir("libapp", Context.MODE_PRIVATE).absolutePath
                ).apply {
                    fileName = "libapp.so"
                    expectedMd5 = appAbiConfig.md5
                    expectedSize = appAbiConfig.size
                })
            }
            
            // 检查下载结果并初始化Flutter
            val hasFlutter = !TextUtils.isEmpty(libFlutterResult)
            val hasApp = !TextUtils.isEmpty(libAppResult)
            
            if (hasFlutter || hasApp) {
                Log.i(TAG, "SO文件下载完成 - Flutter: $hasFlutter, App: $hasApp，开始初始化Flutter")
                loadAndInitFlutter(context, libFlutterSOSaveDir, libAppResult ?: "")
            } else {
                Log.e(TAG, "所有SO文件下载失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "从assets配置加载SO文件失败", e)
        }
    }
    
    /**
     * 从旧版配置加载SO文件
     */
    private suspend fun loadFromLegacyConfig(context: Context) {
        try {
            // 尝试加载旧版统一配置文件
            val legacyConfigContent = try {
                context.assets.open("flutter_so_config.json").readBytes().decodeToString()
            } catch (e: Exception) {
                Log.w(TAG, "无法加载旧版配置文件flutter_so_config.json: ${e.message}")
                null
            }
            
            if (legacyConfigContent == null) {
                Log.e(TAG, "未找到任何可用的配置文件")
                return
            }
            
            val flutterConfig = Gson().fromJsonProxy(legacyConfigContent, FlutterConfig::class.java)
            if (flutterConfig == null) {
                Log.e(TAG, "解析旧版配置文件失败")
                return
            }
            
            // 检查版本兼容性
            val appVersion = getAppVersion(context)
            if (!SoPackageManager.isVersionCompatible(
                    flutterConfig.flutterSoVersion ?: "",
                    appVersion,
                    flutterConfig.minAppVersion,
                    flutterConfig.maxAppVersion
                )) {
                Log.w(TAG, "Flutter SO版本不兼容，当前App版本: $appVersion")
                return
            }
            
            Log.i(TAG, "开始从旧版配置下载SO文件")
            Log.i(TAG, "Flutter SO URL: ${flutterConfig.flutterSoUrl}")
            Log.i(TAG, "App SO URL: ${flutterConfig.appSoUrl}")
            
            val libFlutterSOSaveDir = context.getDir("libflutter", Context.MODE_PRIVATE)
            var libFlutterResult: String? = null
            var libAppResult: String? = null
            
            // 下载Flutter SO（如果URL存在）
            if (!flutterConfig.flutterSoUrl.isNullOrEmpty()) {
                libFlutterResult = downloadDynamicSO(context, DownloadConfig(
                    flutterConfig.flutterSoUrl,
                    libFlutterSOSaveDir.absolutePath
                ).apply {
                    fileName = "libflutter.so"
                    expectedMd5 = flutterConfig.flutterSoMd5 ?: ""
                    expectedSize = flutterConfig.flutterSoSize
                })
            }
            
            // 下载App SO（如果URL存在）
            if (!flutterConfig.appSoUrl.isNullOrEmpty()) {
                libAppResult = downloadDynamicSO(context, DownloadConfig(
                    flutterConfig.appSoUrl,
                    context.getDir("libapp", Context.MODE_PRIVATE).absolutePath
                ).apply {
                    fileName = "libapp.so"
                    expectedMd5 = flutterConfig.appSoMd5 ?: ""
                    expectedSize = flutterConfig.appSoSize
                })
            }
            
            // 检查下载结果并初始化Flutter
            val hasFlutter = !TextUtils.isEmpty(libFlutterResult)
            val hasApp = !TextUtils.isEmpty(libAppResult)
            
            if (hasFlutter || hasApp) {
                Log.i(TAG, "SO文件下载完成 - Flutter: $hasFlutter, App: $hasApp，开始初始化Flutter")
                loadAndInitFlutter(context, libFlutterSOSaveDir, libAppResult ?: "")
            } else {
                Log.e(TAG, "所有SO文件下载失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "从旧版配置加载SO文件失败", e)
        }
    }

    /**
     * 获取设备ABI
     */
    private fun getDeviceAbi(): String {
        return try {
            val supportedAbis = android.os.Build.SUPPORTED_ABIS
            Log.i(TAG, "设备支持的ABI列表: ${supportedAbis.joinToString(", ")}")
            
            if (supportedAbis.isNotEmpty()) {
                val primaryAbi = supportedAbis[0]
                Log.i(TAG, "选择主要ABI: $primaryAbi")
                
                // 确保返回的ABI是我们支持的架构之一
                when (primaryAbi) {
                    "arm64-v8a", "armeabi-v7a" -> {
                        Log.i(TAG, "使用支持的ABI: $primaryAbi")
                        primaryAbi
                    }
                    else -> {
                        // 如果主要ABI不在支持列表中，查找支持的ABI
                        val supportedAbi = supportedAbis.find { it in listOf("arm64-v8a", "armeabi-v7a") }
                        if (supportedAbi != null) {
                            Log.i(TAG, "主要ABI不支持，使用备选ABI: $supportedAbi")
                            supportedAbi
                        } else {
                            Log.w(TAG, "未找到支持的ABI，使用默认值: arm64-v8a")
                            "arm64-v8a"
                        }
                    }
                }
            } else {
                Log.w(TAG, "设备ABI列表为空，使用默认值: arm64-v8a")
                "arm64-v8a"
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取设备ABI失败", e)
            "arm64-v8a" // 默认值
        }
    }
    
    /**
     * 获取指定架构的SO配置
     */
    private fun getAbiConfig(config: FlutterConfig, abi: String): AbiConfigInfo? {
        // 首先尝试获取新版多架构配置
        val abiConfig = when (abi) {
            "arm64-v8a" -> config.arm64_v8a
            "armeabi-v7a" -> config.armeabi_v7a
            else -> null
        }
        
        if (abiConfig != null) {
            return AbiConfigInfo(
                md5 = abiConfig.md5,
                size = abiConfig.size,
                url = abiConfig.url
            )
        }
        
        // 如果新版配置不存在，尝试使用旧版配置
        // 检查是否有Flutter SO的旧版配置
        if (!config.flutterSoUrl.isNullOrEmpty()) {
            return AbiConfigInfo(
                md5 = config.flutterSoMd5 ?: "",
                size = config.flutterSoSize,
                url = config.flutterSoUrl
            )
        }
        
        // 检查是否有App SO的旧版配置
        if (!config.appSoUrl.isNullOrEmpty()) {
            return AbiConfigInfo(
                md5 = config.appSoMd5 ?: "",
                size = config.appSoSize,
                url = config.appSoUrl
            )
        }
        
        return null
    }
    
    /**
     * 架构配置信息
     */
    data class AbiConfigInfo(
        val md5: String,
        val size: Long,
        val url: String
    )

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
        appVersion: String,
        deviceAbi: String
    ): SoPackageInfo? {
        Log.i(TAG, "查找兼容的SO包: 前缀=$packagePrefix, 设备架构=$deviceAbi")
        
        // 首先过滤出匹配前缀和架构的包
        val compatiblePackages = packages.filter { soPackage ->
            val matchesPrefix = soPackage.fileName.startsWith(packagePrefix)
            val matchesAbi = soPackage.abi == deviceAbi || soPackage.fileName.contains(deviceAbi)
            
            Log.d(TAG, "检查包: ${soPackage.fileName}, 前缀匹配: $matchesPrefix, 架构匹配: $matchesAbi (包架构: ${soPackage.abi})")
            
            matchesPrefix && matchesAbi
        }
        
        if (compatiblePackages.isEmpty()) {
            Log.w(TAG, "未找到匹配的SO包，尝试查找所有架构的包")
            // 如果没有找到匹配架构的包，尝试查找所有匹配前缀的包
            val allMatchingPackages = packages.filter { it.fileName.startsWith(packagePrefix) }
            Log.i(TAG, "找到 ${allMatchingPackages.size} 个匹配前缀的包")
            allMatchingPackages.forEach { pkg ->
                Log.i(TAG, "可用包: ${pkg.fileName}, 架构: ${pkg.abi}")
            }
            return null
        }
        
        // 从兼容的包中选择版本最高的
        // 从兼容的包中选择版本最高的
        val selectedPackage = compatiblePackages.maxByOrNull { parseVersionCode(it.version) }
        
        if (selectedPackage != null) {
            Log.i(TAG, "选择的SO包: ${selectedPackage.fileName}, 版本: ${selectedPackage.version}, 架构: ${selectedPackage.abi}")
        }
        
        return selectedPackage
    }
    
    // 重载方法，保持向后兼容性
    private fun findCompatiblePackage(
        packages: List<SoPackageInfo>,
        packagePrefix: String,
        appVersion: String
    ): SoPackageInfo? {
        val deviceAbi = getDeviceAbi()
        return findCompatiblePackage(packages, packagePrefix, appVersion, deviceAbi)
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