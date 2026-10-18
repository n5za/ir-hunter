#!/usr/bin/env python3
"""Import the full official Flipper Devices MIT IR catalog without caps."""
import os, re, shutil, sys

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.environ.get("FLIPPER_OFFICIAL_IRDB", os.path.join(PROJECT, "../IRDB"))
DST = os.path.join(PROJECT, "core/data/src/main/assets/codes_flipper_mit")
CATEGORY = {
    "TVs":"TV", "A_V_receiver":"AV_RECEIVER", "Air_Purifiers":"OTHER",
    "Box":"SET_TOP_BOX", "Camera":"CAMERA", "DVD":"DVD", "Fan":"FAN",
    "Projector":"PROJECTOR",
}
UNSAFE = re.compile(r"[^A-Za-z0-9._-]+")

def clean(value): return UNSAFE.sub("_", value).strip("._-") or "unknown"

def main():
    base = os.path.join(SRC, "database/categories")
    if not os.path.isfile(os.path.join(SRC, "LICENSE")):
        sys.exit("Set FLIPPER_OFFICIAL_IRDB to a local flipperdevices/IRDB checkout")
    if os.path.isdir(DST): shutil.rmtree(DST)
    copied = 0
    for root, dirs, files in os.walk(base):
        dirs.sort()
        for name in sorted(files):
            if not name.endswith(".ir"): continue
            rel = os.path.relpath(os.path.join(root, name), base).split(os.sep)
            if len(rel) < 4 or rel[0] not in CATEGORY: continue
            category, brand, model = CATEGORY[rel[0]], clean(rel[1]), clean(rel[2])
            target_dir = os.path.join(DST, category, brand)
            os.makedirs(target_dir, exist_ok=True)
            target = os.path.join(target_dir, model + ".ir"); suffix = 2
            while os.path.exists(target):
                target = os.path.join(target_dir, f"{model}_{suffix}.ir"); suffix += 1
            shutil.copyfile(os.path.join(root, name), target); copied += 1
    print(f"copied={copied}")

if __name__ == "__main__": main()
