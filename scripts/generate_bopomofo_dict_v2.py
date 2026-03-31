#!/usr/bin/env python3
"""
將 terra_pinyin.dict.yaml 轉換為 bopomofo_t9.dict.yaml (v2)

改用 terra_pinyin 作為源頭（已含聲調數字），完全移除 pypinyin 依賴。
結合 essay.txt 詞頻排序選出常用字。

用法:
  python3 scripts/generate_bopomofo_dict_v2.py [--max-chars 5500] [--dry-run] [--diff]
"""

import os
import re
import sys
from collections import defaultdict

# ==================== 聲調映射 ====================

TONE_MAP = {
    1: "",     # 一聲無標記
    2: "ˊ",
    3: "ˇ",
    4: "ˋ",
    5: "˙",   # 輕聲
}

# ==================== 拼音 → 注音 轉換表 ====================

# 整音節特殊處理（優先匹配）
WHOLE_SYLLABLES = {
    "yi": "ㄧ", "ya": "ㄧㄚ", "ye": "ㄧㄝ", "yao": "ㄧㄠ", "you": "ㄧㄡ",
    "yan": "ㄧㄢ", "yin": "ㄧㄣ", "yang": "ㄧㄤ", "ying": "ㄧㄥ", "yong": "ㄩㄥ",
    "yu": "ㄩ", "yue": "ㄩㄝ", "yuan": "ㄩㄢ", "yun": "ㄩㄣ",
    "wu": "ㄨ", "wa": "ㄨㄚ", "wo": "ㄨㄛ", "wai": "ㄨㄞ", "wei": "ㄨㄟ",
    "wan": "ㄨㄢ", "wen": "ㄨㄣ", "wang": "ㄨㄤ", "weng": "ㄨㄥ",
    "zhi": "ㄓ", "chi": "ㄔ", "shi": "ㄕ", "ri": "ㄖ",
    "zi": "ㄗ", "ci": "ㄘ", "si": "ㄙ",
    "a": "ㄚ", "o": "ㄛ", "e": "ㄜ", "eh": "ㄝ",
    "ai": "ㄞ", "ei": "ㄟ", "ao": "ㄠ", "ou": "ㄡ",
    "an": "ㄢ", "en": "ㄣ", "ang": "ㄤ", "eng": "ㄥ", "er": "ㄦ",
    "yo": "ㄧㄛ",
}

# 聲母
INITIALS = [
    ("zh", "ㄓ"), ("ch", "ㄔ"), ("sh", "ㄕ"),
    ("b", "ㄅ"), ("p", "ㄆ"), ("m", "ㄇ"), ("f", "ㄈ"),
    ("d", "ㄉ"), ("t", "ㄊ"), ("n", "ㄋ"), ("l", "ㄌ"),
    ("g", "ㄍ"), ("k", "ㄎ"), ("h", "ㄏ"),
    ("j", "ㄐ"), ("q", "ㄑ"), ("x", "ㄒ"),
    ("r", "ㄖ"),
    ("z", "ㄗ"), ("c", "ㄘ"), ("s", "ㄙ"),
]

# 韻母
FINALS = [
    ("iang", "ㄧㄤ"), ("iong", "ㄩㄥ"), ("uang", "ㄨㄤ"),
    ("ang", "ㄤ"), ("eng", "ㄥ"), ("ing", "ㄧㄥ"), ("ong", "ㄨㄥ"),
    ("ian", "ㄧㄢ"), ("uan", "ㄨㄢ"), ("van", "ㄩㄢ"),
    ("iao", "ㄧㄠ"), ("uai", "ㄨㄞ"),
    ("ia", "ㄧㄚ"), ("ie", "ㄧㄝ"), ("iu", "ㄧㄡ"), ("in", "ㄧㄣ"),
    ("ua", "ㄨㄚ"), ("uo", "ㄨㄛ"), ("ui", "ㄨㄟ"), ("un", "ㄨㄣ"),
    ("ve", "ㄩㄝ"), ("vn", "ㄩㄣ"),
    ("ai", "ㄞ"), ("ei", "ㄟ"), ("ao", "ㄠ"), ("ou", "ㄡ"),
    ("an", "ㄢ"), ("en", "ㄣ"), ("er", "ㄦ"),
    ("a", "ㄚ"), ("o", "ㄛ"), ("e", "ㄜ"),
    ("i", "ㄧ"), ("u", "ㄨ"), ("v", "ㄩ"),
]


