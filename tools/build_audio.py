# -*- coding: utf-8 -*-
"""
拼音动物园 —— 音频包生成管线（edge-tts）

产出：
  app/src/main/assets/audio/{syl,ini,fin,zy,word,vo}/*.mp3
  app/src/main/assets/audio_index.json   （含标准音高曲线，作诊断用）

核心设计：
1. 音节读音一律用「同音汉字」驱动，绝不把拼音文本交给 TTS（会读成英文字母）；
2. 声母用教学呼读音汉字（b->玻），保证拼合题成立；
3. 生成后用 ffmpeg 重编码为 24kbps 单声道，体积减半；
4. 提取 F0 轮廓写入索引。注意：评分主依据是「五度标记模板 + 个体音域归一化」，
   提取的曲线仅用于家长端展示与调试，不作为绝对比对基准。

用法：
  python build_audio.py                # 全量（跳过已存在）
  python build_audio.py --force        # 强制重建
  python build_audio.py --only syl     # 只生成某类
  python build_audio.py --no-contour   # 跳过曲线提取（更快）
"""
import asyncio
import json
import os
import subprocess
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
AUDIO_DIR = os.path.join(ASSETS, "audio")
INDEX_PATH = os.path.join(ASSETS, "audio_index.json")

VOICE = "zh-CN-YunxiaNeural"   # 官方标签 Cartoon / Cute
RATE = "-12%"                  # 略慢，适合一年级
FALLBACK_VOICE = "zh-CN-XiaoyiNeural"
BITRATE = "24k"
SAMPLE_RATE = 22050

# 响度归一化（EBU R128）：短音节用较高的目标响度，避免孩子听不清
LOUDNESS_I = -16.0    # 目标整合响度 LUFS
LOUDNESS_TP = -1.5    # 真峰值上限 dBTP

# ---------------------------------------------------------------- 内容表

INITIALS = {
    "b": "玻", "p": "坡", "m": "摸", "f": "佛",
    "d": "得", "t": "特", "n": "讷", "l": "勒",
    "g": "哥", "k": "科", "h": "喝",
    "j": "基", "q": "欺", "x": "希",
    "zh": "知", "ch": "吃", "sh": "诗", "r": "日",
    "z": "资", "c": "次", "s": "思",
    "y": "衣", "w": "乌",
}

FINALS = {
    "a": "啊", "o": "哦", "e": "鹅", "i": "衣", "u": "乌", "v": "迂",
    "ai": "哀", "ei": "诶", "ui": "威", "ao": "熬", "ou": "欧", "iu": "优",
    "ie": "耶", "ve": "约", "er": "儿",
    "an": "安", "en": "恩", "in": "因", "un": "温", "vn": "晕",
    "ang": "昂", "eng": "鞥", "ing": "英", "ong": "翁",
}

WHOLE = {
    "zhi": "知", "chi": "吃", "shi": "诗", "ri": "日",
    "zi": "资", "ci": "次", "si": "思",
    "yi": "衣", "wu": "乌", "yu": "迂",
    "ye": "耶", "yue": "约", "yuan": "冤",
    "yin": "因", "yun": "晕", "ying": "英",
}

