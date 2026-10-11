"""Generate synthetic Thai bank slip images and negative samples for testing.

The slip text is representative of Thai transfer slips, not copied from a
real bank slip. The .txt files carry the same text the PNGs render, so JVM
rule tests and the on-device OCR check stay aligned.

Run: python3 testdata/make_slips.py
"""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).parent / "slips"
FONT_DIR = Path("/usr/share/fonts/noto")

PREFER = [
    "NotoSansThai-Medium.ttf",
    "NotoSansThai-Regular.ttf",
    "NotoSansThai-SemiBold.ttf",
    "NotoSansThaiLooped-Regular.ttf",
    "NotoSansThaiLooped-Medium.ttf",
]

LATIN_PREFER = [
    "NotoSans-Medium.ttf",
    "NotoSans-Regular.ttf",
    "NotoSans-SemiBold.ttf",
]


def font_path(weight_hint: str) -> Path:
    for name in PREFER:
        p = FONT_DIR / name
        if p.exists():
            return p
    matches = sorted(FONT_DIR.glob("NotoSansThai*.ttf"))
    if not matches:
        raise SystemExit("no Noto Sans Thai font found in /usr/share/fonts/noto")
    for m in matches:
        if weight_hint.lower() in m.name.lower():
            return m
    return matches[0]


def load(size: int, weight_hint: str = "regular") -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(font_path(weight_hint)), size)


def latin_font_path(weight_hint: str) -> Path:
    for name in LATIN_PREFER:
        p = FONT_DIR / name
        if p.exists():
            return p
    matches = sorted(FONT_DIR.glob("NotoSans-*.ttf"))
    if not matches:
        raise SystemExit("no Noto Sans latin font found in /usr/share/fonts/noto")
    for m in matches:
        if weight_hint.lower() in m.name.lower():
            return m
    return matches[0]


def load_latin(size: int, weight_hint: str = "regular") -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(latin_font_path(weight_hint)), size)


def is_thai(ch: str) -> bool:
    return "\u0e00" <= ch <= "\u0e7f"


def draw_mixed(
    d: ImageDraw.ImageDraw,
    xy: tuple[int, int],
    text: str,
    thai: ImageFont.FreeTypeFont,
    latin: ImageFont.FreeTypeFont,
    fill,
) -> None:
    """Draw text, switching fonts between Thai and Latin runs."""
    x, y = xy
    run = ""
    run_thai = None

    def flush() -> None:
        nonlocal x, run, run_thai
        if not run:
            return
        f = thai if run_thai else latin
        d.text((x, y), run, font=f, fill=fill)
        x += d.textlength(run, font=f)
        run = ""

    for ch in text:
        t = is_thai(ch)
        if run and t != run_thai and not ch.isspace():
            flush()
        if run_thai is None or not run:
            run_thai = t
        run += ch
    flush()


SLIPS = {
    "scb_transfer": {
        "bank": "ธนาคารไทยพาณิชย์",
        "accent": (78, 44, 140),
        "dark": False,
        "lines": [
            "โอนเงินสำเร็จ",
            "Transfer Successful",
            "",
            "จาก: นายสมชาย ใจดี",
            "พร้อมเพย์ 0-9876-543-2",
            "ไปที่: ร้านอาหารครัวอร่อย",
            "พร้อมเพย์ 1-2345-678-9",
            "",
            "จำนวนเงิน: ฿350.00",
            "ค่าธรรมเนียม: ฿0.00",
            "",
            "วันที่: 11 ต.ค. 2026 14:32",
            "เลขอ้างอิง: 20261011000123456789",
        ],
    },
    "kbank_transfer": {
        "bank": "K PLUS by Kasikornbank",
        "accent": (19, 122, 75),
        "dark": False,
        "lines": [
            "โอนเงินผ่าน K PLUS",
            "Transfer via K PLUS",
            "",
            "ถึงบัญชี: นางสาวปิยะดา วงศ์สุข",
            "ธนาคารกสิกรไทย",
            "บัญชีออมทรัพย์ x-x-x-x-x8901",
            "",
            "จำนวนเงิน 1,250.00 บาท",
            "ค่าธรรมเนียม 0.00 บาท",
            "",
            "วันที่ทำรายการ: 10 ต.ค. 2026 09:15",
            "เลขที่อ้างอิง: KT26101009150042",
        ],
    },
    "promptpay_dark": {
        "bank": "พร้อมเพย์",
        "accent": (20, 24, 40),
        "dark": True,
        "lines": [
            "โอนสำเร็จ",
            "",
            "ผู้รับ: มูลนิธิช่วยชาวประมง",
            "เบอร์โทร x-x-x1234",
            "",
            "45.50 บาท",
            "",
            "วันเวลา: 09 ต.ค. 2026 18:44",
            "Ref: PP26091234450987",
        ],
    },
}

NEGATIVES = {
    "chat_negative": {
        "bank": "LINE",
        "accent": (90, 110, 140),
        "dark": False,
        "lines": [
            "แม่: เย็นนี้กินข้าวที่ไหน",
            "ผม: ที่บ้านก็ได้ครับ",
            "แม่: ซื้อผักมาด้วยนะ",
            "ผม: เดี๋ยวโอนให้ครับ อิ่มแล้ว",
            "แม่: อย่าลืมซื้อนม",
            "ผม: ครับ",
        ],
    },
    "settings_negative": {
        "bank": "Settings",
        "accent": (70, 70, 78),
        "dark": True,
        "lines": [
            "Settings",
            "Network & internet",
            "Connected devices",
            "Apps",
            "Notifications",
            "Battery",
            "Storage",
        ],
    },
}


def render(name: str, spec: dict, width: int = 1080) -> None:
    body = load(46)
    body_latin = load_latin(44)
    header = load(58, "semibold")
    header_latin = load_latin(52, "semibold")
    big = load(78, "semibold")
    big_latin = load_latin(70, "semibold")
    pad = 64
    line_h = 78
    height = pad * 3 + 90 + line_h * len(spec["lines"])
    img = Image.new("RGB", (width, height), (18, 18, 22) if spec["dark"] else (246, 247, 250))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, width, 130], fill=spec["accent"])
    fg_head = (255, 255, 255)
    d.text((pad, 36), spec["bank"], font=header, fill=fg_head)
    fg = (240, 240, 245) if spec["dark"] else (34, 38, 46)
    y = 190
    for line in spec["lines"]:
        if not line:
            y += line_h // 2
            continue
        is_big = line.startswith("จำนวน") or ("บาท" in line.split(" ")[0] and name != "settings_negative")
        tf = big if is_big else body
        lf = big_latin if is_big else body_latin
        draw_mixed(d, (pad, y), line, tf, lf, fg)
        y += line_h + (26 if is_big else 0)
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "text").mkdir(parents=True, exist_ok=True)
    img.save(OUT / f"{name}.png")
    (OUT / "text" / f"{name}.txt").write_text(
        "\n".join([spec["bank"]] + spec["lines"]), encoding="utf-8"
    )
    print(f"{name}.png  {width}x{height}")


for n, s in SLIPS.items():
    render(n, s)
for n, s in NEGATIVES.items():
    render(n, s)
