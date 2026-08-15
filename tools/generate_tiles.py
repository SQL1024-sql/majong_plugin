#!/usr/bin/env python3
"""Draws the mahjong tile glyphs and writes the resource pack that ships them.

Run from the repository root:

    python3 tools/generate_tiles.py

Everything it writes is committed, so this only needs re-running when the tile
art changes. Needs Pillow and a CJK font; WenQuanYi Zen Hei is used because its
hand tuned bitmaps stay sharp at twelve pixels, which is the size the character
tiles are drawn at.
"""
import json
import os
import struct
import zlib

from PIL import Image, ImageDraw, ImageFont

CELL_W, CELL_H = 16, 20
COLS = 9
FIRST_CODEPOINT = 0xE000

FONT_CANDIDATES = [
    "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
    "/usr/share/fonts/opentype/unifont/unifont.otf",
    "/etc/alternatives/fonts-japanese-gothic.ttf",
]
FONT_SIZE = 12

# The resource pack format 26.1 ships with; the range keeps neighbouring
# versions from complaining that the pack is out of date.
PACK_FORMAT = 84
PACK_FORMAT_MIN = 32
PACK_FORMAT_MAX = 120

BORDER = (58, 42, 30, 255)
FACE = (245, 238, 220, 255)
FACE_AKA = (250, 224, 224, 255)
SHADOW = (196, 176, 140, 255)
CLEAR = (0, 0, 0, 0)

MAN = (168, 32, 32, 255)
PIN = (32, 72, 160, 255)
PIN_CENTRE = (176, 32, 32, 255)
SOU = (28, 118, 52, 255)
AKA = (228, 40, 40, 255)
WIND = (36, 56, 120, 255)
HAKU = (40, 84, 152, 255)
HATSU = (24, 120, 60, 255)
CHUN = (184, 32, 32, 255)

# The drawable area inside the tile face.
ART_X, ART_Y, ART_W, ART_H = 2, 2, 12, 14

# Where the pips sit on a three by three grid, per count.
PIP_LAYOUT = {
    1: [(1, 1)],
    2: [(1, 0), (1, 2)],
    3: [(0, 0), (1, 1), (2, 2)],
    4: [(0, 0), (2, 0), (0, 2), (2, 2)],
    5: [(0, 0), (2, 0), (1, 1), (0, 2), (2, 2)],
    6: [(0, 0), (2, 0), (0, 1), (2, 1), (0, 2), (2, 2)],
    7: [(0, 0), (1, 0), (2, 0), (0, 1), (2, 1), (0, 2), (2, 2)],
    8: [(0, 0), (1, 0), (2, 0), (0, 1), (2, 1), (0, 2), (1, 2), (2, 2)],
    9: [(0, 0), (1, 0), (2, 0), (0, 1), (1, 1), (2, 1), (0, 2), (1, 2), (2, 2)],
}

NUMERALS = "一二三四五六七八九"
HONOURS = "東南西北白發中"
HONOUR_COLOURS = [WIND, WIND, WIND, WIND, HAKU, HATSU, CHUN]


def load_font():
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            return ImageFont.truetype(path, FONT_SIZE), path
    raise SystemExit("no CJK font found; install fonts-wqy-zenhei")


FONT, FONT_USED = load_font()


def blank_tile(aka=False):
    """A tile face: dark outline, cream front and a shaded bottom right edge."""
    img = Image.new("RGBA", (CELL_W, CELL_H), CLEAR)
    draw = ImageDraw.Draw(img)
    draw.rectangle([0, 0, CELL_W - 2, CELL_H - 2], fill=FACE_AKA if aka else FACE, outline=BORDER)
    for corner in [(0, 0), (CELL_W - 2, 0), (0, CELL_H - 2), (CELL_W - 2, CELL_H - 2)]:
        img.putpixel(corner, CLEAR)
    draw.line([(CELL_W - 1, 1), (CELL_W - 1, CELL_H - 1)], fill=SHADOW)
    draw.line([(1, CELL_H - 1), (CELL_W - 1, CELL_H - 1)], fill=SHADOW)
    return img


