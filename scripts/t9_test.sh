#!/system/bin/sh
# T9 注音輸入法裝置端自動化測試腳本
# 在裝置上執行，使用 input tap + usleep 減少 ADB 開銷
# 適用於 Android mksh (POSIX sh 相容)
# shellcheck disable=SC2317,SC2119,SC2120

# ========== 配置 ==========

PKG="com.osfans.trime.debug"
ACTIVITY="com.osfans.trime.ui.test.InputTestActivity"

# T9 鍵盤座標常數（根據 456x456 螢幕校準）
KEY1_X=155  KEY1_Y=195   # ㄅㄉㄚ
KEY2_X=250  KEY2_Y=195   # ㄍㄐㄞㄧ
KEY3_X=345  KEY3_Y=195   # ㄓㄗㄢㄦ
KEY4_X=155  KEY4_Y=265   # ㄆㄊㄛ
KEY5_X=250  KEY5_Y=265   # ㄎㄑㄟㄨ
KEY6_X=345  KEY6_Y=265   # ㄔㄘㄣ
KEY7_X=155  KEY7_Y=340   # ㄇㄋㄜㄝ
KEY8_X=250  KEY8_Y=340   # ㄏㄒㄠㄩ
KEY9_X=345  KEY9_Y=340   # ㄕㄙㄤㄥ
KEY0_X=185  KEY0_Y=405   # ㄈㄌㄡㄖ

# 功能鍵
BACKSPACE_X=415  BACKSPACE_Y=110
CONFIRM_X=410    CONFIRM_Y=230

# 候選詞
CAND_L_X=175  CAND_Y=47
CAND_M_X=228
CAND_R_X=285

# 計數器
PASS_COUNT=0
FAIL_COUNT=0
TOTAL_COUNT=0

# hint 文字
HINT_TEXT="請在此測試輸入法"

# tap 間等待（微秒）
TAP_WAIT=400000    # 400ms 給 RIME 處理
SELECT_WAIT=600000 # 600ms 給候選詞選擇

# ========== 核心函式 ==========

tap() {
    # input tap + 延遲
    # 參數: $1=x $2=y [$3=delay_us]
    _wait="${3:-$TAP_WAIT}"
    input tap "$1" "$2"
    usleep "$_wait"
}

wait_ime_shown() {
    _timeout=50
    _i=0
    while [ "$_i" -lt "$_timeout" ]; do
        if dumpsys input_method 2>/dev/null | grep -q "mInputShown=true"; then
            return 0
        fi
        usleep 100000
        _i=$((_i + 1))
    done
    echo "WARN: IME not shown after 5s"
    return 1
}

wait_activity_ready() {
    _timeout=30
    _i=0
    while [ "$_i" -lt "$_timeout" ]; do
        if dumpsys activity activities 2>/dev/null | grep -q "InputTestActivity"; then
            return 0
        fi
        usleep 200000
        _i=$((_i + 1))
    done
    echo "WARN: Activity not ready after 6s"
    return 1
}

get_edittext_content() {
    _dumpfile="/data/local/tmp/ui_dump.xml"
    uiautomator dump "$_dumpfile" >/dev/null 2>/dev/null
    if [ ! -f "$_dumpfile" ]; then
        echo ""
        return 1
    fi
    _text=$(grep -o 'text="[^"]*"[^>]*class="android.widget.EditText"' "$_dumpfile" | head -1 | grep -o '^text="[^"]*"' | sed 's/text="//;s/"$//')
    rm -f "$_dumpfile"
    if [ "$_text" = "$HINT_TEXT" ]; then
        echo ""
    else
        echo "$_text"
    fi
}

assert_text() {
    _expected="$1"
    _name="$2"
    TOTAL_COUNT=$((TOTAL_COUNT + 1))

    _actual=$(get_edittext_content)
    if [ "$_actual" = "$_expected" ]; then
        echo "PASS: $_name (expected='$_expected', got='$_actual')"
        PASS_COUNT=$((PASS_COUNT + 1))
        return 0
    else
        echo "FAIL: $_name (expected='$_expected', got='$_actual')"
        FAIL_COUNT=$((FAIL_COUNT + 1))
        return 1
    fi
}

assert_endswith() {
    _suffix="$1"
    _name="$2"
    TOTAL_COUNT=$((TOTAL_COUNT + 1))

    _actual=$(get_edittext_content)
    case "$_actual" in
        *"$_suffix")
            echo "PASS: $_name (content='$_actual', ends with '$_suffix')"
            PASS_COUNT=$((PASS_COUNT + 1))
            return 0
            ;;
        *)
            echo "FAIL: $_name (expected ends with '$_suffix', got='$_actual')"
            FAIL_COUNT=$((FAIL_COUNT + 1))
            return 1
            ;;
    esac
}

# ========== 按鍵快捷函式 ==========

