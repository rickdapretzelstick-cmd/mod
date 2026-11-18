// Bundles the app into one self-contained HTML page (for hosts that want a single file, such as a
// claude.ai artifact). Usage: node tools/build-artifact.mjs [out.html]   (needs npx / esbuild)
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const out = resolve(process.argv[2] || `${root}/dist/minecraft-animation-studio.html`);
const js = execFileSync('npx', ['-y', 'esbuild@0.24.0', 'src/app.js', '--bundle', '--format=iife', '--minify', '--target=es2020',
  `--alias:three=./vendor/three.module.min.js`], { cwd: root, maxBuffer: 64 * 1024 * 1024 }).toString();
const css = readFileSync(`${root}/styles.css`, 'utf8') + '\n#app { height: 100%; }\n';
const page = `<title>Minecraft Animation Studio</title>
<meta name="theme-color" content="#141a26">
<style>${css}</style>
<div id="app"><div class="boot">Loading Animation Studio…</div></div>
<script>${js.replace(/<\/script/gi, '<\\/script')}</script>
`;
mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, page);
console.log(`wrote ${out} (${(page.length / 1024).toFixed(0)} KB)`);
