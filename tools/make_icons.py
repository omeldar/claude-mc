"""Generates the mod's bitmap assets with nothing but the Python standard library.

    python tools/make_icons.py

Writes:
  src/main/resources/assets/claudemc/textures/font/mark.png  (32x16 sheet: U+E000 U+E001)
  src/main/resources/assets/claudemc/icon.png                (128x128 mod icon)

The font sheet holds two glyphs side by side, both drawn white so Minecraft can tint them:
  U+E000  the mod's block mark, on every message the mod prints
  U+E001  a warning triangle, shown when a reply was billed to an API key
The mod icon is drawn in redstone red instead, since nothing tints it.

The block is an original drawing: it uses no Mojang texture and no Anthropic mark.
"""

import math
import os
import struct
import zlib

# Redstone reds, lightest to darkest.
LIT = (255, 74, 54)
MID = (212, 32, 15)
SHADE = (142, 20, 8)
OUTLINE = (58, 8, 4)
GLOW = (255, 214, 196)


def blank(width, height):
    return [[(0, 0, 0, 0) for _ in range(width)] for _ in range(height)]


def block_face(x, y):
    """Which face of a 32x32 isometric cube a logical pixel falls on, or None."""
    fx, fy = x + 0.5, y + 0.5
    if abs(fx - 16) / 15.0 + abs(fy - 9) / 7.5 <= 1.0:
        return "top"
    if 1 <= fx <= 16 and 9 + (fx - 1) * 0.5 <= fy <= 24 + (fx - 1) * 0.5:
        return "left"
    if 16 < fx <= 31 and 16.5 - (fx - 16) * 0.5 <= fy <= 31.5 - (fx - 16) * 0.5:
        return "right"
    return None


def prompt_pixels():
    """A ">_" command prompt, placed by hand on the 32x32 grid to follow the left face."""
    pixels = set()
    for y in range(13, 20):                 # the chevron's steep upper arm
        x = 3 + round((y - 13) * 5 / 7)
        pixels.update({(x, y), (x + 1, y)})
    for x in range(3, 9):                   # its shallow lower arm
        y = 19 + round((8 - x) * 0.4)
        pixels.update({(x, y), (x, y + 1)})
    for x in range(9, 15):                  # the underscore, running along the face
        y = 24 + (x - 9) // 2
        pixels.update({(x, y), (x, y + 1)})
    return pixels


def block_icon():
    """The 128x128 mod icon: a chunky redstone-red block showing a command prompt."""
    logical = 32
    grid = [[block_face(x, y) for x in range(logical)] for y in range(logical)]
    prompt = prompt_pixels()
    out = blank(logical, logical)
    for y in range(logical):
        for x in range(logical):
            face = grid[y][x]
            if face is None:
                near = any(0 <= x + dx < logical and 0 <= y + dy < logical
                           and grid[y + dy][x + dx]
                           for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                if near:
                    out[y][x] = OUTLINE + (255,)
                continue
            colour = {"top": LIT, "left": MID, "right": SHADE}[face]
            if face == "left" and (x, y) in prompt:
                colour = GLOW
            out[y][x] = colour + (255,)
    scale = 128 // logical
    return [[out[y // scale][x // scale] for x in range(128)] for y in range(128)]


def block_glyph(size, colour):
    """The icon's silhouette for chat: three faces split by transparent seams."""
    px = blank(size, size)
    centre = size / 2.0
    half_height = size / 4.0
    ss = 4
    for y in range(size):
        for x in range(size):
            hits = 0
            for sy in range(ss):
                for sx in range(ss):
                    fx = x + (sx + 0.5) / ss
                    fy = y + (sy + 0.5) / ss
                    dx = abs(fx - centre)
                    in_top = abs(fy - half_height) + dx * 0.5 <= half_height - 0.5
                    in_side = (dx <= centre - 0.5 and dx >= 0.5
                               and fy >= 2 * half_height - dx * 0.5 + 0.5
                               and fy <= size - dx * 0.5 - 0.25)
                    if in_top or in_side:
                        hits += 1
            if hits:
                px[y][x] = (colour[0], colour[1], colour[2],
                            int(round(255 * hits / (ss * ss))))
    return px


def warning(size, colour):
    """A hollow triangle with an exclamation mark - the usual 'careful' shape."""
    px = blank(size, size)
    ss = 4
    # Triangle corners, inset a little so the stroke has room.
    pad = size * 0.08
    apex = (size / 2.0, pad)
    left = (pad, size - pad)
    right = (size - pad, size - pad)
    stroke = max(1.0, size * 0.13)

    def dist_to_segment(p, a, b):
        px_, py_ = p
        ax, ay = a
        bx, by = b
        dx, dy = bx - ax, by - ay
        if dx == 0 and dy == 0:
            return math.hypot(px_ - ax, py_ - ay)
        t = max(0.0, min(1.0, ((px_ - ax) * dx + (py_ - ay) * dy) / (dx * dx + dy * dy)))
        return math.hypot(px_ - (ax + t * dx), py_ - (ay + t * dy))

    bar_top = size * 0.38
    bar_bottom = size * 0.66
    dot_y = size * 0.78
    bar_half = max(0.5, size * 0.07)

    for y in range(size):
        for x in range(size):
            hits = 0
            for sy in range(ss):
                for sx in range(ss):
                    fx = x + (sx + 0.5) / ss
                    fy = y + (sy + 0.5) / ss
                    p = (fx, fy)
                    on_edge = (dist_to_segment(p, apex, left) <= stroke / 2
                               or dist_to_segment(p, apex, right) <= stroke / 2
                               or dist_to_segment(p, left, right) <= stroke / 2)
                    in_bar = (abs(fx - size / 2.0) <= bar_half
                              and bar_top <= fy <= bar_bottom)
                    in_dot = math.hypot(fx - size / 2.0, fy - dot_y) <= bar_half * 1.1
                    if on_edge or in_bar or in_dot:
                        hits += 1
            if hits:
                px[y][x] = (colour[0], colour[1], colour[2],
                            int(round(255 * hits / (ss * ss))))
    return px


def side_by_side(*tiles):
    """Lays square tiles out in one row, which is how a bitmap font sheet is read."""
    height = len(tiles[0])
    out = []
    for y in range(height):
        row = []
        for tile in tiles:
            row.extend(tile[y])
        out.append(row)
    return out


def write_png(path, px):
    height = len(px)
    width = len(px[0])
    raw = bytearray()
    for row in px:
        raw.append(0)
        for (r, g, b, a) in row:
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        out = struct.pack(">I", len(data)) + tag + data
        return out + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", header)
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(png)
    print("wrote", path, f"{width}x{height}", len(png), "bytes")


def main():
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
    assets = os.path.join(root, "src", "main", "resources", "assets", "claudemc")
    white = (255, 255, 255)
    write_png(os.path.join(assets, "textures", "font", "mark.png"),
              side_by_side(block_glyph(16, white), warning(16, white)))
    write_png(os.path.join(assets, "icon.png"), block_icon())


if __name__ == "__main__":
    main()
