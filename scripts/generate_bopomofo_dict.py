#!/usr/bin/env python3
"""
將 luna_pinyin.dict.yaml 轉換為 bopomofo_t9.dict.yaml

拼音 → 注音轉換，結合 essay.txt 詞頻排序。
"""

import sys
from collections import defaultdict
from pypinyin import pinyin, Style

# 聲調數字 → 注音聲調符號
TONE_MAP = {
    "1": "",    # 一聲無標記
    "2": "ˊ",
    "3": "ˇ",
    "4": "ˋ",
    "5": "˙",   # 輕聲
}

# ==================== 拼音 → 注音 轉換表 ====================

# 聲母 (initials) - 順序很重要，長的先匹配
INITIALS = [
    ("zh", "ㄓ"), ("ch", "ㄔ"), ("sh", "ㄕ"),
    ("b", "ㄅ"), ("p", "ㄆ"), ("m", "ㄇ"), ("f", "ㄈ"),
    ("d", "ㄉ"), ("t", "ㄊ"), ("n", "ㄋ"), ("l", "ㄌ"),
    ("g", "ㄍ"), ("k", "ㄎ"), ("h", "ㄏ"),
    ("j", "ㄐ"), ("q", "ㄑ"), ("x", "ㄒ"),
    ("r", "ㄖ"),
    ("z", "ㄗ"), ("c", "ㄘ"), ("s", "ㄙ"),
]

# 韻母 (finals) - 順序很重要，長的先匹配
FINALS = [
    # 複合韻母（4字母）
    ("iang", "ㄧㄤ"), ("iong", "ㄩㄥ"), ("uang", "ㄨㄤ"),
    # 複合韻母（3字母）
    ("ang", "ㄤ"), ("eng", "ㄥ"), ("ing", "ㄧㄥ"), ("ong", "ㄨㄥ"),
    ("ian", "ㄧㄢ"), ("uan", "ㄨㄢ"), ("van", "ㄩㄢ"),
    ("iao", "ㄧㄠ"), ("uai", "ㄨㄞ"),
    # 複合韻母（2字母）
    ("ia", "ㄧㄚ"), ("ie", "ㄧㄝ"), ("iu", "ㄧㄡ"), ("in", "ㄧㄣ"),
    ("ua", "ㄨㄚ"), ("uo", "ㄨㄛ"), ("ui", "ㄨㄟ"), ("un", "ㄨㄣ"),
    ("ve", "ㄩㄝ"), ("vn", "ㄩㄣ"),
    ("ai", "ㄞ"), ("ei", "ㄟ"), ("ao", "ㄠ"), ("ou", "ㄡ"),
    ("an", "ㄢ"), ("en", "ㄣ"), ("er", "ㄦ"),
    # 單韻母（1字母）
    ("a", "ㄚ"), ("o", "ㄛ"), ("e", "ㄜ"),
    ("i", "ㄧ"), ("u", "ㄨ"), ("v", "ㄩ"),
]

# 整音節特殊處理
WHOLE_SYLLABLES = {
    "yi": "ㄧ", "ya": "ㄧㄚ", "ye": "ㄧㄝ", "yao": "ㄧㄠ", "you": "ㄧㄡ",
    "yan": "ㄧㄢ", "yin": "ㄧㄣ", "yang": "ㄧㄤ", "ying": "ㄧㄥ", "yong": "ㄩㄥ",
    "yu": "ㄩ", "yue": "ㄩㄝ", "yuan": "ㄩㄢ", "yun": "ㄩㄣ",
    "wu": "ㄨ", "wa": "ㄨㄚ", "wo": "ㄨㄛ", "wai": "ㄨㄞ", "wei": "ㄨㄟ",
    "wan": "ㄨㄢ", "wen": "ㄨㄣ", "wang": "ㄨㄤ", "weng": "ㄨㄥ",
    # 特殊音節
    "zhi": "ㄓ", "chi": "ㄔ", "shi": "ㄕ", "ri": "ㄖ",
    "zi": "ㄗ", "ci": "ㄘ", "si": "ㄙ",
    # 獨立韻母
    "a": "ㄚ", "o": "ㄛ", "e": "ㄜ", "eh": "ㄝ",
    "ai": "ㄞ", "ei": "ㄟ",
    "ao": "ㄠ", "ou": "ㄡ", "an": "ㄢ", "en": "ㄣ",
    "ang": "ㄤ", "eng": "ㄥ", "er": "ㄦ",
    "yo": "ㄧㄛ",
}


