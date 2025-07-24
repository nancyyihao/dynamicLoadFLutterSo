package com.example.flutterdynamic.download

import androidx.annotation.DrawableRes

data class DownloadConfig(var url: String, var path: String) {

    /**
     * 自定义文件名
     */
    var fileName: String? = null

    /**
     * 是否显示系统通知
     */
    var isShowNotification: Boolean = false

    /**
     * 系统通知显示 icon
     */
    @DrawableRes
    var notificationIcon: Int? = null
    
    /**
     * 期望的MD5值，用于文件完整性校验
     */
    var expectedMd5: String = ""
    
    /**
     * 期望的文件大小，用于文件完整性校验
     */
    var expectedSize: Long = 0
}

sealed class DownloadState {
    object UNKNOW : DownloadState() //未知
    object PEND : DownloadState() //等待
    object DOWNLOADING : DownloadState() //下载中
    object PAUSE : DownloadState() //暂停
    object SUCCESS : DownloadState() //成功
    object CANCEL : DownloadState() //取消
    object FAIL : DownloadState() //失败
}