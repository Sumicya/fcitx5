#!/usr/bin/env python3
"""Pack the official fcitx5 pinyin dictionary into the binary asset the app reads.

Sources:
    lexicon   https://download.fcitx-im.org/data/<tar> -> dict_sc.txt
              LGPL-2.1-or-later (see fcitx/libime REUSE.toml)
    frequency https://github.com/rime/rime-essay -> essay.txt, pinned by commit
              LGPL-3.0, which makes the shipped app LGPL-3.0 as a whole
    variants  OpenCC STCharacters.txt, vendored in scripts/ (Apache-2.0)

Input lines are "hanzi pinyin freq", syllables separated by an apostrophe:
    你好 ni'hao 3.14159

Two modes:
    --explore   download, parse, print statistics and exit non-zero (used to
                inspect the real data from CI, where the log is readable)
    (default)   write app/src/main/assets/pinyin.dict
"""

import argparse
import hashlib
import os
import shutil
import struct
import subprocess
import sys
import tarfile
import tempfile
import urllib.request

TAR_NAME = "dict-20260907.tar.zst"
TAR_URL = "https://download.fcitx-im.org/data/" + TAR_NAME
TAR_SHA256 = "fb75a179065e690dfc4559ce1807cbaf4fbe4f0111a5005615be9f435e6b9d76"
DICT_MEMBER = "dict_sc.txt"

# rime-essay is LGPL-3.0, dict_sc.txt is LGPL-2.1-or-later; shipping both means
# the app as a whole goes out under LGPL-3.0. Pinned by commit because the file
# has no published checksum.
ESSAY_SHA = "054920de4f54c9e5994276a96a4fc2a35cb51aa3"
ESSAY_URL = (
    "https://raw.githubusercontent.com/rime/rime-essay/%s/essay.txt" % ESSAY_SHA
)
OUT_PATH = os.path.join("app", "src", "main", "assets", "pinyin.dict")
ST_SRC = os.path.join("scripts", "st_characters.txt")
ST_OUT = os.path.join("app", "src", "main", "assets", "st.txt")

MAGIC = b"FCPYDIC1"
MAX_SYLLABLES = 7

# syllable ids start at 1 so that zero padding sorts before any real syllable
SYLLABLE_BITS = 9
SYLLABLE_MASK = (1 << SYLLABLE_BITS) - 1

# scores of words the corpus knows live above this, everything else below
CORPUS_FLOOR = 0.5

# a phrase that covers more syllables beats per-character matches, but only just
SYLLABLE_BONUS = 600
REFINE_PASSES = 2


def log(msg):
    print(msg, flush=True)


def download(url, dest, sha256):
    """sha256=None means the file has no published checksum: it is pinned by URL."""
    if sha256 and os.path.exists(dest) and hashlib.sha256(open(dest, "rb").read()).hexdigest() == sha256:
        log("using cached %s" % dest)
        return
    log("downloading %s" % url)
    tmp = dest + ".part"
    with urllib.request.urlopen(url, timeout=300) as r, open(tmp, "wb") as f:
        shutil.copyfileobj(r, f)
    if sha256:
        got = hashlib.sha256(open(tmp, "rb").read()).hexdigest()
        if got != sha256:
            os.remove(tmp)
            sys.exit("sha256 mismatch for %s\n  expected %s\n  got      %s" % (url, sha256, got))
    os.replace(tmp, dest)
    log("downloaded %s (%d bytes)" % (dest, os.path.getsize(dest)))


def untar_zst(archive, workdir):
    """zstd is not in tarfile before 3.14; prefer the zstd binary, fall back to pip."""
    plain = os.path.join(workdir, "dict.tar")
    if shutil.which("zstd"):
        subprocess.run(["zstd", "-d", "-f", "-q", archive, "-o", plain], check=True)
    else:
        try:
            import zstandard
        except ImportError:
            subprocess.run([sys.executable, "-m", "pip", "install", "--quiet", "zstandard"], check=True)
            import zstandard
        with open(archive, "rb") as src, open(plain, "wb") as dst:
            zstandard.ZstdDecompressor().copy_stream(src, dst)
    with tarfile.open(plain) as tar:
        names = tar.getnames()
        log("archive members: %s" % ", ".join(names))
        member = next((n for n in names if os.path.basename(n) == DICT_MEMBER), None)
        if member is None:
            sys.exit("%s not found in %s" % (DICT_MEMBER, archive))
        tar.extract(member, workdir)
        return os.path.join(workdir, member)


