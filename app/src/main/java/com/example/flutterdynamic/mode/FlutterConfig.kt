package com.example.flutterdynamic.mode

/**
 * Flutter SO配置类
 * 统一配置结构
 */
data class FlutterConfig(
    // SO版本信息
    val libflutterVersion: String? = null,
    val libappVersion: String? = null,
    
    // 通用配置字段
    val minAppVersion: String = "1.0.0",
    val maxAppVersion: String = "9.9.9",
    val targetAbi: List<String> = listOf("arm64-v8a", "armeabi-v7a"),
    
    // 多架构配置字段
    val arm64_v8a: AbiConfig? = null,
    val armeabi_v7a: AbiConfig? = null,
    
    // 上传和下载URL
    val uploadUrl: String? = null,
    val downloadUrl: String? = null,
    
    // 兼容旧版配置字段（已废弃，仅用于向后兼容）
    @Deprecated("使用新的统一配置结构")
    val flutterSoVersion: String? = null,
    @Deprecated("使用新的统一配置结构")
    val flutterSoUrl: String? = null,
    @Deprecated("使用新的统一配置结构")
    val flutterSoMd5: String? = null,
    @Deprecated("使用新的统一配置结构")
    val flutterSoSize: Long = 0,
    @Deprecated("使用新的统一配置结构")
    val appSoVersion: String? = null,
    @Deprecated("使用新的统一配置结构")
    val appSoUrl: String? = null,
    @Deprecated("使用新的统一配置结构")
    val appSoMd5: String? = null,
    @Deprecated("使用新的统一配置结构")
    val appSoSize: Long = 0
)

/**
 * 架构特定的配置
 */
data class AbiConfig(
    val url: String,
    val md5: String,
    val size: Long
)

/**
 * SO包信息
 */
data class SoPackageInfo(
    val version: String,
    val md5: String,
    val size: Long,
    val url: String,
    val fileName: String,
    val abi: String
)