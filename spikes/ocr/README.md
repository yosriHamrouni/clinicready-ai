# OCR engine spike (YOS-22)

Compares Tesseract (fra + ara) with EasyOCR on 10 synthetic FR/AR scans (fake data only).

**Status: scaffolding only. The engines have not been run yet, so there are no accuracy numbers and no go/no-go.**

## Run

```
python generate_scans.py                 # writes scans/ (needs pillow, arabic-reshaper, python-bidi, Arial)
docker build -t ocr-spike .
docker run --rm -v "$PWD":/work ocr-spike python run_ocr.py
python score.py 0.70                     # CER, key-field recall, confidence, NEEDS_REVIEW routing
```

Scans 09 and 10 are deliberately near-unreadable: they should fall below `documents.min-confidence` (0.7) and route to `NEEDS_REVIEW`.
