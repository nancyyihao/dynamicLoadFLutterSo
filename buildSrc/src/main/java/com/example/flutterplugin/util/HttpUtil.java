package com.example.flutterplugin.util;

import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HttpUtil {

    private static volatile HttpUtil single;

    public static HttpUtil getInstance() {
        if (single == null) {
            synchronized (HttpUtil.class) {
                if (single == null) {
                    single = new HttpUtil();
                }
            }
        }
        return single;
    }

    private OkHttpClient client;

    private HttpUtil() {
        client = new OkHttpClient.Builder()
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 上传到本地服务器
     * @param file
     * @return
     */
    @Nullable
    public String upload(File file){
        try{
            // 上传到本地Dart服务器
            String uploadUrl = "http://127.0.0.1:1234/api/upload";
            
            MultipartBody multipartBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", file.getName(),
                            RequestBody.create(MediaType.parse("application/zip"), file))
                    .build();

            Request request = new Request.Builder()
                    .url(uploadUrl)
                    .post(multipartBody)
                    .build();

            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                LogUtil.log("上传失败，响应码: " + response.code() + ", 消息: " + response.message());
                return null;
            }
            
            if (response.body() == null) {
                LogUtil.log("上传失败，响应体为空");
                return null;
            }
            
            String resultJson = response.body().string();
            LogUtil.log("上传结果: " + resultJson);
            
            JSONObject jsonObject = new JSONObject(resultJson);
            boolean success = jsonObject.optBoolean("success", false);
            if (success) {
                String filename = jsonObject.optString("filename", file.getName());
                return "http://127.0.0.1:1234/api/download/" + filename;
            }
            
        } catch (Exception e) {
            LogUtil.log("上传到本地服务器失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 校验是否已上传
     * @return
     */
    @Nullable
    public String check(SoType type, String sdkVersion){
        //TODO: 自己实现版本校验
        return null;
    }
}