WORDS = {
    "pingguo": ("苹果", "apple"), "niunai": ("牛奶", "milk"), "laoshi": ("老师", "teacher"),
    "lvse": ("绿色", "green"), "changcheng": ("长城", "wall"), "daxiang": ("大象", "elephant"),
    "xiongmao": ("熊猫", "panda"), "xiaoniao": ("小鸟", "bird"), "laohu": ("老虎", "tiger"),
    "tuzi": ("兔子", "rabbit"), "maque": ("麻雀", "sparrow"), "lizi": ("梨子", "pear"),
    "putao": ("葡萄", "grape"), "xigua": ("西瓜", "watermelon"), "caomei": ("草莓", "strawberry"),
    "mifeng": ("蜜蜂", "bee"), "hudie": ("蝴蝶", "butterfly"), "qingwa": ("青蛙", "frog"),
    "yueliang": ("月亮", "moon"), "taiyang": ("太阳", "sun"), "xingxing": ("星星", "star"),
    "senlin": ("森林", "forest"), "daishu": ("袋鼠", "kangaroo"), "konglong": ("恐龙", "dinosaur"),
    "changjinglu": ("长颈鹿", "giraffe"), "banma": ("斑马", "zebra"), "shizi": ("狮子", "lion"),
    "houzi": ("猴子", "monkey"), "xiaozhu": ("小猪", "pig"), "xiaoji": ("小鸡", "chick"),
    "chitang": ("池塘", "pond"), "yumao": ("羽毛", "feather"), "huayuan": ("花园", "garden"),
    "shuben": ("书本", "book"), "qianbi": ("铅笔", "pencil"), "shengri": ("生日", "birthday"),
    "pengyou": ("朋友", "friend"), "xuexiao": ("学校", "school"), "jiazhang": ("家长", "parent"),
    "zhuozi": ("桌子", "table"), "yizi": ("椅子", "chair"), "shuiguo": ("水果", "fruit"),
    "xiangjiao": ("香蕉", "banana"), "juzi": ("橘子", "orange"), "taozi": ("桃子", "peach"),
    "luobo": ("萝卜", "carrot"), "bocai": ("菠菜", "spinach"), "xihongshi": ("西红柿", "tomato"),
    "miantiao": ("面条", "noodle"), "baozi": ("包子", "bun"), "mifan": ("米饭", "rice"),
    "chuan": ("船", "boat"), "feiji": ("飞机", "plane"), "qiche": ("汽车", "car"),
    "zixingche": ("自行车", "bike"), "huoche": ("火车", "train"), "fangzi": ("房子", "house"),
    "shuijing": ("水井", "well"), "xiaohe": ("小河", "river"), "dashan": ("大山", "mountain"),
    "shu": ("树", "tree"), "hua": ("花", "flower"), "cao": ("草", "grass"),
    "yundong": ("运动", "sport"), "tiaosheng": ("跳绳", "rope"), "paobu": ("跑步", "run"),
    "youyong": ("游泳", "swim"), "zuoqiu": ("足球", "football"), "lanqiu": ("篮球", "basketball"),
    "mama": ("妈妈", "mother"), "baba": ("爸爸", "father"), "yeye": ("爷爷", "grandpa"),
    "nainai": ("奶奶", "grandma"), "gege": ("哥哥", "brother"), "jiejie": ("姐姐", "sister"),
    "didi": ("弟弟", "brother2"), "meimei": ("妹妹", "sister2"),
    "erduo": ("耳朵", "ear"), "yanjing": ("眼睛", "eye"), "bizi": ("鼻子", "nose"),
    "zuiba": ("嘴巴", "mouth"), "shou": ("手", "hand"), "jiao": ("脚", "foot"),
    "shangxue": ("上学", "school2"), "fangxue": ("放学", "afterschool"),
    "zuoye": ("作业", "homework"), "shuxue": ("数学", "math"), "yuwen": ("语文", "chinese"),
    "yinyue": ("音乐", "music"), "meishu": ("美术", "art"), "tiyu": ("体育", "pe"),
}

VOICE_LINES = {
    "perfect": "太棒了！", "great": "真厉害！", "correct": "答对啦！", "good": "很好哦！",
    "tryagain": "再听一次吧。", "thinkagain": "再想一想。", "almost": "差一点点。",
    "correct_is": "正确的读音是。", "listencareful": "仔细听哦。",
    "levellasted": "这一层完成啦！", "unlock": "新的关卡解锁了！",
    "tonetoolow": "这次声音往上走一点。", "tonehigh": "这次声音往下走一点。",
    "tonestart": "从高处开始，往下滑。", "tonerise": "慢慢往上爬。",
    "tonedip": "先降下去，再升上来。", "tonestay": "保持平平的，不要上下跑。",
    "speakslower": "慢一点，说清楚。", "louder": "大声一点，我听不清。",
    "listen1": "我们来听一听。", "listen2": "跟着它再读一遍。",
    "letsgo": "我们开始吧！", "allcorrect": "全部答对，真了不起！",
    "keepgoing": "继续加油！", "nextlevel": "进入下一关。",
    "tryonce": "再试一次。", "yourstar": "你获得了星星！",
    "listenandpick": "听一听，选出正确的那个。",
    "readafter": "跟着读一遍。", "wellread": "读得真好！",
}


def load_syl_index():
    with open(os.path.join(HERE, "syl_index.json"), encoding="utf-8") as f:
        return json.load(f)


def ffmpeg_exe():
    import imageio_ffmpeg
    return imageio_ffmpeg.get_ffmpeg_exe()


# ---------------------------------------------------------------- 生成