def pinyin_to_bopomofo(pinyin: str) -> str:
    """將單個拼音音節轉換為注音符號"""
    pinyin = pinyin.lower().strip()
    if not pinyin:
        return ""

    # 先檢查整音節
    if pinyin in WHOLE_SYLLABLES:
        return WHOLE_SYLLABLES[pinyin]

    # j/q/x 後的 u 實際是 ü（但 iu 例外，iu=ㄧㄡ）
    if pinyin[0] in ('j', 'q', 'x'):
        rest = pinyin[1:]
        # 只在 u 不是 iu 的一部分時才轉 v
        if rest == "u":
            pinyin = pinyin[0] + "v"
        elif rest.startswith("u") and not rest.startswith("iu"):
            pinyin = pinyin[0] + "v" + rest[1:]
        pinyin = pinyin.replace("ue", "ve").replace("un", "vn").replace("uan", "van")

    # 分離聲母
    initial = ""
    initial_bpmf = ""
    remaining = pinyin

    for py, bpmf in INITIALS:
        if pinyin.startswith(py):
            initial = py
            initial_bpmf = bpmf
            remaining = pinyin[len(py):]
            break

    # n/l + ü 的處理
    if initial in ('n', 'l'):
        remaining = remaining.replace("ue", "ve").replace("v", "v")
        if remaining == "u" and initial + "u" not in ("nu", "lu"):
            pass  # nu/lu 是正常的
        remaining = remaining.replace("ü", "v")

    # 匹配韻母
    final_bpmf = ""
    for py, bpmf in FINALS:
        if remaining == py:
            final_bpmf = bpmf
            break

    if not final_bpmf and remaining:
        # 未匹配到韻母，嘗試直接字符轉換
        return ""

    result = initial_bpmf + final_bpmf
    return result if result else ""


