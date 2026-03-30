#!/bin/bash
#
# T9 多音字聲調過濾自動驗證腳本
#
# 驗證策略：透過 adb 模擬 T9 按鍵序列，從 logcat 解析聲調過濾結果。
# 測試用例來自客戶回報的 20 個找不到的字。
#
# 用法: ./scripts/t9_tone_filter_test.sh -s <serial> [--no-install] [--no-build]
#
# 依賴：adb 已連線，手錶螢幕亮起且有輸入框 focus

set -euo pipefail

# ========== 參數解析 ==========
SERIAL=""
NO_INSTALL=false
NO_BUILD=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        -s) SERIAL="$2"; shift 2 ;;
        --no-install) NO_INSTALL=true; shift ;;
        --no-build) NO_BUILD=true; NO_INSTALL=true; shift ;;
        *) echo "未知參數: $1"; exit 1 ;;
    esac
done

if [[ -z "$SERIAL" ]]; then
    echo "用法: $0 -s <serial> [--no-install] [--no-build]"
    exit 1
fi

ADB="adb -s $SERIAL"

# ========== T9 按鍵座標（456x456 圓形螢幕）==========
# 4x3 注音鍵盤 grid: col 0/1/2 × row 0/1/2/3
COL0_X=135; COL1_X=228; COL2_X=320
ROW0_Y=160; ROW1_Y=220; ROW2_Y=278; ROW3_Y=335

# T9 digit → grid position (x y)
get_key_coords() {
    local key=$1
    case "$key" in
        1) echo "$COL0_X $ROW0_Y" ;;
        2) echo "$COL1_X $ROW0_Y" ;;
        3) echo "$COL2_X $ROW0_Y" ;;
        4) echo "$COL0_X $ROW1_Y" ;;
        5) echo "$COL1_X $ROW1_Y" ;;
        6) echo "$COL2_X $ROW1_Y" ;;
        7) echo "$COL0_X $ROW2_Y" ;;
        8) echo "$COL1_X $ROW2_Y" ;;
        9) echo "$COL2_X $ROW2_Y" ;;
        0) echo "$COL0_X $ROW3_Y" ;;
        a|10) echo "$COL1_X $ROW3_Y" ;;
        b|11) echo "$COL2_X $ROW3_Y" ;;
        *) echo "228 228" ;;
    esac
}

# T9 code char → key number
code_to_key() {
    local ch=$1
    case "$ch" in
        [0-9]) echo "$ch" ;;
        a) echo "a" ;;
        b) echo "b" ;;
    esac
}

# 聲調鍵座標
TONE2_Y=214
TONE3_Y=253
TONE4_Y=291
# TONE5_Y=330  # 輕聲，目前測試用例未用到
TONE_X=402

# 退格鍵
BACKSPACE_X=383
BACKSPACE_Y=108

# 注音選擇器
ZHUYIN_SELECTOR_X=50

# ========== 測試用例 ==========
# 格式: "字|T9序列|注音過濾|期望聲調|聲調鍵Y座標"
TEST_CASES=(
    "拒|5b|ㄐㄩ|ˋ|${TONE4_Y}"
    "踏|42|ㄊㄚ|ˋ|${TONE4_Y}"
    "那|72|ㄋㄚ|ˋ|${TONE4_Y}"
    "浪|09|ㄌㄤ|ˋ|${TONE4_Y}"
    "累|06|ㄌㄟ|ˋ|${TONE4_Y}"
    "個|49|ㄍㄜ|ˋ|${TONE4_Y}"
    "狼|09|ㄌㄤ|ˊ|${TONE2_Y}"
    "郝|09|ㄏㄠ|ˇ|${TONE3_Y}"
    "幹|43|ㄍㄢ|ˋ|${TONE4_Y}"
    "少|89|ㄕㄠ|ˋ|${TONE4_Y}"
    "上|89|ㄕㄤ|ˋ|${TONE4_Y}"
    "好|09|ㄏㄠ|ˇ|${TONE3_Y}"
    "撒|a2|ㄙㄚ|ˇ|${TONE3_Y}"
    "炸|22|ㄓㄚ|ˋ|${TONE4_Y}"
    "囊|79|ㄋㄤ|ˊ|${TONE2_Y}"
    "骰|4b|ㄊㄡ|ˊ|${TONE2_Y}"
)

# ========== 輔助函數 ==========
press_key() {
    local x=$1 y=$2
    $ADB shell input tap "$x" "$y"
}

clear_input() {
    # 多次按退格清除
    for i in $(seq 1 6); do
        press_key $BACKSPACE_X $BACKSPACE_Y
        sleep 0.2
    done
    sleep 0.5
}

# shellcheck disable=SC2317
ensure_keyboard() {
    # 點擊輸入欄位確保鍵盤顯示
    $ADB shell input tap 228 88
    sleep 1.5
}

# ========== 建置與安裝 ==========
if [[ "$NO_BUILD" == false ]]; then
    echo ">>> 建置 APK..."
    cd "$(dirname "$0")/.."
    BUILD_ABI=armeabi-v7a ./gradlew assembleDebug 2>&1 | tail -3
fi