async def tts_one(text, out_path, rate=RATE):
    import edge_tts
    tmp = out_path + ".raw.mp3"
    ok = False
    for voice in (VOICE, FALLBACK_VOICE):
        try:
            comm = edge_tts.Communicate(text, voice, rate=rate)
            await comm.save(tmp)
            if os.path.exists(tmp) and os.path.getsize(tmp) > 500:
                ok = True
                break
        except Exception as e:
            print(f"    [warn] {voice} 失败 {text}: {e}")
    if not ok:
        if os.path.exists(tmp):
            os.remove(tmp)
        return False

    # 两遍响度归一化（EBU R128）：
    # 第一遍测量，第二遍按测量值归一，音色不变但音量统一。
    norm = out_path + ".norm.mp3"
    try:
        subprocess.run(
            [ffmpeg_exe(), "-y", "-v", "quiet", "-i", tmp,
             "-af", f"loudnorm=I={LOUDNESS_I}:TP={LOUDNESS_TP}:LRA=11:print_format=json",
             "-f", "null", "-"],
            check=False, capture_output=True)
    except Exception:
        pass

    try:
        # 单遍 loudnorm（动态模式），对短音足够且稳定
        subprocess.run(
            [ffmpeg_exe(), "-y", "-v", "quiet", "-i", tmp,
             "-af", f"loudnorm=I={LOUDNESS_I}:TP={LOUDNESS_TP}:LRA=11",
             "-ac", "1", "-ar", str(SAMPLE_RATE), "-b:a", BITRATE, norm],
            check=True, capture_output=True)
        if os.path.exists(norm) and os.path.getsize(norm) > 300:
            os.replace(norm, out_path)
            os.remove(tmp)
            return True
    except Exception as e:
        print(f"    [warn] 归一化失败 {out_path}: {e}")

    # 归一化失败时退回普通重编码
    try:
        subprocess.run(
            [ffmpeg_exe(), "-y", "-v", "quiet", "-i", tmp,
             "-ac", "1", "-ar", str(SAMPLE_RATE), "-b:a", BITRATE, out_path],
            check=True, capture_output=True)
        os.remove(tmp)
        return os.path.exists(out_path) and os.path.getsize(out_path) > 300
    except Exception as e:
        print(f"    [warn] 重编码失败 {out_path}: {e}")
        if os.path.exists(tmp):
            os.replace(tmp, out_path)
        return os.path.exists(out_path)


async def tts_batch(items, force=False, concurrency=8):
    sem = asyncio.Semaphore(concurrency)
    stat = {"ok": 0, "skip": 0, "fail": 0}
    lock = asyncio.Lock()

    async def worker(rel, text):
        path = os.path.join(AUDIO_DIR, rel)
        if not force and os.path.exists(path) and os.path.getsize(path) > 300:
            async with lock:
                stat["skip"] += 1
            return
        os.makedirs(os.path.dirname(path), exist_ok=True)
        async with sem:
            success = await tts_one(text, path)
        async with lock:
            if success:
                stat["ok"] += 1
                if stat["ok"] % 100 == 0:
                    print(f"  已生成 {stat['ok']} 条...", flush=True)
            else:
                stat["fail"] += 1
                print(f"  [FAIL] {rel} <- {text}", flush=True)

    await asyncio.gather(*[worker(r, t) for r, t in items])
    return stat


# ---------------------------------------------------------------- F0 提取

def mp3_to_pcm(path, sr=16000):
    cmd = [ffmpeg_exe(), "-v", "quiet", "-i", path,
           "-f", "s16le", "-ac", "1", "-ar", str(sr), "-"]
    raw = subprocess.run(cmd, capture_output=True).stdout
    if not raw:
        return np.zeros(0, dtype=np.float32)
    return np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0


def trim_silence(x, sr=16000, thr=0.02, pad=0.03):
    idx = np.where(np.abs(x) > thr)[0]
    if len(idx) == 0:
        return x
    p = int(pad * sr)
    return x[max(0, idx[0] - p): idx[-1] + p]


def f0_track(x, sr=16000, frame=1024, hop=80, fmin=75, fmax=500):
    """自相关基频轨迹，带倍频纠正"""
    n = 1 + max(0, (len(x) - frame)) // hop
    out = np.zeros(n, dtype=np.float32)
    tmin, tmax = int(sr / fmax), int(sr / fmin)
    for i in range(n):
        seg = x[i * hop: i * hop + frame]
        if len(seg) < frame:
            break
        if float(np.sqrt(np.mean(seg ** 2))) < 0.02:
            continue
        seg = seg - seg.mean()
        ac = np.correlate(seg, seg, "full")[frame - 1:]
        if ac[0] == 0:
            continue
        ac = ac / ac[0]
        hi = min(tmax, len(ac) - 2)
        if hi <= tmin:
            continue
        k = int(np.argmax(ac[tmin:hi])) + tmin
        if ac[k] < 0.35:
            continue
        for div in (2, 3):
            kk = k // div
            if tmin <= kk < len(ac) - 1 and ac[kk] > 0.85 * ac[k]:
                k = kk
                break
        if 0 < k < len(ac) - 1:
            a, b, c = ac[k - 1], ac[k], ac[k + 1]
            d = 2 * (2 * b - a - c)
            sh = (c - a) / d if abs(d) > 1e-9 else 0.0
            sh = max(-1.0, min(1.0, sh))
        else:
            sh = 0.0
        out[i] = sr / (k + sh)
    return out


