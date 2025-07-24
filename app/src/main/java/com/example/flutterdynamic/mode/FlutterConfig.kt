package com.example.flutterdynamic.mode

data class FlutterConfig(
    val flutterSoVersion: String,
    val flutterSoUrl: String,
    val flutterSoMd5: String,
    val flutterSoSize: Long,
    val appSoVersion: String,
    val appSoUrl: String,
    val appSoMd5: String,
    val appSoSize: Long,
    val minAppVersion: String,
    val maxAppVersion: String,
    val targetAbi: List<String> = listOf("arm64-v8a", "armeabi-v7a")
)

data class SoPackageInfo(
    val version: String,
    val md5: String,
    val size: Long,
    val url: String,
    val fileName: String,
    val abi: String
)
