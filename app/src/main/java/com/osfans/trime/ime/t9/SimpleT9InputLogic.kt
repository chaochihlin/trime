// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.t9

import com.osfans.trime.daemon.RimeSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 簡化版T9輸入邏輯控制器
 *
 * 整合T9數字映射、RIME引擎和候選詞生成，提供統一的輸入處理接口。
 */
class SimpleT9InputLogic(
    private val rimeSession: RimeSession,
) {
    companion object {
        private const val TAG = "SimpleT9InputLogic"
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    // 當前輸入狀態
    private var currentState = T9InputState()

    // 狀態變更監聽器
    private val stateListeners = mutableListOf<() -> Unit>()

    /**
     * 添加狀態變更監聽器
     */
    fun addStateListener(listener: () -> Unit) {
        stateListeners.add(listener)
    }

    /**
     * 移除狀態變更監聽器
     */
    fun removeStateListener(listener: () -> Unit) {
        stateListeners.remove(listener)
    }

    /**
     * 通知狀態變更
     */
    private fun notifyStateChange() {
        stateListeners.forEach { listener ->
            try {
                listener()
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error in state listener")
            }
        }
    }

    /**
     * 處理數字輸入
     */
    fun processDigit(digit: Int) {
        if (digit !in 0..9) {
            Timber.w("$TAG: Invalid digit: $digit")
            return
        }

        Timber.d("$TAG: 將數字 '$digit' 傳遞給 Rime 引擎")

        // 檢查 RIME 引擎就緒狀態和生命週期
        val isRimeReady = rimeSession.run { isReady }
        Timber.d("$TAG: RIME 引擎就緒狀態: $isRimeReady")

        // 檢查 RIME 生命週期狀態
        val lifecycleState = rimeSession.run { stateFlow.replayCache.lastOrNull() }
        Timber.d("$TAG: RIME 生命週期狀態: $lifecycleState")

        if (!isRimeReady) {
            Timber.w("$TAG: RIME 引擎尚未就緒，無法處理按鍵輸入")
            return
        }

        coroutineScope.launch {
            try {
                Timber.d("$TAG: 準備呼叫 runOnReady...")
                rimeSession.runOnReady {
                    Timber.d("$TAG: ✅ 成功進入 runOnReady 回調")

                    // 檢查 RIME 引擎狀態
                    val currentSchema = selectedSchemaId()
                    Timber.d("$TAG: RIME 狀態 - 當前方案: '$currentSchema'")

                    // 直接將按鍵事件傳遞給 Rime 引擎
                    val keyCode = digit.toString().first().code
                    Timber.d("$TAG: 發送按鍵碼: $keyCode (字符: '${digit.toString().first()}')")

                    val result = processKey(keyCode, 0u)
                    Timber.d("$TAG: processKey 結果: $result")

                    // 檢查處理後的狀態
                    val composition = compositionCached
                    val menu = menuCached
                    Timber.d("$TAG: 處理後狀態 - preedit: '${composition.preedit}', candidates: ${menu.candidates.size}")
                }
                Timber.d("$TAG: runOnReady 呼叫完成")
            } catch (e: Exception) {
                Timber.e(e, "$TAG: 處理數字輸入時發生錯誤")
            }
        }
    }

    /**
     * 刪除最後一個數字
     */
    fun deleteLastDigit() {
        Timber.d("$TAG: 將 BackSpace 傳遞給 Rime 引擎")
        coroutineScope.launch {
            rimeSession.runOnReady {
                // Rime 的 processKey 接受 keysym，BackSpace 對應的 keysym 是 0xff08
                processKey(0xff08, 0u)
                // RIME 引擎會透過 messageFlow 自動通知 UI 更新
            }
        }
    }

    /**
     * 選擇候選詞
     */
    fun selectCandidate(index: Int): String? {
        if (index < 0 || index >= currentState.candidates.size) {
            Timber.w("$TAG: Invalid candidate index: $index")
            return null
        }

        val selectedCandidate = currentState.candidates[index]
        Timber.d("$TAG: Selected candidate: $selectedCandidate at index $index")

        // 提交候選詞
        commitCandidate(selectedCandidate)

        // 重置狀態
        reset()

        return selectedCandidate
    }

    /**
     * 重置輸入狀態
     */
    fun reset() {
        Timber.d("$TAG: Resetting input state")

        currentState = T9InputState()

        // 清空RIME組合
        coroutineScope.launch {
            rimeSession.runOnReady {
                clearComposition()
            }
        }

        notifyStateChange()
    }

    /**
     * 獲取當前狀態
     */
    fun getCurrentState(): T9InputState = currentState

    /**
     * 異步更新候選詞
     */
    private fun updateCandidatesAsync() {
        if (!currentState.hasInput) {
            notifyStateChange()
            return
        }

        coroutineScope.launch {
            try {
                // 1. 生成注音組合
                val zhuyinCombinations =
                    T9ZhuyinMapper.mapToZhuyinCombinations(
                        currentState.digitSequence,
                    )

                Timber.d("$TAG: Generated ${zhuyinCombinations.size} zhuyin combinations for '${currentState.digitSequence}'")

                if (zhuyinCombinations.isEmpty()) {
                    // 沒有有效的注音組合
                    currentState =
                        currentState.updateCandidatesAndZhuyin(
                            emptyList(),
                            emptyList(),
                        )
                    notifyStateChange()
                    return@launch
                }

                // 2. 從RIME獲取候選詞
                val candidates =
                    T9RimeHelper.getCandidatesFromRime(
                        rimeSession,
                        zhuyinCombinations,
                    )

                Timber.d("$TAG: Got ${candidates.size} candidates from RIME")

                // 3. 更新狀態
                currentState =
                    currentState.updateCandidatesAndZhuyin(
                        candidates,
                        zhuyinCombinations,
                    )

                notifyStateChange()
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error updating candidates")

                // 發生錯誤時清空候選詞
                currentState =
                    currentState.updateCandidatesAndZhuyin(
                        emptyList(),
                        emptyList(),
                    )
                notifyStateChange()
            }
        }
    }

    /**
     * 提交候選詞到輸入目標
     */
    private fun commitCandidate(candidate: String) {
        try {
            // 清空RIME組合
            coroutineScope.launch {
                rimeSession.runOnReady {
                    clearComposition()
                }
            }

            Timber.d("$TAG: Committed candidate: $candidate")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error committing candidate: $candidate")
        }
    }

    /**
     * 檢查是否有輸入
     */
    fun hasInput(): Boolean = currentState.hasInput

    /**
     * 檢查是否有候選詞
     */
    fun hasCandidates(): Boolean = currentState.hasCandidates

    /**
     * 獲取當前數字序列
     */
    fun getDigitSequence(): String = currentState.digitSequence

    /**
     * 獲取候選詞列表
     */
    fun getCandidates(): List<String> = currentState.candidates

    /**
     * 獲取注音組合列表 (用於調試)
     */
    fun getZhuyinCombinations(): List<String> = currentState.zhuyinCombinations
}
