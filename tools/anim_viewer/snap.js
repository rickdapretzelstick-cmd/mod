// Renders clip poses from the built viewer page to PNGs, for side-by-side checks against reference footage:
//   NODE_PATH=$(npm root -g) node tools/anim_viewer/snap.js <viewer.html> <out-dir> <group/name> <t_ms:yaw:pitch>...
// (yaw/pitch in radians, the viewer's orbit camera; see tools/r6_fit.py --compare).
const { chromium } = require('playwright');
(async () => {
  const [html, out, id, ...shots] = process.argv.slice(2);
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM || '/opt/pw-browsers/chromium',
    args: ['--use-gl=swiftshader', '--enable-webgl', '--ignore-gpu-blocklist'] });
  const page = await browser.newPage({ viewport: { width: 900, height: 700 } });
  await page.goto('file://' + require('path').resolve(html));
  await page.waitForFunction(() => window.jjkShow);
  const fs = require('fs');
  fs.mkdirSync(out, { recursive: true });
  for (const [k, s] of shots.entries()) {
    const [t, yaw, pitch] = s.split(':').map(Number);
    if (!await page.evaluate(([id, t, y, p]) => window.jjkShow(id, t, y, p), [id, t, yaw, pitch])) throw new Error('no clip ' + id);
    await page.evaluate(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))));
    await page.locator('canvas').first().screenshot({ path: `${out}/${String(k).padStart(3, '0')}.png` });
  }
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
