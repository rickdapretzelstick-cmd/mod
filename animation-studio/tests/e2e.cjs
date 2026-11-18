// End-to-end test on a phone viewport (Pixel 7) with Playwright.
// Usage: serve the folder (python3 -m http.server 8765) then: node tests/e2e.cjs [url] [screenshotDir]
const { chromium, devices } = require('playwright');
const fs = require('fs');
const path = require('path');

const URL = process.argv[2] || 'http://localhost:8765/index.html?nosw';
const OUT = process.argv[3] || path.join(__dirname, '..', 'screenshots');
fs.mkdirSync(OUT, { recursive: true });
let failed = 0;
const ok = (cond, msg) => { console.log(`${cond ? '  ✓' : '  ✗'} ${msg}`); if (!cond) failed++; };

(async () => {
  const browser = await chromium.launch({ args: ['--use-gl=swiftshader', '--enable-webgl', '--ignore-gpu-blocklist'] });
  const ctx = await browser.newContext({ ...devices['Pixel 7'], acceptDownloads: true });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  const shot = (n) => page.screenshot({ path: path.join(OUT, `${n}.png`) });
  const app = (fn, arg) => page.evaluate(fn, arg);

  await page.goto(URL);
  await page.waitForSelector('.modal');
  await shot('01-welcome');

  // 1. onboarding → first punch
  await page.click('text=Let’s go');
  await page.waitForTimeout(600);
  let s = await app(() => ({ name: __app.anim.name, tracks: Object.keys(__app.anim.tracks).length, playing: __app.playing, sections: __app.anim.sections.length }));
  ok(s.name === 'punch' && s.tracks > 5 && s.playing, `onboarding creates and plays a punch (${JSON.stringify(s)})`);
  await page.waitForTimeout(250);
  await shot('02-punch-playing');

  // 2. playback advances time
  const t1 = await app(() => __app.time); await page.waitForTimeout(300); const t2 = await app(() => __app.time);
  ok(t2 !== t1, 'playback advances the playhead');
  await page.click('#playBtn'); // pause

  // 3. assistant prompt
  await page.fill('.assist-input', 'Make the character perform a powerful overhead sword strike.');
  await page.click('button.go');
  await page.waitForTimeout(400);
  s = await app(() => ({ name: __app.anim.name, n: __app.project.animations.length, sec: __app.anim.sections.map((x) => x.block) }));
  ok(s.sec[0] === 'sword_overhead' && s.n === 3, `assistant made an overhead strike (${JSON.stringify(s)})`);
  ok(await page.isVisible('.bubble'), 'assistant explains its plan');
  await page.click('#playBtn'); await page.waitForTimeout(450); await page.click('#playBtn');
  await shot('03-assistant-overhead');

  // 4. adjustment command + undo/redo
  const len0 = await app(() => __app.anim.length);
  await page.fill('.assist-input', 'Make it faster');
  await page.click('button.go');
  const len1 = await app(() => __app.anim.length);
  ok(len1 < len0, `"make it faster" shortened ${len0.toFixed(2)}s → ${len1.toFixed(2)}s`);
  await page.click('#undoBtn');
  ok(Math.abs((await app(() => __app.anim.length)) - len0) < 1e-6, 'undo restores the length');
  await page.click('#redoBtn');
  ok(Math.abs((await app(() => __app.anim.length)) - len1) < 1e-6, 'redo re-applies it');

  // 5. tap a section → regenerate just that move
  await page.click('.tl-section');
  ok(await app(() => !!__app.selection.section), 'tapping a move on the timeline selects it');
  await page.click('text=🎲 Regenerate');
  ok(await app(() => __app.history.undoStack.at(-1).label === 'Regenerate move'), 'regenerate section is undoable');

  // 6. tap a body part in the viewport (pick at the head's projected position)
  await app(() => { __app.setTime(0, true); __app.viewer.frame(); });
  await page.waitForTimeout(300);
  const headPt = await app(() => {
    const g = __app.viewer.bones.get('head'); const v = new (g.position.constructor)();
    g.getWorldPosition(v); v.y += 7 / 16; v.project(__app.viewer.camera);
    const r = __app.viewer.renderer.domElement.getBoundingClientRect();
    return { x: r.left + (v.x + 1) / 2 * r.width, y: r.top + (1 - v.y) / 2 * r.height };
  });
  await page.mouse.click(headPt.x, headPt.y);
  await page.waitForTimeout(200);
  ok((await app(() => __app.selection.bone)) === 'head', `tapping the head selects it (${await app(() => __app.selection.bone)})`);

  // 7. Pose panel: drag the X scrubber → a key appears at the playhead
  await page.click('#tab-pose');
  await app(() => __app.setTime(0.3, true));
  const before = await app(() => (__app.anim.tracks.head?.rotation || []).length);
  await page.locator('.scrub').first().scrollIntoViewIfNeeded();
  const sc = await page.locator('.scrub').first().boundingBox();
  await page.mouse.move(sc.x + 40, sc.y + sc.height / 2);
  await page.mouse.down(); await page.mouse.move(sc.x + 120, sc.y + sc.height / 2, { steps: 6 }); await page.mouse.up();
  const after = await app(() => ({ n: (__app.anim.tracks.head?.rotation || []).length, k: (__app.anim.tracks.head?.rotation || []).find((k) => Math.abs(k.t - 0.3) < 1e-3) }));
  ok(after.k && Math.abs(after.k.v[0]) > 5, `dragging the slider keyed the head at 0.30s (${JSON.stringify(after.k)})`);
  await shot('04-pose-panel');

  // 8. timeline key select → easing → drag to retime → delete
  await app(() => __app.selectKey('head', 0.3));
  await page.waitForSelector('.keybar.show');
  await page.click('.keybar >> text=Overshoot');
  ok((await app(() => __app.anim.tracks.head.rotation.find((k) => Math.abs(k.t - 0.3) < 1e-3).easing)) === 'easeOutBack', 'easing preset applied to the selected key');
  await shot('05-keybar');
  const key = page.locator('.tl-key.sel').first();
  await key.scrollIntoViewIfNeeded();
  const kb = await key.boundingBox();
  await page.mouse.move(kb.x + kb.width / 2, kb.y + kb.height / 2);
  await page.mouse.down(); await page.mouse.move(kb.x + kb.width / 2 + 60, kb.y + kb.height / 2, { steps: 5 }); await page.mouse.up();
  const moved = await app(() => __app.selection.keyTime);
  ok(moved > 0.3, `dragging the diamond retimed the key to ${moved}s`);
  await page.click('.keybar >> text=🗑 Delete');
  ok(await app((t) => !__app.anim.tracks.head.rotation.some((k) => Math.abs(k.t - t) < 1e-3), moved), 'key deleted');

  // 9. polish on a hand-keyed robotic animation
  await page.click('#tab-anims');
  await page.click('text=+ Empty');
  await app(() => { const a = __app.anim; __app.commit('test keys', () => { a.length = 1; for (const [t, x] of [[0, 0], [0.4, 0], [0.5, -90], [1, 0]]) { a.tracks.right_arm = a.tracks.right_arm || { rotation: [], position: [], scale: [] }; a.tracks.right_arm.rotation.push({ t, v: [x, 0, 0], easing: 'linear' }); } }); });
  await page.click('#tab-create');
  await page.click('text=🪄 Polish motion');
  await page.waitForSelector('.modal');
  const polished = await app(() => __app.anim.tracks.right_arm.rotation.length);
  ok(polished > 4, `polish added anticipation/follow-through keys (${polished} keys)`);
  await shot('06-polish');
  await page.click('.modal >> text=Close');

  // 10. library save → reuse
  await page.click('#tab-anims');
  await page.click('text=⭳ Save current to library');
  await page.fill('.modal input', 'Tests');
  await page.click('.modal >> text=OK');
  await page.waitForSelector('.lib-item');
  const nBefore = await app(() => __app.project.animations.length);
  await page.click('.lib-item >> text=Use');
  ok((await app(() => __app.project.animations.length)) === nBefore + 1, 'library item inserted into the project');
  await shot('07-library');

  // 11. export: validate + download the mod package
  await page.click('#tab-export');
  await page.waitForSelector('.report .bubble');
  ok(await page.isVisible('text=✅ Valid'), 'export validation passes');
  const [dl] = await Promise.all([page.waitForEvent('download'), page.click('text=⬇ Download mod package (.zip)')]);
  const zipPath = path.join(OUT, dl.suggestedFilename());
  await dl.saveAs(zipPath);
  ok(fs.statSync(zipPath).size > 1000, `zip downloaded: ${dl.suggestedFilename()}`);
  await shot('08-export');

  // 12. persistence: reload, project and animations survive
  await page.waitForTimeout(800);
  const count = await app(() => __app.project.animations.length);
  await page.reload();
  await page.waitForFunction(() => window.__app && __app.project);
  await page.waitForTimeout(500);
  ok((await app(() => __app.project.animations.length)) === count, `project restored after reload (${count} animations)`);

  // 13. landscape layout
  await page.setViewportSize({ width: 915, height: 412 });
  await page.waitForTimeout(500);
  await app(() => { __app.selectAnimation(__app.project.animations[1].id); __app.setTime(0.45, true); });
  await page.waitForTimeout(300);
  await shot('09-landscape');
  const lay = await page.evaluate(() => { const v = document.querySelector('.viewport-wrap').getBoundingClientRect(), p = document.querySelector('.panel').getBoundingClientRect(); return { side: p.left >= v.right - 1 }; });
  ok(lay.side, 'landscape puts the panel beside the viewport');

  ok(errors.length === 0, `no page errors${errors.length ? `: ${errors.join(' | ')}` : ''}`);
  await browser.close();
  console.log(failed ? `\n${failed} FAILED` : '\nall e2e checks passed');
  process.exit(failed ? 1 : 0);
})();
