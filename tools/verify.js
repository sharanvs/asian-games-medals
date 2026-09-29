// Verify v1 signature of final APK. Run: node verify.js
const { execSync } = require('child_process');
const crypto = require('crypto');
const APK = 'asian-games-medal-app/AsianGamesMedals.apk';
const list = execSync(`unzip -l ${APK}`).toString().split('\n')
  .map(l => l.trim().split(/\s+/).pop())
  .filter(n => n && !n.startsWith('----') && n !== 'Name' && !n.startsWith('Archive') && !n.endsWith('/') && !n.endsWith('.apk') && !/^\d+$/.test(n) && n !== 'files');
const skip = new Set(['META-INF/MANIFEST.MF', 'META-INF/CERT.SF', 'META-INF/CERT.RSA']);
const mf = execSync(`unzip -p ${APK} META-INF/MANIFEST.MF`).toString('binary');
let ok = true;
for (const e of list) {
  if (skip.has(e)) continue;
  const data = execSync(`unzip -p ${APK} "${e}"`, { maxBuffer: 20 * 1024 * 1024 });
  const d = crypto.createHash('sha256').update(data).digest('base64');
  if (!mf.includes(`Name: ${e}\r\nSHA-256-Digest: ${d}`)) { console.log('MISMATCH:', e); ok = false; }
  else console.log('OK:', e, d.slice(0, 12) + '...');
}
console.log(ok ? 'ALL DIGESTS VERIFIED ✔' : 'VERIFICATION FAILED ✘');
