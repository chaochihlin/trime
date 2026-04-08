#!/bin/bash
# T9 注音輸入法測試 - 主機端執行器
# 用法: ./scripts/t9_test_runner.sh [-s SERIAL] [-b] [--no-install]

set -euo pipefail

# ========== 配置 ==========

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DEVICE_SCRIPT="t9_test.sh"
DEVICE_SCRIPT_PATH="/data/local/tmp/$DEVICE_SCRIPT"
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"

# 色彩
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color
BOLD='\033[1m'

# 預設參數
SERIAL=""
DO_BUILD=false
DO_INSTALL=true

# ========== 參數解析 ==========

usage() {
    echo "用法: $0 [-s SERIAL] [-b] [--no-install]"
    echo ""
    echo "選項:"
    echo "  -s SERIAL     指定 ADB 裝置序號"
    echo "  -b            建置 APK（預設不建置）"
    echo "  --no-install  跳過安裝步驟"
    echo "  -h, --help    顯示說明"
}

while [ $# -gt 0 ]; do
    case "$1" in
        -s)
            SERIAL="$2"
            shift 2
            ;;
        -b)
            DO_BUILD=true
            shift
            ;;
        --no-install)
            DO_INSTALL=false
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "未知參數: $1"
            usage
            exit 1
            ;;
    esac
done

# ========== 函式 ==========

adb_cmd() {
    if [ -n "$SERIAL" ]; then
        adb -s "$SERIAL" "$@"
    else
        adb "$@"
    fi
}

log_step() {
    echo -e "${CYAN}[步驟]${NC} $1"
}

log_ok() {
    echo -e "${GREEN}[OK]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# ========== 自動偵測裝置 ==========

detect_device() {
    if [ -n "$SERIAL" ]; then
        log_step "使用指定裝置: $SERIAL"
        return 0
    fi

    local devices
    devices=$(adb devices | grep -v "^List" | grep -v "^$" | awk '{print $1}')
    local count
    count=$(echo "$devices" | grep -c . || true)

    if [ "$count" -eq 0 ]; then
        log_error "未偵測到 ADB 裝置"
        exit 1
    elif [ "$count" -eq 1 ]; then
        SERIAL=$(echo "$devices" | head -1)
        log_step "自動偵測裝置: $SERIAL"
    else
        log_warn "偵測到多個裝置:"
        echo "$devices"
        log_error "請用 -s SERIAL 指定裝置"
        exit 1
    fi
}

# ========== 螢幕常亮 ==========

ensure_screen_on() {
    log_step "設定螢幕常亮 (USB)..."
    adb_cmd shell "svc power stayon usb" 2>/dev/null
    adb_cmd shell "settings put system screen_off_timeout 600000" 2>/dev/null
    adb_cmd shell "input keyevent KEYCODE_WAKEUP" 2>/dev/null
    log_ok "螢幕常亮已啟用"
}

# ========== 建置 ==========

build_apk() {
    if [ "$DO_BUILD" = true ]; then
        log_step "建置 APK (armeabi-v7a)..."
        cd "$PROJECT_DIR"
        BUILD_ABI=armeabi-v7a ./gradlew assembleDebug
        log_ok "建置完成"
    fi
}

# ========== 安裝 ==========

install_apk() {
    if [ "$DO_INSTALL" = true ]; then
        if [ ! -f "$APK_PATH" ]; then
            log_error "APK 不存在: $APK_PATH"
            log_error "請先用 -b 建置或手動建置"
            exit 1
        fi
        log_step "安裝 APK..."
        adb_cmd install -r "$APK_PATH" 2>/dev/null
        log_ok "APK 安裝完成"
    else
        log_step "跳過安裝"
    fi
}

# ========== 推送腳本 ==========

push_script() {
    log_step "推送測試腳本至裝置..."
    adb_cmd push "$SCRIPT_DIR/$DEVICE_SCRIPT" "$DEVICE_SCRIPT_PATH" 2>/dev/null
    adb_cmd shell chmod 755 "$DEVICE_SCRIPT_PATH"
    log_ok "腳本已推送"
}

# ========== 執行測試 ==========

run_tests() {
    log_step "執行裝置端測試..."
    echo -e "${BOLD}----------------------------------------${NC}"
    echo ""

    local exit_code=0
    adb_cmd shell sh "$DEVICE_SCRIPT_PATH" || exit_code=$?

    echo ""
    echo -e "${BOLD}----------------------------------------${NC}"

    return $exit_code
}

# ========== 主程式 ==========

echo -e "${BOLD}======================================${NC}"
echo -e "${BOLD}  T9 注音輸入法自動化測試${NC}"
echo -e "${BOLD}======================================${NC}"
echo ""

START=$(date +%s)

detect_device
ensure_screen_on
build_apk
install_apk
push_script

EXIT_CODE=0
run_tests || EXIT_CODE=$?

END=$(date +%s)
TOTAL=$((END - START))

echo ""
if [ $EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}${BOLD}全部測試通過！${NC} (總耗時: ${TOTAL}s)"
else
    echo -e "${RED}${BOLD}部分測試失敗${NC} (總耗時: ${TOTAL}s)"
fi

exit $EXIT_CODE