def median_filter(a, w=5):
    out = np.full_like(a, np.nan)
    for i in range(len(a)):
        lo, hi = max(0, i - w // 2), min(len(a), i + w // 2 + 1)
        seg = a[lo:hi]
        seg = seg[~np.isnan(seg)]
        if len(seg):
            out[i] = np.median(seg)
    return out


def contour_of(path, points=24):
    """返回归一化到 [-1,1] 的 F0 轮廓，失败返回 None"""
    pcm = mp3_to_pcm(path)
    if len(pcm) < 2048:
        return None
    f0 = f0_track(trim_silence(pcm))
    idx = np.where(f0 > 0)[0]
    if len(idx) < 6:
        return None
    # 取最长连续有声段
    runs, cur = [], [idx[0]]
    for a, b in zip(idx, idx[1:]):
        if b - a <= 4:
            cur.append(b)
        else:
            runs.append(cur)
            cur = [b]
    runs.append(cur)
    run = max(runs, key=len)
    if len(run) < 6:
        return None
    seg = f0[run[0]: run[-1] + 1].astype(np.float64)
    seg[seg == 0] = np.nan
    sm = median_filter(seg, 5)
    m = ~np.isnan(sm)
    if m.sum() < 5:
        return None
    logf = np.log2(sm[m])
    src = np.linspace(0, 1, len(logf))
    dst = np.linspace(0, 1, points)
    r = np.interp(dst, src, logf)
    if r.std() < 0.004:
        return None
    r = r - np.median(r)
    sc = max(float(np.abs(r).max()), 1e-6)
    return [round(float(v), 4) for v in (r / sc)]


# ---------------------------------------------------------------- 索引

def collect_items(syl_index, only=None):
    items = []

    def add(cat, name, text):
        if only and cat != only:
            return
        items.append((f"{cat}/{name}.mp3", text))

    for syl, chars in syl_index.items():
        if chars:
            add("syl", syl, chars[0])
    for k, v in INITIALS.items():
        add("ini", k, v)
    for k, v in FINALS.items():
        add("fin", k, v)
    for k, v in WHOLE.items():
        add("zy", k, v)
    for k, (word, _img) in WORDS.items():
        add("word", k, word)
    for k, v in VOICE_LINES.items():
        add("vo", k, v)
    return items


def build_index_file(syl_index, no_contour=False):
    index = {"version": 1, "voice": VOICE, "rate": RATE,
             "sampleRate": SAMPLE_RATE, "entries": {}}
    all_items = collect_items(syl_index)
    total = len(all_items)
    done = 0
    for rel, text in all_items:
        path = os.path.join(AUDIO_DIR, rel)
        if not os.path.exists(path):
            continue
        entry = {"t": text}
        if not no_contour and rel.startswith(("syl/", "fin/", "zy/")):
            c = contour_of(path)
            if c:
                entry["c"] = c
        index["entries"][rel[:-4]] = entry
        done += 1
        if done % 150 == 0:
            print(f"  曲线提取 {done}/{total}...", flush=True)

    with open(INDEX_PATH, "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, separators=(",", ":"))
    size = os.path.getsize(INDEX_PATH)
    with_c = sum(1 for e in index["entries"].values() if "c" in e)
    print(f"索引写入 {INDEX_PATH}")
    print(f"  条目 {len(index['entries'])}，含曲线 {with_c}，大小 {size // 1024} KB")


async def amain():
    force = "--force" in sys.argv
    no_contour = "--no-contour" in sys.argv
    only = None
    if "--only" in sys.argv:
        only = sys.argv[sys.argv.index("--only") + 1]

    syl_index = load_syl_index()
    items = collect_items(syl_index, only)
    print(f"待处理 {len(items)} 条（voice={VOICE} rate={RATE} bitrate={BITRATE}）", flush=True)

    stat = await tts_batch(items, force=force)
    print(f"生成完成：新建 {stat['ok']} / 已存在 {stat['skip']} / 失败 {stat['fail']}", flush=True)

    if not only:
        build_index_file(syl_index, no_contour)


if __name__ == "__main__":
    asyncio.run(amain())
