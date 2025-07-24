package com.example.flutterdynamic

import android.content.Context
import android.util.Log
import com.example.flutterdynamic.mode.SoPackageInfo
import com.example.flutterdynamic.util.MD5Util
import com.google.gson.Gson
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * SO包管理器
 * 负责SO包的解压、验证和管理
 */
object SoPackageManager {
    
    private const val TAG = "SoPackageManager"
    private const val PACKAGE_INFO_FILE = "package_info.json"
    
    /**
     * 解压并验证SO包
     */
    fun extractAndVerifySoPackage(
        context: Context,
        zipFile: File,
        extractDir: File
    ): SoPackageInfo? {
        try {
            // 确保解压目录存在
            if (!extractDir.exists()) {
                extractDir.mkdirs()
            }
            
            // 解压ZIP文件
            val extractedFiles = extractZipFile(zipFile, extractDir)
            if (extractedFiles.isEmpty()) {
                Log.e(TAG, "ZIP文件解压失败或为空")
                return null
            }
            
            // 查找包信息文件
            val packageInfoFile = File(extractDir, PACKAGE_INFO_FILE)
            if (!packageInfoFile.exists()) {
                Log.e(TAG, "包信息文件不存在: $PACKAGE_INFO_FILE")
                return null
            }
            
            // 解析包信息
            val packageInfoJson = packageInfoFile.readText()
            val packageInfo = try {
                Gson().fromJson(packageInfoJson, SoPackageInfo::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "解析包信息失败", e)
                return null
            }
            
            // 查找SO文件
            val soFile = File(extractDir, packageInfo.fileName)
            if (!soFile.exists()) {
                Log.e(TAG, "SO文件不存在: ${packageInfo.fileName}")
                return null
            }
            
            // 验证SO文件
            val actualMd5 = MD5Util.getFileMD5(soFile)
            val actualSize = soFile.length()
            
            if (actualMd5 != packageInfo.md5) {
                Log.e(TAG, "SO文件MD5校验失败: 期望=${packageInfo.md5}, 实际=$actualMd5")
                soFile.delete()
                return null
            }
            
            if (actualSize != packageInfo.size) {
                Log.e(TAG, "SO文件大小校验失败: 期望=${packageInfo.size}, 实际=$actualSize")
                soFile.delete()
                return null
            }
            
            Log.i(TAG, "SO包验证成功: ${packageInfo.fileName}")
            return packageInfo
            
        } catch (e: Exception) {
            Log.e(TAG, "解压和验证SO包失败", e)
            return null
        }
    }
    
    /**
     * 解压ZIP文件
     */
    private fun extractZipFile(zipFile: File, extractDir: File): List<File> {
        val extractedFiles = mutableListOf<File>()
        
        try {
            ZipInputStream(FileInputStream(zipFile)).use { zipIn ->
                var entry = zipIn.nextEntry
                
                while (entry != null) {
                    val extractedFile = File(extractDir, entry.name)
                    
                    // 安全检查：防止路径遍历攻击
                    if (!extractedFile.canonicalPath.startsWith(extractDir.canonicalPath)) {
                        Log.w(TAG, "跳过不安全的路径: ${entry.name}")
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                        continue
                    }
                    
                    if (entry.isDirectory) {
                        extractedFile.mkdirs()
                    } else {
                        // 确保父目录存在
                        extractedFile.parentFile?.mkdirs()
                        
                        // 使用缓冲区安全解压文件
                        FileOutputStream(extractedFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (zipIn.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                            }
                        }
                        
                        extractedFiles.add(extractedFile)
                        Log.d(TAG, "解压文件: ${entry.name}, 大小: ${extractedFile.length()} bytes")
                    }
                    
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
            
            Log.i(TAG, "ZIP文件解压完成，共解压 ${extractedFiles.size} 个文件")
            
        } catch (e: Exception) {
            Log.e(TAG, "解压ZIP文件失败", e)
            // 清理已解压的文件
            extractedFiles.forEach { 
                try {
                    it.delete()
                } catch (ex: Exception) {
                    Log.w(TAG, "清理文件失败: ${it.name}")
                }
            }
            return emptyList()
        }
        
        return extractedFiles
    }
    
    /**
     * 检查版本兼容性
     */
    fun isVersionCompatible(
        soVersion: String,
        appVersion: String,
        minAppVersion: String,
        maxAppVersion: String
    ): Boolean {
        return try {
            val appVersionCode = parseVersionCode(appVersion)
            val minVersionCode = parseVersionCode(minAppVersion)
            val maxVersionCode = parseVersionCode(maxAppVersion)
            
            appVersionCode in minVersionCode..maxVersionCode
        } catch (e: Exception) {
            Log.e(TAG, "版本兼容性检查失败", e)
            false
        }
    }
    
    /**
     * 解析版本号为数字代码
     */
    private fun parseVersionCode(version: String): Int {
        return try {
            version.replace(".", "").toInt()
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * 清理SO包缓存
     */
    fun clearSoCache(context: Context) {
        try {
            val cacheDir = context.cacheDir
            val flutterDir = File(cacheDir, "server_flutter")
            val appDir = File(cacheDir, "server_app")
            
            if (flutterDir.exists()) {
                flutterDir.deleteRecursively()
                Log.i(TAG, "清理Flutter SO缓存")
            }
            
            if (appDir.exists()) {
                appDir.deleteRecursively()
                Log.i(TAG, "清理App SO缓存")
            }
            
            // 清理临时文件
            cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("temp_lib")) {
                    file.delete()
                    Log.d(TAG, "清理临时文件: ${file.name}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "清理SO缓存失败", e)
        }
    }
    
    /**
     * 获取SO包信息
     */
    fun getSoPackageInfo(extractDir: File): SoPackageInfo? {
        return try {
            val packageInfoFile = File(extractDir, PACKAGE_INFO_FILE)
            if (!packageInfoFile.exists()) {
                return null
            }
            
            val packageInfoJson = packageInfoFile.readText()
            Gson().fromJson(packageInfoJson, SoPackageInfo::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "获取SO包信息失败", e)
            null
        }
    }
}