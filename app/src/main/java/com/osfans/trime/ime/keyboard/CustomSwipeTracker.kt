/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.view.MotionEvent

/**
 * 自定義滑動追蹤器
 *
 * 追蹤觸摸事件的滑動軌跡，計算滑動速度和方向。
 * 維持最近的觸摸點歷史記錄，用於精確計算滑動參數。
 */
class CustomSwipeTracker {
    /** 過去觸摸點的 X 座標記錄 */
    private val mPastX = FloatArray(NUM_PAST)

    /** 過去觸摸點的 Y 座標記錄 */
    private val mPastY = FloatArray(NUM_PAST)

    /** 過去觸摸點的時間戳記錄 */
    private val mPastTime = LongArray(NUM_PAST)

    /** Y 軸方向的滑動速度 */
    var yVelocity = 0f

    /** X 軸方向的滑動速度 */
    var xVelocity = 0f

    /**
     * 清除追蹤記錄
     *
     * 重設所有歷史記錄，為下一次追蹤做準備。
     */
    fun clear() {
        mPastTime[0] = 0
    }

    /**
     * 添加觸摸事件的移動記錄
     *
     * 將觸摸事件中的所有歷史位置和當前位置加入追蹤記錄。
     *
     * @param ev 觸摸事件
     */
    fun addMovement(ev: MotionEvent) {
        for (i in 0 until ev.historySize) {
            addPoint(ev.getHistoricalX(i), ev.getHistoricalY(i), ev.getHistoricalEventTime(i))
        }
        addPoint(ev.x, ev.y, ev.eventTime)
    }

    /**
     * 添加單個觸摸點記錄
     *
     * 將新的觸摸點加入記錄，自動管理陣列大小和過期資料。
     *
     * @param x X 座標
     * @param y Y 座標
     * @param time 時間戳
     */
    private fun addPoint(
        x: Float,
        y: Float,
        time: Long,
    ) {
        var drop = -1
        val pastTime = mPastTime
        var i = 0
        while (i < NUM_PAST) {
            if (pastTime[i] == 0L) {
                break
            } else if (pastTime[i] < time - LONGEST_PAST_TIME) {
                drop = i
            }
            i++
        }
        if (i == NUM_PAST && drop < 0) {
            drop = 0
        }
        if (drop == i) drop--
        val pastX = mPastX
        val pastY = mPastY
        if (drop >= 0) {
            val start = drop + 1
            val count = NUM_PAST - drop - 1
            System.arraycopy(pastX, start, pastX, 0, count)
            System.arraycopy(pastY, start, pastY, 0, count)
            System.arraycopy(pastTime, start, pastTime, 0, count)
            i -= drop + 1
        }
        pastX[i] = x
        pastY[i] = y
        pastTime[i] = time
        i++
        if (i < NUM_PAST) {
            pastTime[i] = 0
        }
    }

    /**
     * 計算當前滑動速度
     *
     * 根據過去的觸摸點記錄計算 X 和 Y 軸的滑動速度。
     *
     * @param units 速度單位，通常為 1000 表示毎秒像素
     */
    fun computeCurrentVelocity(units: Int) {
        val oldestX = mPastX[0]
        val oldestY = mPastY[0]
        val oldestTime = mPastTime[0]
        var accumX = 0f
        var accumY = 0f
        val n = mPastTime.indexOfFirst { it == 0L }.coerceAtMost(NUM_PAST)
        for (i in 1 until n) {
            val dur = mPastTime[i] - oldestTime
            if (dur == 0L) continue
            val distX = mPastX[i] - oldestX
            val velX = distX / dur * units // pixels/frame.
            accumX += velX
            val distY = mPastY[i] - oldestY
            val velY = distY / dur * units // pixels/frame.
            accumY += velY
        }
        xVelocity = accumX.coerceIn(Float.MIN_VALUE, Float.MAX_VALUE)
        yVelocity = accumY.coerceIn(Float.MIN_VALUE, Float.MAX_VALUE)
    }

    companion object {
        /** 保存的歷史觸摸點數量 */
        const val NUM_PAST = 4

        /** 最長的歷史時間範圍（毫秒） */
        const val LONGEST_PAST_TIME = 200
    }
}
