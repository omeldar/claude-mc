"""Generates ClaudeMC's bitmap assets with nothing but the Python standard library.

    python tools/make_icons.py

Writes:
  src/main/resources/assets/claudemc/textures/font/claude_mark.png  (32x16 sheet: U+E000 U+E001)
  src/main/resources/assets/claudemc/icon.png                       (128x128 mod icon)

The font sheet holds two glyphs side by side, both drawn white so Minecraft can tint them:
  U+E000  the Claude mark, on every message the mod prints
  U+E001  a warning triangle, shown when a reply was billed to an API key
The mod icon is drawn in Claude clay instead, since nothing tints it.
"""

import math
import os
import struct
import zlib

CLAY = (217, 119, 87)
RAYS = 10


def blank(width, height):
    return [[(0, 0, 0, 0) for _ in range(width)] for _ in range(height)]


def burst(size, colour, rays=RAYS, radius_factor=0.46, core_factor=0.07):
    """A Claude-style asterisk: evenly spaced tapered rays around a small core."""
    px = blank(size, size)
    cx = cy = (size - 1) / 2.0
    radius = size * radius_factor
    core = size * core_factor
    ss = 4  # supersample, or the diagonals come out jagged
    for y in range(size):
        for x in range(size):
            hits = 0
            for sy in range(ss):
                for sx in range(ss):
                    fx = x + (sx + 0.5) / ss - cx
                    fy = y + (sy + 0.5) / ss - cy
                    r = math.hypot(fx, fy)
                    if r <= core:
                        hits += 1
                        continue
                    if r > radius:
                        continue
                    theta = math.atan2(fy, fx)
                    step = 2 * math.pi / rays
                    nearest = round(theta / step) * step
                    d = abs(theta - nearest) * r
                    taper = (1.0 - (r / radius)) * 0.55 + 0.12
                    half_width = max(0.35, size * 0.055 * taper * 3.2)
                    if d <= half_width:
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
    write_png(os.path.join(assets, "textures", "font", "claude_mark.png"),
              side_by_side(burst(16, white), warning(16, white)))
    write_png(os.path.join(assets, "icon.png"), burst(128, CLAY))


if __name__ == "__main__":
    main()