def parse(path):
    """Yield (word, [syllable, ...], freq)."""
    kept = skipped = 0
    with open(path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            tokens = line.split()
            if len(tokens) not in (2, 3):
                skipped += 1
                continue
            word, pinyin = tokens[0], tokens[1]
            syllables = pinyin.split("'")
            if not word or not all(syllables) or len(syllables) > MAX_SYLLABLES:
                skipped += 1
                continue
            try:
                freq = float(tokens[2]) if len(tokens) == 3 else 0.0
            except ValueError:
                skipped += 1
                continue
            kept += 1
            yield word, syllables, freq
    log("parsed %d entries, skipped %d lines" % (kept, skipped))


def report(entries, path):
    """A short summary; the point is to notice when upstream data changes."""
    if not entries:
        sys.exit("no entries parsed")
    known = [f for _, _, f in entries if f != 0.0]
    log("entries=%d source=%d bytes with_freq=%d" % (len(entries), os.path.getsize(path), len(known)))
    by_word = {}
    for word, syl, freq in entries:
        by_word.setdefault(word, []).append((" ".join(syl), freq))
    for probe in ("的", "我", "你好", "输入法"):
        log("probe %s -> %s" % (probe, by_word.get(probe)))
    lens = {}
    for word, _, _ in entries:
        lens[len(word)] = lens.get(len(word), 0) + 1
    log("word length histogram: %s" % dict(sorted(lens.items())))


def build_scorer(entries, essay=None):
    """Rank words, corpus counts first.

    Anything with a real corpus count outranks anything without one; the
    heuristic below only orders the words essay.txt never saw. The two bands
    are kept apart by CORPUS_FLOOR.
    """

    heuristic = build_heuristic(entries)

    if essay:
        import math

        top = math.log1p(max(essay.values()))
        bottom = math.log1p(min(essay.values()))

        def corpus(word):
            count = essay.get(word)
            if not count:
                return None
            weight = (math.log1p(count) - bottom) / ((top - bottom) or 1.0)
            return CORPUS_FLOOR + (1.0 - CORPUS_FLOOR) * weight

    def fall_back(word, freq):
        return (1.0 - CORPUS_FLOOR) * heuristic(word, freq)

    if not essay:
        return heuristic

    def score(word, freq):
        value = corpus(word)
        return value if value is not None else fall_back(word, freq)

    return score


def build_heuristic(entries):
    """Rank words with no corpus at hand.

    Two weak signals, averaged because neither survives on its own:

    1. how many entries a character appears in, and how common the entries it
       appears in are. Counts alone rank 尼 (a thousand place names) above 你
       (你好, 你们); averaging in the quality of the containing entries pulls
       characters back that only show up in a few very common words.
    2. the frequency column of the source, for the ~0.5% of lines that carry
       one. Only negative values are used: positive ones are artifacts of
       characters whose whole count was attached to a rare reading (挝 as in
       老挝), and even the negative ones are only half trustworthy (they claim
       倭 outranks 涡), so they never get more weight than the first signal.
    """
    import math
    from collections import Counter

    def normalise(values):
        lo, hi = min(values.values()), max(values.values())
        span = (hi - lo) or 1.0
        return {k: (v - lo) / span for k, v in values.items()}

    counts = Counter()
    for word, _, _ in entries:
        for ch in set(word):
            counts[ch] += 1
    spread = normalise({ch: math.log1p(n) for ch, n in counts.items()})

    totals = Counter()
    seen = Counter()
    for word, _, _ in entries:
        quality = sum(spread.get(ch, 0.0) for ch in word) / len(word)
        for ch in set(word):
            totals[ch] += quality
            seen[ch] += 1
    company = normalise({ch: totals[ch] / seen[ch] for ch in seen})

    char = {ch: 0.5 * spread.get(ch, 0.0) + 0.5 * company.get(ch, 0.0) for ch in counts}

    known = [f for _, _, f in entries if f < 0.0]
    flo, fhi = (min(known), max(known)) if known else (0.0, 1.0)
    fspan = (fhi - flo) or 1.0
    log("ranked with %d source character frequencies" % len(known))

    def score(word, freq):
        p = sum(char.get(ch, 0.0) for ch in word) / len(word)
        f = (freq - flo) / fspan if freq < 0.0 else p
        return 0.5 * (f + p)

    return score


def pack(entries, out_path, max_per_key=40, min_freq=None, essay=None):
    entries = list(entries)
    syllables = sorted({s for _, syl, _ in entries for s in syl})
    if len(syllables) > SYLLABLE_MASK:
        sys.exit("too many distinct syllables: %d" % len(syllables))
    syl_id = {s: i + 1 for i, s in enumerate(syllables)}
    log("distinct syllables: %d" % len(syllables))

    score = build_scorer(entries, essay)

    by_key = {}
    for word, syl, freq in entries:
        key = 0
        for i in range(MAX_SYLLABLES):
            key <<= SYLLABLE_BITS
            if i < len(syl):
                key |= syl_id[syl[i]]
        bucket = by_key.setdefault(key, (len(syl), []))
        bucket[1].append((word, freq))
    log("distinct pinyin keys: %d" % len(by_key))

    keys = sorted(by_key)
    key_bytes = bytearray()
    data = bytearray()
    offsets = []
    dropped = 0
    for key in keys:
        nsyl, raw_items = by_key[key]
        if min_freq is not None:
            raw_items = [it for it in raw_items if it[1] >= min_freq]
        # ponytail: only the top N homophones are shipped, the tail is noise
        items = sorted(
            raw_items,
            key=lambda it: -(min(0xFFFF, int(0xFFFF * score(it[0], it[1])) + SYLLABLE_BONUS * (nsyl - 1))),
        )
        if len(items) > max_per_key:
            dropped += len(items) - max_per_key
            items = items[:max_per_key]
        if not items:
            continue
        key_bytes += struct.pack("<Q", key)
        offsets.append(len(data))
        data += struct.pack("<H", len(items))
        for word, freq in items:
            raw = word.encode("utf-8")
            value = min(0xFFFF, int(0xFFFF * score(word, freq)) + SYLLABLE_BONUS * (nsyl - 1))
            data += struct.pack("<B", len(raw)) + raw + struct.pack("<H", value)
    log("shipped %d keys, dropped %d low ranked homophones" % (len(offsets), dropped))
    if essay:
        covered = sum(1 for word, _, _ in entries if word in essay)
        log("corpus covers %d/%d entries (%.0f%%)" % (covered, len(entries), 100.0 * covered / len(entries)))
        for probe in ("的", "你", "我", "好", "你好", "我们"):
            log("essay %s -> %s" % (probe, essay.get(probe)))

    header = bytearray()
    header += MAGIC
    header += struct.pack("<I", len(offsets))
    header += struct.pack("<I", 0)  # placeholder: data start
    header += struct.pack("<H", len(syllables))
    for s in syllables:
        raw = s.encode("ascii")
        header += struct.pack("<B", len(raw)) + raw
    while len(header) % 8:
        header += b"\0"

    keys_start = len(header)
    offsets_start = keys_start + len(key_bytes)
    data_start = offsets_start + 4 * len(offsets)
    blob = bytearray()
    blob += header
    blob += key_bytes
    for off in offsets:
        blob += struct.pack("<I", off)
    blob += data
    struct.pack_into("<I", blob, 8 + 4, data_start)

    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "wb") as fh:
        fh.write(blob)
    log("wrote %s (%d bytes, data section starts at %d)" % (out_path, len(blob), data_start))


