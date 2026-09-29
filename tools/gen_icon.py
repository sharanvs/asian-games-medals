"""Generate a 192x192 launcher icon (pure stdlib PNG)."""
import struct, zlib

W = H = 192

def chunk(t, d):
    c = t + d
    return struct.pack('>I', len(d)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)

def png(pixels):
    raw = b''.join(b'\x00' + bytes(pixels[y * W * 4:(y + 1) * W * 4]) for y in range(H))
    ihdr = struct.pack('>IIBBBBB', W, H, 8, 6, 0, 0, 0)
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', ihdr)
            + chunk(b'IDAT', zlib.compress(raw, 9))
            + chunk(b'IEND', b''))

px = bytearray(W * H * 4)
def setp(x, y, r, g, b, a=255):
    if 0 <= x < W and 0 <= y < H:
        o = (y * W + x) * 4
        px[o:o+4] = bytes((r, g, b, a))

def disc(cx, cy, rad, r, g, b):
    for y in range(int(cy - rad) - 1, int(cy + rad) + 2):
        for x in range(int(cx - rad) - 1, int(cx + rad) + 2):
            if (x - cx) ** 2 + (y - cy) ** 2 <= rad * rad:
                setp(x, y, r, g, b)

# background: deep navy
for i in range(0, len(px), 4):
    px[i:i+4] = bytes((11, 16, 32, 255))
# saffron / white / green stripes across the middle band
for y in range(78, 114):
    c = (255, 153, 51) if y < 90 else ((255, 255, 255) if y < 102 else (19, 136, 8))
    for x in range(W):
        setp(x, y, *c)
# ribbon (saffron left, green right) from top to medal
for y in range(18, 78):
    w = 10 + (y - 18) // 4
    for x in range(86 - w, 86):
        setp(x, y, 255, 153, 51)
    for x in range(106, 106 + w):
        setp(x, y, 19, 136, 8)
# medal: outer ring + gold face + highlight
disc(96, 128, 44, 176, 124, 0)
disc(96, 128, 37, 255, 180, 0)
disc(88, 120, 12, 255, 214, 90)
# navy chakra-ish dot ring on the white stripe? draw small navy rosette on medal
disc(96, 128, 13, 11, 16, 32)
disc(96, 128, 9, 255, 255, 255)
disc(96, 128, 4, 11, 16, 32)

open('/data/data/com.termux/files/home/apkproj/res/drawable/icon.png', 'wb').write(png(px))
print('icon written')
