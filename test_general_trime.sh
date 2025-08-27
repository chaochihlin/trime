#!/bin/bash

echo "=== Trime 一般效能監控 ==="
echo "監控所有 Trime 相關記錄以了解目前行為..."
echo

# 檢查裝置是否已連接
if ! adb devices | grep -q "device$"; then
    echo "❌ 未找到 Android 裝置。請連接裝置並啟用 USB 除錯。"
    exit 1
fi

echo "📱 裝置已連接。開始 Trime 監控..."
echo "🔍 請立即使用 Trime 鍵盤以觀察其活動..."
echo

# 清除 logcat 緩衝區
adb logcat -c

# 監控所有 Trime 相關記錄
adb logcat -v time | grep -i "trime" --line-buffered | while read -r line; do
    timestamp=$(echo "$line" | cut -d' ' -f1-2)
    
    if echo "$line" | grep -qi "keyboard"; then
        echo "⌨️  $timestamp 鍵盤: $line"
    elif echo "$line" | grep -qi "performance\|time\|ms"; then
        echo "⚡ $timestamp 效能: $line"
    elif echo "$line" | grep -qi "memory\|gc"; then
        echo "🧠 $timestamp 記憶體: $line"
    elif echo "$line" | grep -qi "error\|exception"; then
        echo "❌ $timestamp 錯誤: $line"
    else
        echo "ℹ️  $timestamp 資訊: $line"
    fi
done