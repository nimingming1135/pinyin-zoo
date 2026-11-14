# -*- coding: utf-8 -*-
"""
生成答题反馈音效（WAV 16bit 单声道）。

背景：AudioPlayer.loadSfx() 一直在找 assets/sfx/*.mp3，
但该目录从未生成过，导致 sfxLoaded=false，所有音效静默跳过。
本脚本用纯 numpy 合成，不依赖 ffmpeg / 外部素材，体积小（合计约 60KB）。

设计原则（给一年级孩子听）：
- 答对：上行三音（C5-E5-G5），明亮但不刺耳；
- 答错：下行两音（380Hz→300Hz），柔和、不用刺耳的低频嗡声，
  不制造挫败感，只是"提示你再想想"；
- 包络都带淡入淡出，避免爆音。
"""
import math
import os
import wave

import numpy as np

SR = 22050
OUT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app", "src", "main", "assets", "sfx",
)


def envelope(n, attack=0.006, release=0.05):
    """线性淡入淡出，防止开头结尾爆音。"""
    env = np.ones(n, dtype=np.float64)
    a = max(1, int(attack * SR))
    r = max(1, int(release * SR))
    a = min(a, n)
    r = min(r, n)
    env[:a] = np.linspace(0.0, 1.0, a)
    env[-r:] = np.linspace(1.0, 0.0, r)
    return env


def tone(freq, dur, amp=0.35, attack=0.006, release=0.05, harm=0.18):
    """单个正弦音，叠加一点二次谐波让音色更"玩具"一点。"""
    n = int(dur * SR)
    t = np.arange(n) / SR
    wave_ = np.sin(2 * math.pi * freq * t)
    wave_ += harm * np.sin(2 * math.pi * freq * 2 * t)
    wave_ *= envelope(n, attack, release)
    return amp * wave_ / (1 + harm)


def silence(dur):
    return np.zeros(int(dur * SR), dtype=np.float64)


def glide(f0, f1, dur, amp=0.32, harm=0.15):
    """从 f0 平滑滑到 f1，用于 whoosh / 下滑音。"""
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = np.linspace(f0, f1, n)
    phase = 2 * math.pi * np.cumsum(f) / SR
    w = np.sin(phase) + harm * np.sin(2 * phase)
    w *= envelope(n, 0.01, 0.08)
    return amp * w / (1 + harm)


def noise_sweep(dur, amp=0.16):
    """带限噪声做 whoosh：噪声乘上行包络。"""
    n = int(dur * SR)
    rng = np.random.default_rng(7)
    raw = rng.standard_normal(n)
    # 简单一阶低通，去掉刺耳高频
    out = np.empty(n)
    prev = 0.0
    for i in range(n):
        prev = 0.72 * prev + 0.28 * raw[i]
        out[i] = prev
    out *= np.linspace(0.2, 1.0, n) ** 2
    out *= envelope(n, 0.01, 0.12)
    return amp * out


def save(name, data):
    data = np.clip(data, -1.0, 1.0)
    pcm = (data * 32767).astype("<i2")
    path = os.path.join(OUT_DIR, name + ".wav")
    with wave.open(path, "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(SR)
        f.writeframes(pcm.tobytes())
    print("  %-12s %6.1f KB  %5.2fs" % (name + ".wav", len(pcm) * 2 / 1024, len(data) / SR))


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    print("生成音效 →", OUT_DIR)

    # 点击：极短的木质"嗒"
    save("tap", np.concatenate([
        tone(1180, 0.035, amp=0.30, release=0.03, harm=0.1),
        silence(0.005),
    ]))

    # 答对：C5 - E5 - G5 上行，最后一音稍长
    save("correct", np.concatenate([
        tone(523.25, 0.085, amp=0.30),
        tone(659.25, 0.085, amp=0.30),
        tone(783.99, 0.150, amp=0.34, release=0.10),
    ]))

    # 答错：柔和下行两音，不刺耳
    save("wrong", np.concatenate([
        tone(392.00, 0.130, amp=0.26, harm=0.10),
        tone(311.13, 0.190, amp=0.26, harm=0.10, release=0.12),
    ]))

    # 星星：高频快速上行闪光
    save("star", np.concatenate([
        tone(1046.50, 0.055, amp=0.22, harm=0.1),
        tone(1318.51, 0.055, amp=0.22, harm=0.1),
        tone(1567.98, 0.110, amp=0.24, harm=0.1, release=0.08),
    ]))

    # 解锁：四音琶音
    save("unlock", np.concatenate([
        tone(523.25, 0.080, amp=0.26),
        tone(659.25, 0.080, amp=0.26),
        tone(783.99, 0.080, amp=0.28),
        tone(1046.50, 0.180, amp=0.30, release=0.12),
    ]))

    # 翻页 whoosh
    save("whoosh", np.concatenate([
        noise_sweep(0.22),
        glide(900, 320, 0.10, amp=0.10),
    ]))

    total = sum(
        os.path.getsize(os.path.join(OUT_DIR, f)) for f in os.listdir(OUT_DIR)
    )
    print("合计 %.1f KB" % (total / 1024))


if __name__ == "__main__":
    main()
