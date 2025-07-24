package com.example.flutterdynamic.util

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object MD5Util {
    
    /**
     * 计算文件的MD5值
     */
    fun getFileMD5(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            bytesToHex(digest.digest())
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * 计算字符串的MD5值
     */
    fun getStringMD5(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val bytes = digest.digest(input.toByteArray())
            bytesToHex(bytes)
        } catch (e: Exception) {
            ""
        }
    }
    
    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef"
        val result = StringBuilder(bytes.size * 2)
        bytes.forEach { byte ->
            val i = byte.toInt()
            result.append(hexChars[i shr 4 and 0x0f])
            result.append(hexChars[i and 0x0f])
        }
        return result.toString()
    }
}