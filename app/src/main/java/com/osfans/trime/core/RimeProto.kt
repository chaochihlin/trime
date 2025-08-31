// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.core

/**
 * RIME 協議資料模型類別
 *
 * 定義與 RIME 輸入法引擎交互的資料結構，包含輸入狀態、候選詞選單、
 * 組合輸入和狀態資訊等核心資料模型。這些資料類別用於在 JNI 層與
 * Kotlin 層之間傳遞輸入法引擎的狀態和數據。
 *
 * @see RimeSession
 * @see RimeDaemon
 */
class RimeProto {
    /**
     * 提交文本資料類別
     *
     * 表示要提交到應用程序的最終文本內容
     *
     * @param text 要提交的文本內容，可能為空
     */
    data class Commit(
        val text: String?,
    )

    /**
     * 候選詞資料類別
     *
     * 表示輸入法引擎提供的單個候選詞項目
     *
     * @param text 候選詞的文本內容
     * @param comment 候選詞的註釋或説明，可能為空
     * @param label 候選詞的顯示標籤（通常為數字或字母）
     */
    data class Candidate(
        val text: String,
        val comment: String?,
        val label: String,
    )

    /**
     * 輸入上下文資料類別
     *
     * 包含當前輸入法的完整狀態資訊，包括組合輸入、候選詞選單等
     *
     * @param composition 組合輸入狀態
     * @param menu 候選詞選單
     * @param input 原始輸入字串
     * @param _caretPos 私有的游標位置（內部使用）
     */
    data class Context(
        val composition: Composition,
        val menu: Menu,
        val input: String,
        private val _caretPos: Int,
    ) {
        /**
         * 游標位置
         *
         * 與 [Composition.cursorPos] 相同，直接指向組合輸入中的游標位置
         */
        val caretPos = composition.cursorPos

        /**
         * 組合輸入資料類別
         *
         * 表示當前正在編輯的文本組合狀態，包括預編輯文本、游標位置等
         *
         * @param _length 私有的長度值（以位元組為單位）
         * @param _cursorPos 私有的游標位置（以位元組為單位）
         * @param _selStart 私有的選取開始位置（以位元組為單位）
         * @param _selEnd 私有的選取結束位置（以位元組為單位）
         * @param preedit 預編輯文本內容
         * @param commitTextPreview 提交文本預覽
         */
        data class Composition(
            private val _length: Int = 0,
            private val _cursorPos: Int = 0,
            private val _selStart: Int = 0,
            private val _selEnd: Int = 0,
            val preedit: String? = null,
            val commitTextPreview: String? = null,
        ) {
            /**
             * 文本長度（以字符為單位）
             *
             * 實際上可以直接對 [preedit] 使用 [String.length]，但為了完整性
             * 和語義正確性，此處提供基於位元組位置的字符長度計算
             */
            val length: Int = preedit.run { if (isNullOrEmpty()) 0 else String(toByteArray(), 0, _length).length }

            /**
             * 游標位置（以字符為單位）
             *
             * 將內部的位元組游標位置轉換為字符位置
             */
            val cursorPos: Int = preedit.run { if (isNullOrEmpty()) 0 else String(toByteArray(), 0, _cursorPos).length }

            /**
             * 選取開始位置（以字符為單位）
             *
             * 將內部的位元組選取開始位置轉換為字符位置
             */
            val selStart: Int = preedit.run { if (isNullOrEmpty()) 0 else String(toByteArray(), 0, _selStart).length }

            /**
             * 選取結束位置（以字符為單位）
             *
             * 將內部的位元組選取結束位置轉換為字符位置
             */
            val selEnd: Int = preedit.run { if (isNullOrEmpty()) 0 else String(toByteArray(), 0, _selEnd).length }
        }

        /**
         * 候選詞選單資料類別
         *
         * 管理候選詞的分頁顯示和選取操作
         *
         * @param pageSize 每頁候選詞數量
         * @param pageNumber 當前頁碼
         * @param isLastPage 是否為最後一頁
         * @param highlightedCandidateIndex 當前高亮顯示的候選詞索引
         * @param candidates 候選詞陣列
         * @param selectKeys 選取候選詞使用的按鍵字串
         * @param selectLabels 候選詞選取標籤陣列
         */
        data class Menu(
            val pageSize: Int = 0,
            val pageNumber: Int = 0,
            val isLastPage: Boolean = false,
            val highlightedCandidateIndex: Int = 0,
            val candidates: Array<Candidate> = arrayOf(),
            val selectKeys: String? = null,
            val selectLabels: Array<String> = arrayOf(),
        ) {
            /**
             * 檢查兩個 Menu 物件是否相等
             *
             * 由於包含陣列屬性，需要重寫 equals 方法以正確比較陣列內容
             *
             * @param other 要比較的物件
             * @return 兩個物件是否相等
             */
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Menu

                if (pageSize != other.pageSize) return false
                if (pageNumber != other.pageNumber) return false
                if (isLastPage != other.isLastPage) return false
                if (highlightedCandidateIndex != other.highlightedCandidateIndex) return false
                if (!candidates.contentEquals(other.candidates)) return false
                if (selectKeys != other.selectKeys) return false
                if (!selectLabels.contentEquals(other.selectLabels)) return false

                return true
            }

            /**
             * 計算 Menu 物件的雜湊碼
             *
             * 由於包含陣列屬性，需要重寫 hashCode 方法以正確計算陣列的雜湊值
             *
             * @return 物件的雜湊碼值
             */
            override fun hashCode(): Int {
                var result = pageSize
                result = 31 * result + pageNumber
                result = 31 * result + isLastPage.hashCode()
                result = 31 * result + highlightedCandidateIndex
                result = 31 * result + candidates.contentHashCode()
                result = 31 * result + (selectKeys?.hashCode() ?: 0)
                result = 31 * result + selectLabels.contentHashCode()
                return result
            }
        }
    }

    /**
     * 輸入法狀態資料類別
     *
     * 表示當前輸入法引擎的各種狀態資訊
     *
     * @param schemaId 當前輸入方案的識別碼
     * @param schemaName 當前輸入方案的顯示名稱
     * @param isDisabled 輸入法是否被停用
     * @param isComposing 是否正在組合輸入
     * @param isAsciiMode 是否處於 ASCII 模式（英文輸入）
     * @param isFullShape 是否使用全形字符
     * @param isSimplified 是否使用簡體字
     * @param isTraditional 是否使用繁體字
     * @param isAsciiPunch 是否使用 ASCII 標點符號
     */
    data class Status(
        val schemaId: String = "",
        val schemaName: String = "",
        val isDisabled: Boolean = true,
        val isComposing: Boolean = false,
        val isAsciiMode: Boolean = true,
        val isFullShape: Boolean = false,
        val isSimplified: Boolean = false,
        val isTraditional: Boolean = false,
        val isAsciiPunch: Boolean = true,
    )
}
