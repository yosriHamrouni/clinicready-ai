"""Run Tesseract and EasyOCR over scans/*.png; write results/<engine>/<name>.json (text, confidence, seconds)."""
import json, statistics, sys, time
from pathlib import Path

import easyocr, pytesseract
from PIL import Image

ROOT = Path("/work")
scans = sorted((ROOT / "scans").glob("*.png"))
readers = {}


def easy_langs(lang):
    return {"fra": ["fr", "en"], "ara": ["ar", "en"], "fra+ara": None}[lang]


def tesseract(img, lang):
    t0 = time.time()
    d = pytesseract.image_to_data(img, lang=lang, output_type=pytesseract.Output.DICT)
    words = [(w, float(c)) for w, c in zip(d["text"], d["conf"]) if w.strip() and float(c) >= 0]
    return d, words, time.time() - t0


def run_tesseract(img, lang):
    d, words, secs = tesseract(img, lang)
    text = pytesseract.image_to_string(img, lang=lang)
    conf = statistics.mean(c for _, c in words) / 100 if words else 0.0
    return text, conf, secs


def run_easy(img, lang):
    import numpy as np
    t0 = time.time()
    groups = [["fr", "en"], ["ar", "en"]] if lang == "fra+ara" else [easy_langs(lang)]
    res = []
    for g in groups:
        key = tuple(g)
        readers.setdefault(key, easyocr.Reader(g, gpu=False, verbose=False))
        res += readers[key].readtext(np.array(img), paragraph=False)
    # Order top-to-bottom, merge into lines
    res.sort(key=lambda r: (round(r[0][0][1] / 25), -r[0][0][0] if "ar" in g else r[0][0][0]))
    text = "\n".join(r[1] for r in res)
    conf = statistics.mean(r[2] for r in res) if res else 0.0
    return text, conf, time.time() - t0


for engine, fn, tlang in (("tesseract", run_tesseract, None), ("easyocr", run_easy, None)):
    out = ROOT / "results" / engine
    out.mkdir(parents=True, exist_ok=True)
    for p in scans:
        meta = json.loads(p.with_suffix(".json").read_text(encoding="utf-8"))
        lang = meta["lang"]
        img = Image.open(p)
        text, conf, secs = fn(img, "+".join(lang.split("+")) if engine == "tesseract" else lang)
        (out / f"{p.stem}.json").write_text(json.dumps(
            {"text": text, "confidence": round(conf, 4), "seconds": round(secs, 2)},
            ensure_ascii=False, indent=2), encoding="utf-8")
        print(engine, p.stem, round(conf, 3), round(secs, 1), flush=True)
