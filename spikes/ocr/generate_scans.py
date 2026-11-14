"""Generate 10 synthetic scans (fake data only) with ground truth for the OCR spike (YOS-22).

Usage: python generate_scans.py   -> writes scans/NN-name.png and scans/NN-name.json
Needs: pillow, arabic-reshaper, python-bidi, and Arial (Windows) or any font with Arabic glyphs.
"""
import json
import random
from pathlib import Path

import arabic_reshaper
from bidi.algorithm import get_display
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = Path(__file__).parent / "scans"
FONT = "C:/Windows/Fonts/arial.ttf"
FONT_B = "C:/Windows/Fonts/arialbd.ttf"
W, H = 1654, 2339  # A4 at 200 dpi


def shape(text):
    return get_display(arabic_reshaper.reshape(text))


# (name, language, size, lines, key fields, degradation)
DOCS = [
    ("01-fr-prescription-clean", "fra", 34, [
        "Cabinet du Dr Sami Ben Youssef", "Médecine générale - Tunis", "",
        "ORDONNANCE", "Date : 02/11/2026", "",
        "Patient : Leila Trabelsi", "Né(e) le 14/03/1985", "",
        "1. Amoxicilline 500 mg, 1 gélule 3 fois par jour pendant 7 jours",
        "2. Paracétamol 1 g, 1 comprimé si douleur ou fièvre",
        "3. Vitamine D3, 1 ampoule par mois", "",
        "Signature et cachet du médecin"],
     ["02/11/2026", "Leila Trabelsi", "Sami Ben Youssef"], {}),
    ("02-fr-lab-result", "fra", 32, [
        "Laboratoire d'analyses médicales El Manar", "Résultats d'analyses", "",
        "Date de prélèvement : 28/10/2026", "Patient : Karim Mansouri", "",
        "Glycémie à jeun : 0,95 g/L (0,70 - 1,10)",
        "Hémoglobine : 13,8 g/dL (13,0 - 17,0)",
        "Cholestérol total : 1,92 g/L (< 2,00)",
        "Créatinine : 9 mg/L (7 - 13)", "",
        "Validé par le Dr Nadia Gharbi, biologiste"],
     ["28/10/2026", "Karim Mansouri", "Nadia Gharbi", "0,95 g/L"], {}),
    ("03-fr-referral-skewed", "fra", 32, [
        "Clinique Les Jasmins - Sfax", "LETTRE D'ORIENTATION", "",
        "Sfax, le 05/11/2026", "Cher confrère,", "",
        "Je vous adresse Mme Salma Jebali, 52 ans,",
        "pour avis cardiologique devant des palpitations",
        "récurrentes depuis trois semaines.", "",
        "ECG de repos normal. Traitement en cours : aucun.",
        "Bien confraternellement,", "Dr Mehdi Karray"],
     ["05/11/2026", "Salma Jebali", "Mehdi Karray"], {"rotate": 2.0, "noise": 18}),
    ("04-fr-consent-lowres", "fra", 32, [
        "FORMULAIRE DE CONSENTEMENT ÉCLAIRÉ", "Hôpital Régional de Sousse", "",
        "Je soussigné(e) Hatem Cherif, né le 22/07/1979,",
        "déclare avoir été informé(e) de la nature de l'intervention",
        "et des risques éventuels, et j'accepte de la subir.", "",
        "Fait à Sousse, le 30/10/2026", "Signature du patient :"],
     ["30/10/2026", "Hatem Cherif"], {"downscale": 0.55, "blur": 1.0}),
    ("05-fr-insurance-small", "fra", 24, [
        "CAISSE NATIONALE D'ASSURANCE MALADIE", "Attestation de prise en charge", "",
        "Assuré : Rania Ben Salah", "N° d'affiliation : 12 345 678 90",
        "Valable jusqu'au 31/12/2026", "Ayants droit : 2", "",
        "Cette attestation est délivrée pour servir et valoir ce que de droit."],
     ["Rania Ben Salah", "12 345 678 90", "31/12/2026"], {"noise": 12}),
    ("06-ar-prescription-clean", "ara", 38, [
        "عيادة الدكتور أحمد بن علي", "طب عام - تونس", "",
        "وصفة طبية", "التاريخ: 02/11/2026", "",
        "المريض: ليلى الطرابلسي", "",
        "1. أموكسيسيلين 500 ملغ ثلاث مرات في اليوم لمدة 7 أيام",
        "2. باراسيتامول 1 غ عند الألم أو الحمى", "",
        "توقيع وختم الطبيب"],
     ["02/11/2026", "ليلى الطرابلسي", "أحمد بن علي"], {}),
    ("07-ar-referral-skewed", "ara", 36, [
        "مصحة الياسمين - صفاقس", "رسالة توجيه", "",
        "صفاقس في 05/11/2026", "زميلي العزيز،", "",
        "أوجه إليكم السيدة سلمى الجبالي للاستشارة",
        "في أمراض القلب بسبب خفقان متكرر منذ ثلاثة أسابيع.", "",
        "تخطيط القلب عادي.", "الدكتور مهدي القراي"],
     ["05/11/2026", "سلمى الجبالي", "مهدي القراي"], {"rotate": -2.0, "noise": 18}),
    ("08-bilingual-lab", "fra+ara", 32, [
        "Laboratoire Central de Tunis", "مخبر التحاليل الطبية", "",
        "Date : 29/10/2026", "التاريخ: 29/10/2026", "",
        "Patient : Youssef Hammami", "المريض: يوسف الحمامي", "",
        "Glycémie : 1,05 g/L", "نسبة السكر في الدم: 1,05 غ/ل", "",
        "Validé par Dr Amel Bouzid"],
     ["29/10/2026", "Youssef Hammami", "يوسف الحمامي", "Amel Bouzid"], {}),
    ("09-fr-poor-fax", "fra", 32, [
        "Centre de Radiologie Carthage", "COMPTE RENDU D'ÉCHOGRAPHIE", "",
        "Date : 01/11/2026", "Patient : Imen Chaabane", "",
        "Échographie abdominale : foie de taille normale,",
        "vésicule biliaire alithiasique, reins sans anomalie.", "",
        "Dr Fares Meddeb, radiologue"],
     ["01/11/2026", "Imen Chaabane", "Fares Meddeb"],
     {"downscale": 0.4, "blur": 1.6, "noise": 45, "rotate": 3.0, "contrast": 0.45}),
    ("10-ar-poor-fax", "ara", 36, [
        "مركز الأشعة قرطاج", "تقرير فحص بالموجات فوق الصوتية", "",
        "التاريخ: 01/11/2026", "المريضة: إيمان الشعباني", "",
        "الفحص البطني: الكبد بحجم طبيعي،", "المرارة سليمة والكليتان دون شذوذ.", "",
        "الدكتور فارس المدب"],
     ["01/11/2026", "إيمان الشعباني", "فارس المدب"],
     {"downscale": 0.4, "blur": 1.6, "noise": 45, "rotate": -3.0, "contrast": 0.45}),
]


