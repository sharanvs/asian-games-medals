"""Add APK Signature Scheme v2 signature (RSA/SHA256, alg 0x0103) to our APK.
Usage: python3 v2sign.py <in.apk> <out.apk>
Requires: apkproj/out/cert.pem + key.pem (openssl available).
"""
import struct, sys, hashlib, subprocess

CHUNK = 1024 * 1024
V2_ID = 0x7109871a
SIG_ALG_RSA_SHA256 = 0x0103

def u32(b, o): return struct.unpack('<I', b[o:o+4])[0]

def lp(b: bytes) -> bytes:
    return struct.pack('<I', len(b)) + b

def content_digest_sha256(data, blk_start, cd_off, cd_size, eocd_off):
    eocd = bytearray(data[eocd_off:])
    eocd[16:20] = struct.pack('<I', blk_start)
    tail = bytes(eocd)
    chunks = []
    for seg in (data[0:blk_start], data[cd_off:cd_off+cd_size], tail):
        off = 0
        while off < len(seg):
            c = seg[off:off+CHUNK]
            h = hashlib.sha256()
            h.update(b'\xa5')
            h.update(struct.pack('<I', len(c)))
            h.update(c)
            chunks.append(h.digest())
            off += CHUNK
    m = hashlib.sha256()
    m.update(b'\x5a')
    m.update(struct.pack('<I', len(chunks)))
    for c in chunks:
        m.update(c)
    return m.digest()

def main(inp, outp):
    data = open(inp, 'rb').read()
    idx = data.rfind(b'PK\x05\x06')
    assert idx != -1
    cd_size, cd_off = u32(data, idx+12), u32(data, idx+16)
    assert data[cd_off:cd_off+4] == b'PK\x01\x02', 'bad CD magic'
    assert data[cd_off-16:cd_off] != b'APK Sig Block 42', 'already has signing block'
    blk_start = cd_off  # no existing block

    digest = content_digest_sha256(data, blk_start, cd_off, cd_size, idx)
    print('content digest:', digest.hex()[:32], '...')

    H = '/data/data/com.termux/files/home'
    cert_der = subprocess.check_output(
        ['openssl', 'x509', '-in', H + '/apkproj/out/cert.pem', '-outform', 'DER'])
    pub_der = subprocess.check_output(
        ['openssl', 'pkey', '-in', H + '/apkproj/out/key.pem', '-pubout', '-outform', 'DER'])
    print('cert DER:', len(cert_der), 'pubkey DER:', len(pub_der))

    digests_seq = lp(lp(struct.pack('<I', SIG_ALG_RSA_SHA256) + lp(digest)))
    certs_seq = lp(lp(cert_der))
    attrs_seq = struct.pack('<I', 0)
    signed_data = digests_seq + certs_seq + attrs_seq
    open(H + '/_sd.bin', 'wb').write(signed_data)
    sig = subprocess.check_output(
        ['openssl', 'dgst', '-sha256', '-sign', H + '/apkproj/out/key.pem', H + '/_sd.bin'])
    print('signature bytes:', len(sig))

    signatures_seq = lp(lp(struct.pack('<I', SIG_ALG_RSA_SHA256) + lp(sig)))
    signer = lp(signed_data) + signatures_seq + lp(pub_der)
    v2_value = lp(lp(signer))
    pair = struct.pack('<Q', 4 + len(v2_value)) + struct.pack('<I', V2_ID) + v2_value
    S = len(pair) + 8 + 16
    block = struct.pack('<Q', S) + pair + struct.pack('<Q', S) + b'APK Sig Block 42'

    new = data[:blk_start] + block + data[blk_start:]
    # fix EOCD central-dir offset
    new_cd_off = cd_off + len(block)
    eocd_new = idx + len(block)
    new = (new[:eocd_new+16] + struct.pack('<I', new_cd_off) + new[eocd_new+20:])
    open(outp, 'wb').write(new)
    print(f'inserted v2 block ({len(block)} bytes), cd_off {cd_off} -> {new_cd_off}')

if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
