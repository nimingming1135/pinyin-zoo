# -*- coding: utf-8 -*-
"""
拼音动物园 —— 音节/汉字索引构建工具

用途：
1. 从 GB2312 汉字集反查「带调音节 -> 候选汉字」，作为 edge-tts 的发音驱动文本。
2. 输出 syl_index.json（音节 -> [汉字...]，按常用度排序）供 build_audio.py 使用。

设计原则：
- 只用「默认读音」(heteronym=False)，避免把多音字的生僻读音混进来；
- 候选汉字按笔画数 + 常用度排序，优先简单字（TTS 更稳、歧义更少）；
- 生成后人工抽检，可通过 OVERRIDES 强制指定某个音节的发音字。
"""
import json
import os

from pypinyin import pinyin, Style

# 人工指定：某些音节自动挑字不理想，或需要特定声调语义
OVERRIDES = {
    "nv3": "女",
    "nv4": "恧",
    "lv4": "绿",
    "lv3": "吕",
    "er2": "儿",
    "er3": "耳",
    "er4": "二",
    "yi1": "衣",
    "wu3": "五",
    "yu3": "雨",
    "yu4": "玉",
    "shi2": "十",
    "si4": "四",
    "qi1": "七",
    "ba1": "八",
    "jiu3": "九",
    "liu4": "六",
    "san1": "三",
    "e2": "鹅",
    "a1": "啊",
    "o1": "哦",
    "ma1": "妈",
    "ba4": "爸",
}

# 需要排除的非音节（叹词/拟声/方言）
BLACKLIST = {"hm", "hng", "m", "n", "ng", "ê", "cei", "den", "biang", "nun", "shei", "zhei"}


def gb2312_chars():
    """枚举 GB2312 全部汉字（一级 3755 + 二级 3008）"""
    out = []
    for hi in range(0xB0, 0xF8):
        for lo in range(0xA1, 0xFF):
            try:
                out.append(bytes([hi, lo]).decode("gb2312"))
            except Exception:
                continue
    return out


def build_index():
    idx = {}
    for ch in gb2312_chars():
        try:
            hy = pinyin(ch, style=Style.TONE3, heteronym=False)
        except Exception:
            continue
        if not hy or not hy[0]:
            continue
        s = hy[0][0]
        if not s:
            continue
        # Tone3 无调时形如 "de"，有调时形如 "ba1"
        if s[-1] not in "12345":
            continue
        if s[-1] == "5":
            continue  # 轻声不单独建音节
        idx.setdefault(s, []).append(ch)

    # 常用度排序：优先笔画少、结构简单的常用字
    def score(ch):
        common = (
            ch in "八拔把爸妈麻马骂发佛得特的讷勒哥科喝鸡七西知吃诗日资次思"
            "衣乌鱼女绿儿耳二五十玉雨四三六九鹅我你他天地人山水火木大小多少"
            "牛羊猫狗鸟虫鱼果花草木林森田禾米竹月日月星辰上下左右前后中"
        )
        return (0 if common else 1, ord(ch))

    result = {}
    for syl, chars in idx.items():
        base = syl[:-1]
        if base in BLACKLIST:
            continue
        if syl in OVERRIDES:
            result[syl] = [OVERRIDES[syl]] + [c for c in sorted(chars, key=score) if c != OVERRIDES[syl]]
        else:
            result[syl] = sorted(chars, key=score)
    return result


def main():
    idx = build_index()
    out_path = os.path.join(os.path.dirname(__file__), "syl_index.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(idx, f, ensure_ascii=False, indent=1)

    bases = sorted({s[:-1] for s in idx})
    print(f"带调音节: {len(idx)}  基础音节: {len(bases)}")
    print("示例:", {k: "".join(v[:6]) for k, v in list(idx.items())[:8]})
    for t in ["ba1", "ba2", "ba3", "ba4", "zhi1", "zi1", "nv3", "lv4", "er2", "ying1", "jue2", "xiong2"]:
        print(f"  {t:8s} -> {''.join(idx.get(t, []))[:12]}")
    print("写入:", out_path)


if __name__ == "__main__":
    main()