def has_arabic(s):
    return any("\u0600" <= c <= "\u06ff" for c in s)


def render(lines, size):
    img = Image.new("L", (W, H), 255)
    d = ImageDraw.Draw(img)
    f, fb = ImageFont.truetype(FONT, size), ImageFont.truetype(FONT_B, size)
    y = 150
    for i, line in enumerate(lines):
        if line:
            if has_arabic(line):
                txt = shape(line)
                x = W - 150 - d.textlength(txt, font=f)  # right-aligned
            else:
                txt, x = line, 150
            d.text((x, y), txt, font=fb if i < 2 else f, fill=0)
        y += int(size * 1.9)
    return img


def degrade(img, o, rng):
    if "contrast" in o:  # grey text on grey paper
        img = img.point(lambda p: int(255 - (255 - p) * o["contrast"] - 10))
    if "rotate" in o:
        img = img.rotate(o["rotate"], expand=False, fillcolor=240, resample=Image.BICUBIC)
    if "downscale" in o:
        s = o["downscale"]
        img = img.resize((int(W * s), int(H * s)), Image.LANCZOS)
    if "blur" in o:
        img = img.filter(ImageFilter.GaussianBlur(o["blur"]))
    if "noise" in o:
        px = img.load()
        for _ in range(img.width * img.height // 12):
            x, y = rng.randrange(img.width), rng.randrange(img.height)
            px[x, y] = max(0, min(255, px[x, y] + rng.randint(-o["noise"] * 3, o["noise"] * 3)))
    return img


def main():
    OUT.mkdir(exist_ok=True)
    rng = random.Random(22)
    for name, lang, size, lines, keys, opts in DOCS:
        img = degrade(render(lines, size), opts, rng)
        img.save(OUT / f"{name}.png", dpi=(200, 200))
        (OUT / f"{name}.json").write_text(json.dumps(
            {"lang": lang, "lines": [l for l in lines if l], "key_fields": keys, "degradation": opts},
            ensure_ascii=False, indent=2), encoding="utf-8")
        print("wrote", name)


if __name__ == "__main__":
    main()
