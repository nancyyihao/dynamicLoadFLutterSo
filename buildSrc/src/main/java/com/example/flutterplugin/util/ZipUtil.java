package com.example.flutterplugin.util;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipUtil {
    
    private static final String PACKAGE_INFO_FILE = "package_info.json";
    
    /**
     * 创建SO包（ZIP格式）
     */
    public static File createSoPackage(File soFile, String version, File outputZipFile, String packageName) {
        if (soFile == null || !soFile.exists()) {
            LogUtil.log("SO文件不存在: " + (soFile != null ? soFile.getAbsolutePath() : "null"));
            return null;
        }
        
        try {
            // 确保输出目录存在
            File parentDir = outputZipFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            // 创建包信息
            Map<String, Object> packageInfo = new HashMap<>();
            packageInfo.put("version", version);
            packageInfo.put("md5", MD5Util.getFileMD5(soFile));
            packageInfo.put("size", soFile.length());
            packageInfo.put("fileName", soFile.getName());
            packageInfo.put("packageName", packageName);
            packageInfo.put("createTime", System.currentTimeMillis());
            
            // 创建ZIP文件
            ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(outputZipFile));
            
            try {
                // 添加SO文件
                addFileToZip(zipOut, soFile, soFile.getName());
                
                // 添加包信息文件
                String infoJson = new Gson().toJson(packageInfo);
                addStringToZip(zipOut, infoJson, PACKAGE_INFO_FILE);
                
                LogUtil.log("SO包创建成功: " + outputZipFile.getAbsolutePath());
                return outputZipFile;
                
            } finally {
                zipOut.close();
            }
            
        } catch (IOException e) {
            LogUtil.log("创建SO包失败: " + e.getMessage());
            // 清理失败的文件
            if (outputZipFile.exists()) {
                outputZipFile.delete();
            }
            return null;
        }
    }
    
    /**
     * 添加文件到ZIP
     */
    private static void addFileToZip(ZipOutputStream zipOut, File file, String entryName) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        try {
            ZipEntry entry = new ZipEntry(entryName);
            zipOut.putNextEntry(entry);
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                zipOut.write(buffer, 0, bytesRead);
            }
            
            zipOut.closeEntry();
        } finally {
            fis.close();
        }
    }
    
    /**
     * 添加字符串内容到ZIP
     */
    private static void addStringToZip(ZipOutputStream zipOut, String content, String entryName) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zipOut.putNextEntry(entry);
        zipOut.write(content.getBytes("UTF-8"));
        zipOut.closeEntry();
    }
}