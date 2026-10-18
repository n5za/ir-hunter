#!/usr/bin/env python3
"""Bundle every supported Flipper-IRDB file covered by its current CC0 notice."""
import hashlib, os, re, shutil, subprocess, sys

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.environ.get("FLIPPER_IRDB", os.path.join(PROJECT, "../Flipper-IRDB"))
DST = os.path.join(PROJECT, "core/data/src/main/assets/codes")
CUTOFF = "2319685f2cbf0cd3f809609622cade14d24fb819"
HEAD = "d126fb1b6f1e114c52b4a8c19839ea65e3a9c24d"
DIR_TO_CATEGORY = {
    "TVs":"TV", "ACs":"AC", "Fans":"FAN", "Projectors":"PROJECTOR", "Cameras":"CAMERA",
    "DVD_Players":"DVD", "Blu-Ray":"DVD", "CD_Players":"DVD", "MiniDisc":"DVD", "VCR":"DVD",
    "Audio_and_Video_Receivers":"AV_RECEIVER", "SoundBars":"AV_RECEIVER", "Speakers":"AV_RECEIVER",
    "Head_Units":"AV_RECEIVER", "Cable_Boxes":"SET_TOP_BOX", "DVB-T":"SET_TOP_BOX",
    "DVB-T2":"SET_TOP_BOX", "TV_Tuner":"SET_TOP_BOX", "Streaming_Devices":"SMART_BOX",
}
UNSAFE_RE = re.compile(r"[^A-Za-z0-9._-]+")

def clean(value): return UNSAFE_RE.sub("_", value).strip("._-") or "unknown"
def category(path): return DIR_TO_CATEGORY.get(path.split("/", 1)[0], "OTHER")

def main():
    if not os.path.isdir(os.path.join(SRC, ".git")):
        sys.exit("Set FLIPPER_IRDB to a local Flipper-IRDB checkout")
    paths = subprocess.check_output([
        "git", "-C", SRC, "diff", "--name-only", "--diff-filter=A", "-M",
        CUTOFF, HEAD, "--", "*.ir"
    ], text=True).splitlines()
    if os.path.isdir(DST): shutil.rmtree(DST)
    os.makedirs(DST, exist_ok=True)
    seen, skipped, copied = set(), [], 0
    for rel in sorted(paths):
        source = os.path.join(SRC, rel)
        try:
            data = open(source, "rb").read(); lines = data.decode("utf-8").splitlines()
        except (OSError, UnicodeDecodeError) as exc:
            skipped.append((rel, str(exc))); continue
        if len(lines) < 2 or lines[0].strip() != "Filetype: IR signals file" or lines[1].strip() != "Version: 1":
            skipped.append((rel, "invalid header")); continue
        digest = hashlib.sha256(data).hexdigest()
        if digest in seen:
            skipped.append((rel, "duplicate")); continue
        seen.add(digest)
        parts = rel.split("/"); brand = clean(parts[1] if len(parts) > 2 else "Unknown")
        model = clean(os.path.splitext(parts[-1])[0]); target_dir = os.path.join(DST, category(rel), brand)
        os.makedirs(target_dir, exist_ok=True); target = os.path.join(target_dir, model + ".ir"); suffix = 2
        while os.path.exists(target):
            target = os.path.join(target_dir, f"{model}_{suffix}.ir"); suffix += 1
        shutil.copyfile(source, target); copied += 1
    print(f"eligible={len(paths)} copied={copied} skipped={len(skipped)}")
    for path, reason in skipped: print(f"SKIP {path}: {reason}")
    return 0

if __name__ == "__main__": sys.exit(main())