def load_luna_pinyin(filepath: str) -> dict:
    """載入 luna_pinyin.dict.yaml，返回 {字: [pinyin1, pinyin2, ...]}"""
    char_pinyin = defaultdict(list)
    in_entries = False

    with open(filepath, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if line == '...':
                in_entries = True
                continue
            if not in_entries:
                continue
            if not line or line.startswith('#'):
                continue

            parts = line.split('\t')
            if len(parts) >= 2:
                word = parts[0]
                pinyin = parts[1]
                # 只處理單字（非詞組，拼音中無空格）
                if len(word) == 1 and ' ' not in pinyin:
                    # 第三欄可能有權重如 "100%" 或 "0%"
                    weight_str = parts[2] if len(parts) >= 3 else ""
                    is_low_weight = weight_str.endswith('%') and weight_str != '100%'
                    # 低權重的排在後面
                    if is_low_weight:
                        char_pinyin[word].append(pinyin)
                    else:
                        char_pinyin[word].insert(0, pinyin)  # 高權重排前面

    return char_pinyin


def load_essay(filepath: str) -> dict:
    """載入 essay.txt，返回 {字/詞: frequency}"""
    freq = {}
    with open(filepath, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.split('\t')
            if len(parts) >= 2:
                word = parts[0]
                try:
                    freq[word] = int(parts[1])
                except ValueError:
                    pass
    return freq


def is_cjk_char(char: str) -> bool:
    """檢查是否為 CJK 漢字"""
    cp = ord(char)
    return (
        (0x4E00 <= cp <= 0x9FFF) or      # CJK Unified
        (0x3400 <= cp <= 0x4DBF) or      # CJK Extension A
        (0xF900 <= cp <= 0xFAFF)         # CJK Compatibility
    )


def get_tone_mark(char: str) -> str:
    """用 pypinyin 取得漢字的聲調符號"""
    try:
        toned = pinyin(char, style=Style.TONE3)[0][0]  # e.g. "hai4"
        if toned and toned[-1].isdigit():
            return TONE_MAP.get(toned[-1], "")
        return ""
    except Exception:
        return ""


def get_tones_with_pinyin(char: str, target_pinyin: str) -> list:
    """用 pypinyin 取得漢字特定讀音的所有聲調變體"""
    try:
        all_pinyins = pinyin(char, style=Style.TONE3, heteronym=True)[0]
        tones = []
        for toned in all_pinyins:
            base = toned.rstrip("12345")
            if base == target_pinyin and toned[-1].isdigit():
                tone = TONE_MAP.get(toned[-1], "")
                if tone not in tones:
                    tones.append(tone)
        if tones:
            return tones
        # fallback: 用預設讀音
        return [get_tone_mark(char)]
    except Exception:
        return [get_tone_mark(char)]


def generate_dict(luna_path: str, essay_path: str, output_path: str, max_chars: int = 5500):
    """生成 bopomofo_t9.dict.yaml"""
    print(f"載入 luna_pinyin: {luna_path}")
    char_pinyin = load_luna_pinyin(luna_path)
    print(f"  載入 {len(char_pinyin)} 個字的拼音")

    print(f"載入 essay: {essay_path}")
    essay_freq = load_essay(essay_path)
    print(f"  載入 {len(essay_freq)} 個詞頻")

    # 只保留 CJK 漢字，按詞頻排序
    entries = []
    conversion_ok = 0
    conversion_fail = 0

    for char, pinyins in char_pinyin.items():
        if not is_cjk_char(char):
            continue

        freq = essay_freq.get(char, 0)
        converted_any = False

        # 收錄所有讀音的所有聲調變體（多音字會產生多行）
        seen_bopomofo = set()
        for py in pinyins:
            bopomofo = pinyin_to_bopomofo(py)
            if not bopomofo:
                continue
            # 取得該讀音的所有聲調變體
            tones = get_tones_with_pinyin(char, py)
            for tone in tones:
                bopomofo_with_tone = bopomofo + tone
                if bopomofo_with_tone not in seen_bopomofo:
                    seen_bopomofo.add(bopomofo_with_tone)
                    entries.append((char, bopomofo_with_tone, freq, py))
                    converted_any = True

        if converted_any:
            conversion_ok += 1
        else:
            conversion_fail += 1
            if freq > 100:
                print(f"  ⚠ 轉換失敗（高頻字）: {char} [{pinyins[0]}] freq={freq}")

    print(f"轉換結果: {conversion_ok} 成功, {conversion_fail} 失敗")

    # 先按詞頻選出 top N 個「不重複的字」，再收錄這些字的所有讀音
    # （多音字會有多行 entry，但選字時只算一次）
    char_best_freq = {}
    for char, bopomofo, freq, py in entries:
        if char not in char_best_freq or freq > char_best_freq[char]:
            char_best_freq[char] = freq

    top_chars = sorted(char_best_freq.keys(), key=lambda c: char_best_freq[c], reverse=True)[:max_chars]
    top_chars_set = set(top_chars)

    # 保留 top N 字的所有讀音
    entries = [e for e in entries if e[0] in top_chars_set]
    entries.sort(key=lambda x: x[2], reverse=True)

    unique_count = len(top_chars_set)
    print(f"取 top {unique_count} 字（含多音共 {len(entries)} 條）")

    # 計算權重（正規化到 100-10000）
    max_freq = max(entries[0][2], 1) if entries else 1

    # 寫入字典
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write("# Rime dictionary\n")
        f.write("# encoding: utf-8\n")
        f.write("#\n")
        f.write("# T9 注音字典 - 自動生成\n")
        f.write(f"# 來源: luna_pinyin.dict.yaml + essay.txt\n")
        f.write(f"# 字數: {unique_count}（含多音共 {len(entries)} 條）\n")
        f.write("#\n\n")
        f.write("---\n")
        f.write("name: bopomofo_t9\n")
        f.write('version: "3.0"\n')
        f.write("sort: by_weight\n")
        f.write("use_preset_vocabulary: false\n")
        f.write("\n...\n\n")

        for char, bopomofo, freq, pinyin in entries:
            # 正規化權重到 100-10000
            weight = max(100, int(freq / max_freq * 10000))
            f.write(f"{char}\t{bopomofo}\t{weight}\n")

    print(f"✅ 已寫入: {output_path}")
    print(f"   共 {unique_count} 個字（含多音共 {len(entries)} 條）")

    # 驗證關鍵字
    test_chars = {"嗨": "ㄏㄞ", "你": "ㄋㄧ", "好": "ㄏㄠ", "我": "ㄨㄛ", "是": "ㄕ"}
    print("\n驗證關鍵字:")
    for char, expected_prefix in test_chars.items():
        found = [e for e in entries if e[0] == char]
        if found:
            print(f"  ✅ {char}: {found[0][1]} (pinyin: {found[0][3]}, weight: {found[0][2]})")
        else:
            print(f"  ❌ {char}: 未找到")


if __name__ == "__main__":
    import os
    base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

    luna_path = os.path.join(base, "app/src/main/jni/librime/data/minimal/luna_pinyin.dict.yaml")
    essay_path = os.path.join(base, "app/src/main/jni/librime/data/minimal/essay.txt")
    output_path = os.path.join(base, "app/src/main/assets/shared/bopomofo_t9.dict.yaml")

    max_chars = int(sys.argv[1]) if len(sys.argv) > 1 else 5500
    generate_dict(luna_path, essay_path, output_path, max_chars)
