/*********************************************************************************
 * FileName: LeggedJoystickApplication.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-15
 * Description: 应用程序主类，用于初始化Timber日志
 * Others:
 *********************************************************************************/

/*********************************************************************************
 * FileName: LeggedJoystickApplication.kt
 * Author: helywin <jiang770882022@hotmail.com>
 * Version: 0.0.1
 * Date: 2025-09-15
 * Description: 应用程序入口点
 * Others: 使用Timber作为日志框架
 *********************************************************************************/

package com.helywin.leggedjoystick

import android.app.Application
import timber.log.Timber

class LeggedJoystickApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 初始化Timber日志框架
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        Timber.i("LeggedJoystick应用程序已启动")
    }
}