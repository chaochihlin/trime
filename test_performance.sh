#!/bin/bash

# Trime KeyboardView 效能優化測試腳本
# 此腳本監控 logcat 輸出以追蹤效能改善

echo "=== Trime 效能測試腳本 ==="
echo "正在監控 KeyboardView 效能優化..."
echo

# 檢查裝置是否已連接
if ! adb devices | grep -q "device$"; then
    echo "❌ 未找到 Android 裝置。請連接裝置並啟用 USB 除錯。"
    exit 1
fi

echo "📱 裝置已連接。開始效能監控..."
echo

# 清除 logcat 緩衝區
adb logcat -c

echo "🔍 正在監控效能記錄。按 Ctrl+C 停止。"
echo "📊 注意以下效能指標："
echo "   • 渲染時間 (目標: <30ms)"
echo "   • 記憶體使用模式"
echo "   • 快取命中率"
echo "   • 點陣圖重建次數"
echo

# 開始監控，使用過濾器篩選效能標籤
adb logcat -v time | grep -E "(KeyboardView Performance|SmartBitmapCache|RenderStateCache|MemoryMonitor)" --line-buffered | while read -r line; do
    timestamp=$(echo "$line" | cut -d' ' -f1-2)
    
    # 根據不同訊息類型進行顏色編碼
    if echo "$line" | grep -q "took.*ms"; then
        # 渲染時間測量
        if echo "$line" | grep -q ">30ms threshold"; then
            echo "🔴 $timestamp 慢速渲染: $line"
        else
            echo "🟢 $timestamp 渲染: $line"
        fi
    elif echo "$line" | grep -q "Bitmap recreated"; then
        echo "🔄 $timestamp 點陣圖: $line"
    elif echo "$line" | grep -q "Hit rate"; then
        echo "📈 $timestamp 快取: $line"
    elif echo "$line" | grep -q "Memory pressure"; then
        echo "⚠️  $timestamp 記憶體: $line"
    elif echo "$line" | grep -q "Caches cleared"; then
        echo "🧹 $timestamp 清理: $line"
    else
        echo "ℹ️  $timestamp 資訊: $line"
    fi
done