"""Score results/<engine>/*.json against scans/*.json: CER, key-field recall, confidence, and the NEEDS_REVIEW gate."""
import json, re, sys, unicodedata
from pathlib import Path

ROOT = Path(__file__).parent
THRESHOLD = float(sys.argv[1]) if len(sys.argv) > 1 else 0.70  # same default as documents.min-confidence


def norm(s):
    s = unicodedata.normalize("NFKC", s)
    s = re.sub(r"[\u064b-\u065f\u0640]", "", s)  # Arabic diacritics / tatweel
    return re.sub(r"\s+", " ", s).strip().lower()


def lev(a, b):
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def compact(s):  # order/whitespace-insensitive for CER (line order differs between engines)
    return "".join(sorted(norm(s).replace(" ", "")))


def cer(truth, ocr):
    t, o = norm(truth), norm(ocr)
    return lev(t, o) / max(1, len(t))


rows = []
for engine_dir in sorted((ROOT / "results").iterdir()):
    for r in sorted(engine_dir.glob("*.json")):
        meta = json.loads((ROOT / "scans" / r.name).read_text(encoding="utf-8"))
        res = json.loads(r.read_text(encoding="utf-8"))
        truth = "\n".join(meta["lines"])
        ocr_n = norm(res["text"])
        hit = sum(norm(k) in ocr_n for k in meta["key_fields"])
        rows.append(dict(engine=engine_dir.name, doc=r.stem, cer=cer(truth, res["text"]),
                         fields=f"{hit}/{len(meta['key_fields'])}", fhit=hit, ftot=len(meta["key_fields"]),
                         conf=res["confidence"], secs=res["seconds"],
                         route="OK" if res["confidence"] >= THRESHOLD else "NEEDS_REVIEW"))

print(f"threshold={THRESHOLD}")
print(f"{'engine':10} {'doc':28} {'CER':>6} {'fields':>7} {'conf':>6} {'sec':>6}  route")
for x in rows:
    print(f"{x['engine']:10} {x['doc']:28} {x['cer']:6.2f} {x['fields']:>7} {x['conf']:6.2f} {x['secs']:6.1f}  {x['route']}")
print()
for e in sorted({x["engine"] for x in rows}):
    xs = [x for x in rows if x["engine"] == e]
    print(f"{e}: mean CER {sum(x['cer'] for x in xs)/len(xs):.3f} | key fields "
          f"{sum(x['fhit'] for x in xs)}/{sum(x['ftot'] for x in xs)} | mean {sum(x['secs'] for x in xs)/len(xs):.1f}s/page "
          f"| routed to NEEDS_REVIEW {sum(x['route']!='OK' for x in xs)}/{len(xs)}")
