FROM python:3.11-slim
RUN apt-get update && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-fra tesseract-ocr-ara \
    libgl1 libglib2.0-0 && rm -rf /var/lib/apt/lists/*
RUN pip install --no-cache-dir torch torchvision --index-url https://download.pytorch.org/whl/cpu \
 && pip install --no-cache-dir easyocr pytesseract pillow numpy
# Pre-download EasyOCR models so runs are offline and timing excludes the download.
RUN python -c "import easyocr; easyocr.Reader(['fr','en']); easyocr.Reader(['ar','en'])"
WORKDIR /work