# ==================== 手動修正 ====================
# CC-CEDICT / terra_pinyin 上游錯誤的已知修正
# 格式: {字: [(正確注音, 權重)]}  — 會覆蓋自動生成的讀音
MANUAL_CORRECTIONS = {
    "夕": [("ㄒㄧˋ", None)],      # CC-CEDICT 錯標為一聲
    "汐": [("ㄒㄧˋ", None)],      # 同上
}

# 補充字：詞頻不夠但客戶需要的字
# 格式: {字: [(注音, 權重)]}
SUPPLEMENT_CHARS = {
    "瑚": [("ㄏㄨˊ", 100)],
}


def pinyin_to_bopomofo(py: str) -> str:
    """將單個拼音音節（不含聲調數字）轉換為注音符號"""
    py = py.lower().strip()
    if not py:
        return ""

    if py in WHOLE_SYLLABLES:
        return WHOLE_SYLLABLES[py]

    # j/q/x 後的 u → ü (v)
    if py[0] in ('j', 'q', 'x'):
        rest = py[1:]
        if rest == "u":
            py = py[0] + "v"
        elif rest.startswith("u") and not rest.startswith("iu"):
            py = py[0] + "v" + rest[1:]
        py = py.replace("ue", "ve").replace("un", "vn").replace("uan", "van")

    # 分離聲母
    initial_bpmf = ""
    remaining = py
    for initial_py, bpmf in INITIALS:
        if py.startswith(initial_py):
            initial_bpmf = bpmf
            remaining = py[len(initial_py):]
            # n/l + ü 處理
            if initial_py in ('n', 'l'):
                remaining = remaining.replace("ue", "ve").replace("ü", "v")
            break

    # 匹配韻母
    final_bpmf = ""
    for final_py, bpmf in FINALS:
        if remaining == final_py:
            final_bpmf = bpmf
            break

    result = initial_bpmf + final_bpmf
    return result if result else ""


def is_cjk_char(char: str) -> bool:
    """檢查是否為 CJK 漢字"""
    cp = ord(char)
    return (
        (0x4E00 <= cp <= 0x9FFF)
        or (0x3400 <= cp <= 0x4DBF)
        or (0xF900 <= cp <= 0xFAFF)
    )


