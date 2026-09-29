"""Parse APK Signing Block (v2/v3) and verify content digests. Usage: python3 v2verify.py <apk>"""
import struct, sys, hashlib

def u32(b, o): return struct.unpack('<I', b[o:o+4])[0]
def u64(b, o): return struct.unpack('<Q', b[o:o+8])[0]

def find_eocd(d):
    # EOCD: last 22 bytes min; search backwards for PK\x05\x06
    idx = d.rfind(b'PK\x05\x06')
    assert idx != -1
    cd_size, cd_off = u32(d, idx+12), u32(d, idx+16)
    return idx, cd_size, cd_off

def get_pairs(block):
    # block starts at size field
    total = u64(block, 0)
    assert len(block) == total + 8, f'block len {len(block)} vs size {total}'
    assert block[-16:] == b'APK Sig Block 42', 'bad magic'
    pairs = {}
    o = 8
    end = len(block) - 24  # exclude trailing size field + magic
    while o < end:
        sz = u64(block, o)
        pid = u32(block, o+8)
        pairs[pid] = block[o+12:o+8+sz]
        o += 8 + sz
    return pairs

def lp_read(b, o):
    ln = u32(b, o)
    return b[o+4:o+4+ln], o+4+ln

CHUNK = 1024*1024
def content_digest_sha256(data, blk_start, cd_off, cd_size, eocd_off):
    # apksig: chunk = SHA256(0xA5 || u32LE(len) || bytes); final = SHA256(0x5A || u32LE(n) || digests)
    # EOCD section uses EOCD with CD-offset field rewritten to blk_start
    eocd = bytearray(data[eocd_off:])
    eocd[16:20] = struct.pack('<I', blk_start)
    tail = bytes(eocd)
    secs = [(0, blk_start), (cd_off, cd_off + cd_size)]
    md = hashlib.sha256()
    md.update(b'\x5a')
    chunks = []
    for s, e in secs:
        off = s
        while off < e:
            c = data[off:min(off+CHUNK, e)]
            h = hashlib.sha256()
            h.update(b'\xa5')
            h.update(struct.pack('<I', len(c)))
            h.update(c)
            chunks.append(h.digest())
            off += CHUNK
    # tail as final segment (chunked the same way)
    off = 0
    while off < len(tail):
        c = tail[off:min(off+CHUNK, len(tail))]
        h = hashlib.sha256()
        h.update(b'\xa5')
        h.update(struct.pack('<I', len(c)))
        h.update(c)
        chunks.append(h.digest())
        off += CHUNK
    md.update(struct.pack('<I', len(chunks)))
    for c in chunks:
        md.update(c)
    return md.digest()

def main(apk):
    data = open(apk, 'rb').read()
    eocd, cd_size, cd_off = find_eocd(data)
    # signing block right before CD: [...pairs...][u64 size][magic16]
    if data[cd_off-16:cd_off] == b'APK Sig Block 42':
        blk_size = u64(data, cd_off-24)
        blk_start = cd_off - 8 - blk_size  # S excludes leading size field only
        block = data[blk_start:cd_off]
        pairs = get_pairs(block)
        print('pair IDs:', [hex(k) for k in pairs])
        for pid in (0x7109871a, 0xf05368d0, 0x42726536):
            if pid in pairs:
                v = pairs[pid]
                # v2 value = lp(signers); each signer = lp{ sd lp, sigs lp, pubkey lp }
                signers, _ = lp_read(v, 0)
                o = 0
                ns = 0
                while o < len(signers):
                    one, o = lp_read(signers, o)
                    sd, so2 = lp_read(one, 0)
                    sigs, so2 = lp_read(one, so2)
                    pub, so2 = lp_read(one, so2)
                    ns += 1
                    # signedData: digests lp, certs lp, attrs lp; digest entry = lp{ sigAlgID u32, digest lp }
                    so = 0
                    digests, so = lp_read(sd, so)
                    do = 0
                    while do < len(digests):
                        dent, do = lp_read(digests, do)
                        alg = u32(dent, 0)
                        dg, _ = lp_read(dent, 4)
                        print(f'  alg {hex(alg)} recorded: {dg.hex()[:32]}...')
                        if alg == 0x0103:  # RSA/SHA256 -> CHUNKED_SHA256
                            calc = content_digest_sha256(data, blk_start, cd_off, cd_size, eocd)
                            print(f'  alg 0x01 computed: {calc.hex()[:32]}...')
                            print('  MATCH' if calc == dg else '  MISMATCH')
                print(f'block {hex(pid)}: {ns} signer(s)')
    else:
        print('no signing block')

if __name__ == '__main__':
    main(sys.argv[1])
