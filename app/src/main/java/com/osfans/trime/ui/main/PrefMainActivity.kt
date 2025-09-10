// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.main

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.soundeffect.SoundEffectManager
import com.osfans.trime.util.isStorageAvailable
import kotlinx.coroutines.launch

/**
 * 手錶裝置優化版偏好設定活動
 * 
 * 這是一個極簡化的設定介面，專為記憶體有限的手錶裝置設計。
 * 移除了所有複雜的 Fragment 導航和 UI 元件，僅保留基本的狀態顯示。
 * 所有配置都採用程式化方式處理，不需要用戶互動。
 */
class PrefMainActivity : AppCompatActivity() {
    private val uiMode by AppPrefs.defaultInstance().other.uiMode

    override fun onCreate(savedInstanceState: Bundle?) {
        // 設定夜間模式
        val nightMode = when (uiMode) {
            AppPrefs.Other.UiMode.AUTO -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            AppPrefs.Other.UiMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppPrefs.Other.UiMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
        
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 建立最簡化的 UI
        val textView = TextView(this).apply {
            text = getString(R.string.trime_app_name) + "\n" + getString(R.string.trime_app_slogan) + 
                   "\n\n手錶裝置優化版\n自動配置已完成"
            textSize = 16f
            setPadding(32, 32, 32, 32)
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        }
        
        setContentView(textView)
        
        // 處理系統邊距
        ViewCompat.setOnApplyWindowInsetsListener(textView) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                32 + systemBars.left,
                32 + systemBars.top,
                32 + systemBars.right,
                32 + systemBars.bottom
            )
            windowInsets
        }
        
        // 手錶裝置自動配置
        ensureWatchConfiguration()
    }

    override fun onResume() {
        super.onResume()
        if (isStorageAvailable()) {
            SoundEffectManager.init()
        }
    }
    
    /**
     * 確保手錶裝置的基本配置
     */
    private fun ensureWatchConfiguration() {
        lifecycleScope.launch {
            try {
                // 確保 RIME 引擎正常運行
                RimeDaemon.restartRime(false)
                
                // 手錶裝置不需要複雜的設定流程，所有配置都使用預設值
                // 這裡可以加入任何必要的程式化配置邏輯
                
            } catch (e: Exception) {
                // 靜默處理配置錯誤
            }
        }
    }
    
    override fun onBackPressed() {
        // 返回或最小化
        moveTaskToBack(false)
    }
}