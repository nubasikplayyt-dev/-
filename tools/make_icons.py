#!/usr/bin/env python3
"""
Генерация иконок Lingvo World из исходных изображений (design/icons/source).

  python3 tools/make_icons.py

Создаёт:
  app/src/main/res/mipmap-<dpi>/ic_<id>_fg.webp        — передний слой adaptive icon (непрозрачный)
  app/src/main/res/mipmap-anydpi-v26/ic_<id>.xml       — adaptive icon (фон + передний слой)
  app/src/main/res/values/icon_colors.xml              — цвета фона
  app/src/main/res/drawable-nodpi/art_<id>.webp        — иллюстрации для достижений и выбора иконки
  design/icons/preview.png                             — проверка в масках круга и «сквиркла»

Рисунок вписывается в безопасную зону (66 из 108 dp), края плиток растушёвываются в цвет фона,
поэтому маски лаунчеров не обрезают содержимое и не показывают углы исходных плиток.
"""
import os
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "design", "icons", "source")
RES = os.path.join(ROOT, "app", "src", "main", "res")

COLS = [(68, 415), (454, 803), (840, 1190)]
ROWS = [(83, 427), (454, 798), (832, 1168)]

# id: (файл, ряд, столбец) — порядок плиток в исходных сетках
TILES = {
    "crown_world": ("set1", 0, 0), "days30": ("set1", 0, 1), "dialog_star": ("set1", 0, 2),
    "knowledge_cup": ("set1", 1, 0), "listener": ("set1", 1, 1), "chronicle": ("set1", 1, 2),
    "bullseye": ("set1", 2, 0), "open_book": ("set1", 2, 1), "level_up": ("set1", 2, 2),
    "classic": ("set2", 0, 0), "week7": ("set2", 0, 1), "plan": ("set2", 0, 2),
    "champion": ("set2", 1, 0), "days14": ("set2", 1, 1), "sniper": ("set2", 1, 2),
    "graduate": ("set2", 2, 0), "words100": ("set2", 2, 1), "horizons": ("set2", 2, 2),
}
DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}


def source_tiles():
    out = {}
    imgs = {n: Image.open(os.path.join(SRC, n + ".jpg")).convert("RGB") for n in ("set1", "set2")}
    for tid, (n, r, c) in TILES.items():
        (x0, x1), (y0, y1) = COLS[c], ROWS[r]
        out[tid] = imgs[n].crop((x0, y0, x1, y1))
    out["world"] = Image.open(os.path.join(SRC, "main.jpg")).convert("RGB").crop((160, 160, 1094, 1094))
    return out


def border_color(img, inset):
    w, h = img.size
    pts = []
    for t in (0.25, 0.5, 0.75):
        pts += [(int(w * t), inset), (int(w * t), h - inset - 1), (inset, int(h * t)), (w - inset - 1, int(h * t))]
    px = [img.getpixel(p) for p in pts]
    return tuple(sum(p[i] for p in px) // len(px) for i in range(3))


def feather_mask(size, ramp):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    d.rounded_rectangle((ramp, ramp, size - ramp, size - ramp), radius=ramp * 2, fill=255)
    return m.filter(ImageFilter.GaussianBlur(ramp / 2))


def layer(tile, canvas=432, scale=0.74, inset_frac=0.075):
    w, h = tile.size
    inset = int(w * inset_frac)
    inner = tile.crop((inset, inset, w - inset, h - inset))
    bg = border_color(inner, 6)
    size = int(canvas * scale)
    art = inner.resize((size, size), Image.LANCZOS)
    out = Image.new("RGB", (canvas, canvas), bg)
    off = (canvas - size) // 2
    out.paste(art, (off, off), feather_mask(size, max(6, size // 14)))
    return out, bg


def main():
    tiles = source_tiles()
    colors = {}
    previews = []
    os.makedirs(os.path.join(RES, "mipmap-anydpi-v26"), exist_ok=True)
    os.makedirs(os.path.join(RES, "drawable-nodpi"), exist_ok=True)
    for tid, tile in tiles.items():
        scale = 0.60 if tid == "world" else 0.62
        big, bg = layer(tile, 432, scale, 0.03 if tid == "world" else 0.075)
        colors[tid] = bg
        for dpi, px in DENSITIES.items():
            d = os.path.join(RES, f"mipmap-{dpi}")
            os.makedirs(d, exist_ok=True)
            big.resize((px, px), Image.LANCZOS).save(os.path.join(d, f"ic_{tid}_fg.webp"), quality=90)
        with open(os.path.join(RES, "mipmap-anydpi-v26", f"ic_{tid}.xml"), "w") as f:
            f.write('<?xml version="1.0" encoding="utf-8"?>\n'
                    '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                    f'    <background android:drawable="@color/icon_bg_{tid}" />\n'
                    f'    <foreground android:drawable="@mipmap/ic_{tid}_fg" />\n'
                    '</adaptive-icon>\n')
        art = tile.crop((int(tile.size[0] * 0.04),) * 2 + (int(tile.size[0] * 0.96),) * 2).resize((256, 256), Image.LANCZOS)
        art.save(os.path.join(RES, "drawable-nodpi", f"art_{tid}.webp"), quality=88)
        # превью: видимая часть 72/108 в круге и сквиркле
        vis = big.crop((72, 72, 360, 360)).resize((144, 144), Image.LANCZOS)
        for shape in ("circle", "squircle"):
            m = Image.new("L", (144, 144), 0)
            dr = ImageDraw.Draw(m)
            if shape == "circle":
                dr.ellipse((0, 0, 143, 143), fill=255)
            else:
                dr.rounded_rectangle((0, 0, 143, 143), radius=44, fill=255)
            p = Image.new("RGB", (144, 144), (40, 40, 48))
            p.paste(vis, (0, 0), m)
            previews.append(p)
    with open(os.path.join(RES, "values", "icon_colors.xml"), "w") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n')
        for tid, (r, g, b) in sorted(colors.items()):
            f.write(f'    <color name="icon_bg_{tid}">#FF{r:02X}{g:02X}{b:02X}</color>\n')
        f.write('</resources>\n')
    cols = 8
    sheet = Image.new("RGB", (cols * 150, ((len(previews) + cols - 1) // cols) * 150), (40, 40, 48))
    for i, p in enumerate(previews):
        sheet.paste(p, ((i % cols) * 150 + 3, (i // cols) * 150 + 3))
    sheet.save(os.path.join(ROOT, "design", "icons", "preview.png"))
    print(f"Иконок: {len(tiles)}")


if __name__ == "__main__":
    main()