def load_variants():
    """(simplified, traditional) pairs from the vendored OpenCC table."""
    if not os.path.exists(ST_SRC):
        sys.exit("missing %s" % ST_SRC)
    pairs = []
    with open(ST_SRC, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) < 2 or len(parts[0]) != 1:
                continue
            values = parts[1].split()
            if values:
                pairs.append((parts[0], values[0]))
    return pairs


def pack_variants(pairs):
    """Simplified -> traditional, one character per line, for Trad.kt."""
    os.makedirs(os.path.dirname(ST_OUT), exist_ok=True)
    with open(ST_OUT, "w", encoding="utf-8") as fh:
        fh.write("\n".join("%s\t%s" % pair for pair in pairs) + "\n")
    log("wrote %s (%d mappings)" % (ST_OUT, len(pairs)))


def load_essay(path, pairs):
    """word -> corpus count, folded to simplified so it matches dict_sc.txt.

    essay.txt is written in traditional characters, so every word is mapped
    back through the inverted OpenCC table before it is counted.
    """
    # ponytail: the table is not injective (干/乾/幹), so the fold is lossy;
    # collisions just add their counts together.
    t2s = {}
    for simplified, traditional in pairs:
        t2s.setdefault(traditional, simplified)
    counts = {}
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            parts = line.split()
            if len(parts) != 2 or not parts[1].isdigit():
                continue
            count = int(parts[1])
            if count <= 0:
                continue
            word = "".join(t2s.get(ch, ch) for ch in parts[0])
            counts[word] = counts.get(word, 0) + count
    log("essay: %d simplified words with a corpus count, top=%d" % (len(counts), max(counts.values())))
    return counts


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--max-per-key", type=int, default=40)
    ap.add_argument("--min-freq", type=float, default=None)
    ap.add_argument("--no-essay", action="store_true", help="rank without the corpus")
    args = ap.parse_args()

    workdir = tempfile.mkdtemp(prefix="fcitx5-dict-")
    archive = os.path.join(workdir, TAR_NAME)
    download(TAR_URL, archive, TAR_SHA256)
    txt = untar_zst(archive, workdir)
    log("dictionary text: %s (%d bytes)" % (txt, os.path.getsize(txt)))

    variants = load_variants()
    pack_variants(variants)

    essay = None
    if not args.no_essay:
        essay_path = os.path.join(workdir, "essay.txt")
        download(ESSAY_URL, essay_path, None)
        log("essay text: %s (%d bytes)" % (essay_path, os.path.getsize(essay_path)))
        essay = load_essay(essay_path, variants)

    entries = list(parse(txt))
    report(entries, txt)
    pack(entries, OUT_PATH, args.max_per_key, args.min_freq, essay)
    shutil.rmtree(workdir, ignore_errors=True)


if __name__ == "__main__":
    main()