tap_key1() { tap "$KEY1_X" "$KEY1_Y" "${1:-$TAP_WAIT}"; }
tap_key2() { tap "$KEY2_X" "$KEY2_Y" "${1:-$TAP_WAIT}"; }
tap_key3() { tap "$KEY3_X" "$KEY3_Y" "${1:-$TAP_WAIT}"; }
tap_key4() { tap "$KEY4_X" "$KEY4_Y" "${1:-$TAP_WAIT}"; }
tap_key5() { tap "$KEY5_X" "$KEY5_Y" "${1:-$TAP_WAIT}"; }
tap_key6() { tap "$KEY6_X" "$KEY6_Y" "${1:-$TAP_WAIT}"; }
tap_key7() { tap "$KEY7_X" "$KEY7_Y" "${1:-$TAP_WAIT}"; }
tap_key8() { tap "$KEY8_X" "$KEY8_Y" "${1:-$TAP_WAIT}"; }
tap_key9() { tap "$KEY9_X" "$KEY9_Y" "${1:-$TAP_WAIT}"; }
tap_key0() { tap "$KEY0_X" "$KEY0_Y" "${1:-$TAP_WAIT}"; }

tap_backspace() { tap "$BACKSPACE_X" "$BACKSPACE_Y" "${1:-$TAP_WAIT}"; }
tap_confirm()   { tap "$CONFIRM_X" "$CONFIRM_Y" "${1:-$SELECT_WAIT}"; }

tap_cand_left()   { tap "$CAND_L_X" "$CAND_Y" "${1:-$SELECT_WAIT}"; }
tap_cand_mid()    { tap "$CAND_M_X" "$CAND_Y" "${1:-$SELECT_WAIT}"; }
tap_cand_right()  { tap "$CAND_R_X" "$CAND_Y" "${1:-$SELECT_WAIT}"; }

# ========== 測試案例 ==========

ensure_keyboard() {
    # 確認鍵盤仍然顯示，若否則重新喚起
    if ! dumpsys input_method 2>/dev/null | grep -q "mInputShown=true"; then
        echo "  (re-activating keyboard)"
        input tap 228 280
        usleep 800000
    fi
}

setup() {
    echo "=== Setup ==="
    # 喚醒螢幕
    input keyevent KEYCODE_WAKEUP
    usleep 500000

    am start -n "$PKG/$ACTIVITY" 2>/dev/null
    wait_activity_ready
    usleep 1000000

    # 點擊 EditText 喚起鍵盤
    _retry=0
    while [ "$_retry" -lt 3 ]; do
        input tap 228 280
        usleep 500000
        if dumpsys input_method 2>/dev/null | grep -q "mInputShown=true"; then
            echo "  IME ready (attempt $((_retry + 1)))"
            break
        fi
        echo "  Waiting for IME..."
        _retry=$((_retry + 1))
    done
    usleep 500000

    # 預熱：點一下確認鍵讓 IME 完全就緒
    tap_confirm
    usleep 500000
    echo "Setup done."
}

test_input_nihao() {
    echo ""
    echo "--- Test 1: 輸入「你好」 ---"
    ensure_keyboard

    # ㄋ=Key7, ㄧ=Key2 → 選「你」
    tap_key7
    tap_key2
    tap_cand_left

    # ㄏ=Key8, ㄠ=Key8 → 選「好」
    tap_key8
    tap_key8
    tap_cand_mid

    # 確認提交
    tap_confirm
    usleep 500000

    # 驗證（uiautomator dump 可能會影響鍵盤焦點）
    assert_endswith "你好" "輸入你好"
}

test_backspace_composition() {
    echo ""
    echo "--- Test 2: 退格清除注音組合 ---"
    ensure_keyboard

    # 輸入 ㄋ (Key7) 開始組合，然後退格清除
    tap_key7
    tap_backspace
    # 嘗試確認 — 若組合已清除，應無新文字提交
    tap_confirm

    # 驗證：內容應仍為 test 1 的結果
    assert_endswith "你好" "退格後仍為你好"
}

test_multi_char() {
    echo ""
    echo "--- Test 3: 追加輸入「大家」 ---"
    ensure_keyboard

    # ㄉ=Key1, ㄚ=Key1 → 選「大」
    tap_key1
    tap_key1
    tap_cand_left

    # ㄐ=Key2, ㄧ=Key2, ㄚ=Key1 → 選「家」
    tap_key2
    tap_key2
    tap_key1
    tap_cand_left

    # 確認
    tap_confirm

    assert_endswith "大家" "追加輸入大家"
}

# ========== 主程式 ==========

echo "========================================"
echo "  T9 注音輸入法自動化測試"
echo "========================================"
echo ""

START_TIME=$(date +%s)

setup
test_input_nihao
test_backspace_composition
test_multi_char

END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))

echo ""
echo "========================================"
echo "  測試結果: $PASS_COUNT/$TOTAL_COUNT PASSED"
if [ "$FAIL_COUNT" -gt 0 ]; then
    echo "  失敗: $FAIL_COUNT"
fi
echo "  耗時: ${ELAPSED}s"
echo "========================================"

if [ "$FAIL_COUNT" -gt 0 ]; then
    exit 1
fi
exit 0
