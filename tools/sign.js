// Manual JAR (v1) signing for the APK. Enumerates entries dynamically. Run: node sign.js
const { execSync } = require('child_process');
const fs = require('fs');
const crypto = require('crypto');

const APK = 'apkproj/out/app.apk'; // unaligned apk with classes.dex
const OUT = 'apkproj/out/meta';
fs.mkdirSync(OUT, { recursive: true });

function listEntries() {
  const out = execSync(`unzip -l ${APK}`).toString().split('\n');
  const names = [];
  for (const line of out) {
    const m = line.match(/^\s*\d+\s+\S+\s+\S+\s+(.+?)\s*$/);
    if (!m) continue;
    const n = m[1];
    if (n === 'Name' || n.startsWith('----') || n.startsWith('Archive')) continue;
    if (n.endsWith('/')) continue;
    if (n.startsWith('META-INF/')) continue;
    if (/^\d+ files?/.test(n)) continue;
    names.push(n);
  }
  return names;
}

const entries = listEntries();
console.log('signing entries:', entries);

function sha256b64(entry) {
  const data = execSync(`unzip -p ${APK} "${entry}"`, { maxBuffer: 20 * 1024 * 1024 });
  return crypto.createHash('sha256').update(data).digest('base64');
}

const NL = '\r\n';
let mf = 'Manifest-Version: 1.0' + NL + 'Created-By: MedalApp manual signer' + NL + NL;
const sections = [];
for (const e of entries) {
  const d = sha256b64(e);
  const sec = `Name: ${e}${NL}SHA-256-Digest: ${d}${NL}${NL}`;
  mf += sec;
  sections.push({ name: e, digest: d, section: sec });
}
fs.writeFileSync(`${OUT}/MANIFEST.MF`, mf, 'binary');

// CERT.SF
let sf = 'Signature-Version: 1.0' + NL + 'Created-By: MedalApp manual signer' + NL;
const mfDigest = crypto.createHash('sha256').update(mf, 'binary').digest('base64');
sf += `SHA-256-Digest-Manifest: ${mfDigest}${NL}${NL}`;
for (const s of sections) {
  const sd = crypto.createHash('sha256').update(s.section, 'binary').digest('base64');
  sf += `Name: ${s.name}${NL}SHA-256-Digest: ${sd}${NL}${NL}`;
}
fs.writeFileSync(`${OUT}/CERT.SF`, sf, 'binary');
console.log('MANIFEST.MF sha256:', mfDigest);
console.log('wrote', `${OUT}/MANIFEST.MF`, `${OUT}/CERT.SF`);