def draw_character(img, char, colour):
    """Stamps a character with no antialiasing so it stays crisp."""
    mask = Image.new("1", (CELL_W * 2, CELL_H * 2), 0)
    ImageDraw.Draw(mask).text((4, 4), char, font=FONT, fill=1)
    box = mask.getbbox()
    if box is None:
        raise SystemExit(f"the font has no glyph for {char}")
    ink = mask.crop(box)
    img.paste(colour, (ART_X + (ART_W - ink.width) // 2,
                       ART_Y + (ART_H - ink.height) // 2), ink)


def pip_origin(col, row, size, pitch_x, pitch_y):
    x = ART_X + (ART_W - (2 * pitch_x + size)) // 2 + col * pitch_x
    y = ART_Y + (ART_H - (2 * pitch_y + size)) // 2 + row * pitch_y
    return x, y


def draw_circle(img, x, y, colour, size=3):
    """A filled square with softened corners reads as a circle at this size."""
    draw = ImageDraw.Draw(img)
    draw.rectangle([x, y, x + size - 1, y + size - 1], fill=colour)
    soft = tuple(int(c * 0.45 + f * 0.55) for c, f in zip(colour, FACE))
    for corner in [(x, y), (x + size - 1, y), (x, y + size - 1), (x + size - 1, y + size - 1)]:
        img.putpixel(corner, soft)


def man_tile(rank, aka=False):
    img = blank_tile(aka)
    draw_character(img, NUMERALS[rank - 1], AKA if aka else MAN)
    return img


def pin_tile(rank, aka=False):
    img = blank_tile(aka)
    if rank == 1:
        draw = ImageDraw.Draw(img)
        x = ART_X + (ART_W - 7) // 2
        y = ART_Y + (ART_H - 7) // 2
        draw.ellipse([x, y, x + 6, y + 6], fill=PIN)
        draw.ellipse([x + 2, y + 2, x + 4, y + 4], fill=PIN_CENTRE)
        return img
    for col, row in PIP_LAYOUT[rank]:
        x, y = pip_origin(col, row, 3, 4, 4)
        draw_circle(img, x, y, AKA if aka else PIN)
    return img


def sou_tile(rank, aka=False):
    img = blank_tile(aka)
    if rank == 1:
        x = ART_X + (ART_W - 3) // 2
        y = ART_Y + 2
        ImageDraw.Draw(img).rectangle([x, y, x + 2, y + 9], fill=AKA if aka else SOU)
        img.putpixel((x + 1, y + 4), FACE)
        return img
    for col, row in PIP_LAYOUT[rank]:
        x, y = pip_origin(col, row, 2, 4, 5)
        ImageDraw.Draw(img).rectangle([x, y, x + 1, y + 3], fill=AKA if aka else SOU)
    return img


def honour_tile(index):
    img = blank_tile()
    if HONOURS[index] == "白":
        # The white dragon is a blank face, so draw the frame and nothing else.
        ImageDraw.Draw(img).rectangle(
            [ART_X + 1, ART_Y + 1, ART_X + ART_W - 2, ART_Y + ART_H - 2],
            outline=HONOUR_COLOURS[index])
        return img
    draw_character(img, HONOURS[index], HONOUR_COLOURS[index])
    return img


def build_glyphs():
    glyphs = []
    for rank in range(1, 10):
        glyphs.append((f"{rank}m", man_tile(rank)))
    for rank in range(1, 10):
        glyphs.append((f"{rank}p", pin_tile(rank)))
    for rank in range(1, 10):
        glyphs.append((f"{rank}s", sou_tile(rank)))
    for index in range(7):
        glyphs.append((f"{index + 1}z", honour_tile(index)))
    glyphs.append(("0m", man_tile(5, True)))
    glyphs.append(("0p", pin_tile(5, True)))
    glyphs.append(("0s", sou_tile(5, True)))
    return glyphs


def write_png(path, image):
    """Writes a PNG without the timestamp chunk so rebuilds stay reproducible."""
    width, height = image.size
    raw = b"".join(b"\x00" + image.tobytes()[y * width * 4:(y + 1) * width * 4]
                   for y in range(height))

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as handle:
        handle.write(png)


def main():
    glyphs = build_glyphs()
    rows = (len(glyphs) + COLS - 1) // COLS
    atlas = Image.new("RGBA", (CELL_W * COLS, CELL_H * rows), CLEAR)
    for index, (_, tile) in enumerate(glyphs):
        atlas.paste(tile, ((index % COLS) * CELL_W, (index // COLS) * CELL_H))

    pack = "src/main/resources/resourcepack"
    os.makedirs(f"{pack}/assets/majong/textures/font", exist_ok=True)
    os.makedirs(f"{pack}/assets/majong/font", exist_ok=True)
    write_png(f"{pack}/assets/majong/textures/font/tiles.png", atlas)

    # A zero codepoint marks a cell the font should skip.
    chars = []
    for row in range(rows):
        line = ""
        for col in range(COLS):
            index = row * COLS + col
            line += chr(FIRST_CODEPOINT + index) if index < len(glyphs) else chr(0)
        chars.append(line)

    with open(f"{pack}/assets/majong/font/tiles.json", "w", encoding="utf-8") as handle:
        json.dump({"providers": [{
            "type": "bitmap",
            "file": "majong:font/tiles.png",
            "height": CELL_H,
            "ascent": 15,
            "chars": chars,
        }]}, handle, ensure_ascii=False, indent=2)
        handle.write("\n")

    with open(f"{pack}/pack.mcmeta", "w", encoding="utf-8") as handle:
        json.dump({"pack": {
            "pack_format": PACK_FORMAT,
            "supported_formats": {"min_inclusive": PACK_FORMAT_MIN,
                                  "max_inclusive": PACK_FORMAT_MAX},
            "description": "Mahjong tiles for MajongPlugin",
        }}, handle, indent=2)
        handle.write("\n")

    icon = Image.new("RGBA", (64, 64), (32, 24, 18, 255))
    for index, name in enumerate(["1m", "5p", "9s", "1z", "7z", "5z", "3s", "0p", "6m"]):
        tile = dict(glyphs)[name]
        big = tile.resize((tile.width * 2, tile.height * 2), Image.NEAREST)
        icon.alpha_composite(big, (2 + (index % 3) * 21, 1 + (index // 3) * 21))
    write_png(f"{pack}/pack.png", icon)

    os.makedirs("docs", exist_ok=True)
    preview = Image.new("RGBA", (atlas.width, atlas.height), (28, 28, 32, 255))
    preview.alpha_composite(atlas)
    write_png("docs/tiles.png",
              preview.resize((preview.width * 4, preview.height * 4), Image.NEAREST))

    # A sample hand, laid out the way a player's own tiles appear in chat.
    sample = ["1m", "2m", "3m", "4p", "5p", "0p", "7s", "8s", "9s", "1z", "1z", "5z", "5z", "7z"]
    by_name = dict(glyphs)
    row = Image.new("RGBA", ((CELL_W + 1) * len(sample) + 8, CELL_H + 6), (28, 28, 32, 255))
    for index, name in enumerate(sample):
        row.alpha_composite(by_name[name], (4 + index * (CELL_W + 1), 3))
    write_png("docs/hand.png", row.resize((row.width * 4, row.height * 4), Image.NEAREST))

    print(f"font: {FONT_USED}")
    print(f"atlas: {atlas.size[0]}x{atlas.size[1]}, {len(glyphs)} glyphs, "
          f"U+{FIRST_CODEPOINT:04X}..U+{FIRST_CODEPOINT + len(glyphs) - 1:04X}")


if __name__ == "__main__":
    main()