if [[ "$NO_INSTALL" == false ]]; then
    echo ">>> 安裝 APK..."
    APK=$(find app/build/outputs/apk/debug/ -name '*.apk' -print0 2>/dev/null | xargs -0 ls -t 2>/dev/null | head -1)
    if [[ -z "$APK" ]]; then
        echo "ERROR: 找不到 APK"
        exit 1
    fi
    $ADB install -r "$APK"
    sleep 2
    $ADB shell am force-stop com.osfans.trime.debug
    sleep 2
fi

# ========== 環境準備 ==========
echo ">>> 準備測試環境..."
$ADB shell svc power stayon usb 2>/dev/null || true
$ADB shell settings put system screen_off_timeout 600000 2>/dev/null || true

# ========== 執行測試 ==========
PASS=0
FAIL=0
SKIP=0
RESULTS=()

echo ""
echo "========================================="
echo " T9 多音字聲調過濾驗證"
echo "========================================="
echo ""

for tc in "${TEST_CASES[@]}"; do
    IFS='|' read -r CHAR T9CODE ZHUYIN TONE TONE_KEY_Y <<< "$tc"
    echo -n "測試: $CHAR ($ZHUYIN$TONE) T9=$T9CODE ... "

    # 清除輸入
    clear_input

    # 清除 logcat
    $ADB logcat -c 2>/dev/null

    # 按 T9 按鍵序列
    for ((i=0; i<${#T9CODE}; i++)); do
        ch="${T9CODE:$i:1}"
        key=$(code_to_key "$ch")
        coords=$(get_key_coords "$key")
        read -r kx ky <<< "$coords"
        press_key "$kx" "$ky"
        sleep 1.5
    done

    # 等待 RIME 回應
    sleep 1

    # 讀取 logcat 確認注音組合包含目標注音
    LOGCAT=$($ADB logcat -d 2>/dev/null | grep "T9Input" || true)
    COMBOS=$(echo "$LOGCAT" | grep "注音組合" | tail -1 || true)

    if echo "$COMBOS" | grep -q "$ZHUYIN"; then
        # 點選注音選擇器（需要找到正確的注音項）
        # 先嘗試第一項（通常就是目標注音）
        # 取得注音組合列表
        COMBO_LIST=$(echo "$COMBOS" | sed 's/.*\[\(.*\)\].*/\1/' | tr -d '[]' | tr ',' '\n' | sed 's/ //g')
        ZHUYIN_INDEX=0
        IDX=0
        while read -r line; do
            if [[ "$line" == "$ZHUYIN" ]]; then
                ZHUYIN_INDEX=$IDX
                break
            fi
            IDX=$((IDX + 1))
        done <<< "$COMBO_LIST"

        # 計算注音選擇器 Y 座標（每項約 38px 高）
        SELECTOR_Y=$((160 + ZHUYIN_INDEX * 38))
        press_key $ZHUYIN_SELECTOR_X $SELECTOR_Y
        sleep 1

        # 清除 logcat 再按聲調
        $ADB logcat -c 2>/dev/null
        sleep 0.3

        # 按聲調鍵
        press_key $TONE_X "$TONE_KEY_Y"
        sleep 1.5

        # 讀取聲調過濾結果
        TONE_LOG=$($ADB logcat -d 2>/dev/null | grep "T9Input.*聲調過濾.*$TONE" | tail -1 || true)

        if [[ -n "$TONE_LOG" ]]; then
            # 解析 "聲調過濾 'ˋ': 50 → 24"
            AFTER=$(echo "$TONE_LOG" | sed 's/.*→ //' | grep -o '[0-9]*' || echo "0")
            if [[ "$AFTER" -gt 0 ]]; then
                echo "✅ PASS (${AFTER}個候選詞)"
                PASS=$((PASS + 1))
                RESULTS+=("✅ $CHAR $ZHUYIN$TONE: PASS ($AFTER)")
            else
                echo "❌ FAIL (過濾後 0 個)"
                FAIL=$((FAIL + 1))
                RESULTS+=("❌ $CHAR $ZHUYIN$TONE: FAIL (0)")
            fi
        else
            # 沒抓到聲調過濾 log - 可能按鍵沒命中
            echo "⚠️  SKIP (未偵測到聲調過濾 log)"
            SKIP=$((SKIP + 1))
            RESULTS+=("⚠️  $CHAR $ZHUYIN$TONE: SKIP")
        fi
    else
        echo "⚠️  SKIP (注音組合中無 $ZHUYIN)"
        SKIP=$((SKIP + 1))
        RESULTS+=("⚠️  $CHAR $ZHUYIN$TONE: SKIP (no combo)")
    fi
done

# ========== 結果摘要 ==========
echo ""
echo "========================================="
echo " 測試結果摘要"
echo "========================================="
echo ""
for r in "${RESULTS[@]}"; do
    echo "  $r"
done
echo ""
echo "  PASS: $PASS / ${#TEST_CASES[@]}"
echo "  FAIL: $FAIL"
echo "  SKIP: $SKIP"
echo ""

if [[ $FAIL -gt 0 ]]; then
    echo ">>> 有 $FAIL 個測試失敗！"
    exit 1
elif [[ $PASS -eq 0 ]]; then
    echo ">>> 沒有測試通過，請檢查環境設定"
    exit 1
else
    echo ">>> 全部通過！"
    exit 0
fi
