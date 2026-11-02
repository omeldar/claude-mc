"""Generates ClaudeCraft's bitmap assets with nothing but the Python standard library.

    python tools/make_icons.py

Writes:
  src/main/resources/assets/claudecraft/textures/font/claude_mark.png  (16x16 chat glyph)
  src/main/resources/assets/claudecraft/icon.png                       (128x128 mod icon)

The glyph is drawn white so Minecraft can tint it; the mod icon is drawn in Claude clay.
"""

import math
import os
import struct
import zlib

CLAY = (217, 119, 87)
RAYS = 10


def blank(size):
    return [[(0, 0, 0, 0) for _ in range(size)] for _ in range(size)]


def burst(size, colour, rays=RAYS, radius_factor=0.46, core_factor=0.07):
    """Draws a Claude-style asterisk: evenly spaced tapered rays around a small core."""
    px = blank(size)
    cx = cy = (size - 1) / 2.0
    radius = size * radius_factor
    core = size * core_factor
    # Supersample so the diagonal rays do not come out jagged.
    ss = 4
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
                    # Distance in angle to the nearest ray centre.
                    step = 2 * math.pi / rays
                    nearest = round(theta / step) * step
                    d = abs(theta - nearest) * r
                    # Rays taper from the core outwards.
                    taper = (1.0 - (r / radius)) * 0.55 + 0.12
                    half_width = max(0.35, size * 0.055 * taper * 3.2)
                    if d <= half_width:
                        hits += 1
            if hits:
                a = int(round(255 * hits / (ss * ss)))
                px[y][x] = (colour[0], colour[1], colour[2], a)
    return px


def write_png(path, px):
    size = len(px)
    raw = bytearray()
    for row in px:
        raw.append(0)
        for (r, g, b, a) in row:
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        out = struct.pack(">I", len(data)) + tag + data
        return out + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", header)
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(png)
    print("wrote", path, len(png), "bytes")


def main():
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
    assets = os.path.join(root, "src", "main", "resources", "assets", "claudecraft")
    # White so the chat colour can tint it.
    write_png(os.path.join(assets, "textures", "font", "claude_mark.png"),
              burst(16, (255, 255, 255)))
    # Baked clay for the Mod Menu tile.
    write_png(os.path.join(assets, "icon.png"), burst(128, CLAY))


if __name__ == "__main__":
    main()