def load_terra_pinyin(filepath: str) -> dict:
    """
    載入 terra_pinyin.dict.yaml，返回 {字: [(pinyin, tone, weight_str)]}

    terra_pinyin 格式:
      好\thao3\t95%
      好\thao4\t5%
      拒\tju4
    """
    char_data = defaultdict(list)
    in_entries = False

    with open(filepath, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if line == '...':
                in_entries = True
                continue
            if not in_entries or not line or line.startswith('#'):
                continue

            parts = line.split('\t')
            if len(parts) < 2:
                continue

            char = parts[0]
            pinyin_field = parts[1]
            weight_str = parts[2] if len(parts) >= 3 else ""

            # 只處理單字
            if len(char) != 1 or ' ' in pinyin_field:
                continue

            # 解析 pinyin + tone number
            m = re.match(r'^([a-z]+)(\d)$', pinyin_field)
            if m:
                py_base = m.group(1)
                tone = int(m.group(2))
            else:
                py_base = pinyin_field
                tone = 0  # 無聲調（不應發生）

            # 跳過 0% 權重的罕見讀音
            if weight_str == '0%':
                continue

            char_data[char].append((py_base, tone, weight_str))

    return char_data


def load_essay(filepath: str) -> dict:
    """載入 essay.txt，返回 {字/詞: frequency}"""
    freq = {}
    with open(filepath, 'r', encoding='utf-8') as f:
        for line in f:
            parts = line.strip().split('\t')
            if len(parts) >= 2:
                try:
                    freq[parts[0]] = int(parts[1])
                except ValueError:
                    pass
    return freq


def generate_dict(terra_path: str, essay_path: str, output_path: str,
                  max_chars: int = 5500, dry_run: bool = False):
    """生成 bopomofo_t9.dict.yaml"""

    print(f"載入 terra_pinyin: {terra_path}")
    char_data = load_terra_pinyin(terra_path)
    print(f"  載入 {len(char_data)} 個字")

    print(f"載入 essay: {essay_path}")
    essay_freq = load_essay(essay_path)
    print(f"  載入 {len(essay_freq)} 個詞頻")

    # 轉換
    entries = []
    ok_count = 0
    fail_count = 0
    fail_chars = []

    for char, readings in char_data.items():
        if not is_cjk_char(char):
            continue

        freq = essay_freq.get(char, 0)
        converted = False
        seen = set()

        for py_base, tone, weight_str in readings:
            bopomofo = pinyin_to_bopomofo(py_base)
            if not bopomofo:
                continue

            tone_mark = TONE_MAP.get(tone, "")
            full_reading = bopomofo + tone_mark

            # 用讀音比例縮放權重（如 "95%" → 0.95）
            reading_ratio = 1.0
            if weight_str and weight_str.endswith('%'):
                try:
                    reading_ratio = int(weight_str[:-1]) / 100.0
                except ValueError:
                    pass
            reading_freq = max(1, int(freq * reading_ratio)) if freq > 0 else 0

            if full_reading not in seen:
                seen.add(full_reading)
                entries.append((char, full_reading, reading_freq))
                converted = True

        if converted:
            ok_count += 1
        else:
            fail_count += 1
            if freq > 100:
                fail_chars.append((char, readings, freq))

    print(f"轉換結果: {ok_count} 成功, {fail_count} 失敗")
    if fail_chars:
        print(f"  高頻失敗字 ({len(fail_chars)}):")
        for char, readings, freq in fail_chars[:10]:
            print(f"    {char} [{readings}] freq={freq}")

    # 按詞頻選 top N 字
    char_best_freq = {}
    for char, _reading, freq in entries:
        if char not in char_best_freq or freq > char_best_freq[char]:
            char_best_freq[char] = freq

    top_chars = sorted(char_best_freq.keys(),
                       key=lambda c: char_best_freq[c], reverse=True)[:max_chars]
    top_set = set(top_chars)

    entries = [e for e in entries if e[0] in top_set]
    entries.sort(key=lambda x: x[2], reverse=True)

    # 套用手動修正：覆蓋已知錯誤讀音
    correction_count = 0
    for char, corrections in MANUAL_CORRECTIONS.items():
        if char in top_set:
            # 移除該字所有自動生成的條目
            entries = [e for e in entries if e[0] != char]
            # 加入手動修正的讀音
            freq = char_best_freq.get(char, 100)
            for reading, weight in corrections:
                w = weight if weight is not None else freq
                entries.append((char, reading, w))
            correction_count += 1

    # 加入補充字
    supplement_count = 0
    for char, readings in SUPPLEMENT_CHARS.items():
        if char not in top_set:
            top_set.add(char)
            for reading, weight in readings:
                entries.append((char, reading, weight))
            supplement_count += 1

    entries.sort(key=lambda x: x[2], reverse=True)
    unique_count = len(top_set)
    print(f"取 top {unique_count} 字（含多音共 {len(entries)} 條）")
    print(f"  手動修正: {correction_count} 字, 補充: {supplement_count} 字")

    # 正規化權重
    max_freq = max(entries[0][2], 1) if entries else 1

    if dry_run:
        print(f"\n[DRY RUN] 不寫入檔案")
        return entries, unique_count

    # 寫入
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write("# Rime dictionary\n")
        f.write("# encoding: utf-8\n")
        f.write("#\n")
        f.write("# T9 注音字典 - 自動生成 (v2)\n")
        f.write("# 來源: terra_pinyin.dict.yaml + essay.txt\n")
        f.write(f"# 字數: {unique_count}（含多音共 {len(entries)} 條）\n")
        f.write("#\n\n")
        f.write("---\n")
        f.write("name: bopomofo_t9\n")
        f.write('version: "4.0"\n')
        f.write("sort: by_weight\n")
        f.write("use_preset_vocabulary: false\n")
        f.write("\n...\n\n")

        for char, bopomofo, freq in entries:
            weight = max(100, int(freq / max_freq * 10000))
            f.write(f"{char}\t{bopomofo}\t{weight}\n")

    print(f"✅ 已寫入: {output_path}")
    return entries, unique_count


def compare_dicts(new_entries, old_path):
    """比較新舊字典差異"""
    # 載入舊字典
    old = defaultdict(set)
    in_data = False
    with open(old_path, 'r', encoding='utf-8') as f:
        for line in f:
            if line.strip() == '...':
                in_data = True
                continue
            if not in_data:
                continue
            parts = line.strip().split('\t')
            if len(parts) >= 2 and len(parts[0]) == 1:
                old[parts[0]].add(parts[1])

    # 建立新字典映射
    new = defaultdict(set)
    for char, reading, _freq in new_entries:
        new[char].add(reading)

    # 統計
    chars_both = set(old.keys()) & set(new.keys())
    chars_old_only = set(old.keys()) - set(new.keys())
    chars_new_only = set(new.keys()) - set(old.keys())

    # 聲調變更
    tone_fixed = 0
    tone_added = 0
    tone_removed = 0
    reading_changed = 0
    fix_examples = []

    for char in chars_both:
        old_readings = old[char]
        new_readings = new[char]
        added = new_readings - old_readings
        removed = old_readings - new_readings

        if added or removed:
            reading_changed += 1
            tone_added += len(added)
            tone_removed += len(removed)

            if len(fix_examples) < 20:
                fix_examples.append(
                    f"  {char}: 舊={sorted(old_readings)} → 新={sorted(new_readings)}"
                )

    print(f"\n{'='*50}")
    print(f" 新舊字典比較")
    print(f"{'='*50}")
    print(f"  舊字典字數: {len(old)}, 條目: {sum(len(v) for v in old.values())}")
    print(f"  新字典字數: {len(new)}, 條目: {sum(len(v) for v in new.values())}")
    print(f"  共同字: {len(chars_both)}")
    print(f"  僅舊有: {len(chars_old_only)}")
    print(f"  僅新有: {len(chars_new_only)}")
    print(f"  讀音有變更的字: {reading_changed}")
    print(f"  新增讀音: {tone_added}")
    print(f"  移除讀音: {tone_removed}")

    # 驗證關鍵字
    print(f"\n=== 關鍵字驗證 ===")
    test_cases = [
        ("拒", "ㄐㄩˋ"), ("好", "ㄏㄠˇ"), ("期", "ㄑㄧˊ"),
        ("夕", "ㄒㄧˋ"), ("汐", "ㄒㄧˋ"), ("瑚", "ㄏㄨˊ"),
        ("上", "ㄕㄤˋ"), ("踏", "ㄊㄚˋ"), ("那", "ㄋㄚˋ"),
        ("累", "ㄌㄟˋ"), ("個", "ㄍㄜˋ"), ("行", "ㄏㄤˊ"),
    ]
    for char, expected in test_cases:
        readings = new.get(char, set())
        status = "✅" if expected in readings else "❌"
        print(f"  {status} {char} 期望 {expected} | 新={sorted(readings)}")

    if fix_examples:
        print(f"\n=== 讀音變更範例（前20個）===")
        for ex in fix_examples:
            print(ex)


if __name__ == "__main__":
    base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

    terra_path = os.path.join(base, "scripts/terra_pinyin_reference.dict.yaml")
    essay_path = os.path.join(base, "app/src/main/jni/librime/data/minimal/essay.txt")
    output_path = os.path.join(base, "app/src/main/assets/shared/bopomofo_t9.dict.yaml")
    old_path = output_path  # 比較用

    max_chars = 5500
    dry_run = False
    do_diff = False

    for arg in sys.argv[1:]:
        if arg == "--dry-run":
            dry_run = True
        elif arg == "--diff":
            do_diff = True
        elif arg.startswith("--max-chars"):
            max_chars = int(arg.split("=")[1])
        elif arg.isdigit():
            max_chars = int(arg)

    entries, unique_count = generate_dict(
        terra_path, essay_path, output_path, max_chars, dry_run=dry_run
    )

    if do_diff or dry_run:
        compare_dicts(entries, old_path)
