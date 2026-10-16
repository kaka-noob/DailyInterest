/**
 * 本文件提供每日利息应用的 Android 启动入口。
 */
package com.dailyinterest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * 应用的单一 Activity，负责加载本金利息主页。
 */
class MainActivity : ComponentActivity() {

    /**
     * 创建 Activity 并设置 Compose 主界面。
     *
     * @param savedInstanceState Android 保存的页面状态。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DailyInterestApp()
        }
    }
}

