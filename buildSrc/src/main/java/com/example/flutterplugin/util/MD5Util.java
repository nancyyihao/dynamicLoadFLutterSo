package com.example.flutterplugin.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MD5Util {
    
    /**
     * 计算文件的MD5值
     */
    public static String getFileMD5(File file) {
        if (file == null || !file.exists()) {
            return "";
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            fis.close();
            
            return bytesToHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            LogUtil.log("计算MD5失败: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * 计算字符串的MD5值
     */
    public static String getStringMD5(String input) {
        if (input == null) {
            return "";
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(input.getBytes());
            return bytesToHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            LogUtil.log("计算字符串MD5失败: " + e.getMessage());
            return "";
        }
    }
    
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}