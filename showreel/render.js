// Renders index.html frame by frame and muxes with reel.wav.
// usage: node render.js            -> full 60fps MP4
//        node render.js stills 1 3.5 6  -> PNG stills only
const path = require('path');
const fs = require('fs');
const { spawn } = require('child_process');
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright');

const FPS = 60, DUR = 15, DIR = __dirname;
const url = 'file:///' + path.join(DIR, 'index.html').replace(/\\/g, '/') + '?render=1';

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
  page.on('pageerror', e => console.error('PAGE ERROR', e.message));
  await page.goto(url);
  await page.waitForFunction(() => window.__ready === true, null, { timeout: 60000 });
  const grab = async (t, type, q) => {
    const d = await page.evaluate(([t, type, q]) => { window.renderAt(t); return document.getElementById('c').toDataURL(type, q); }, [t, type, q]);
    return Buffer.from(d.split(',')[1], 'base64');
  };

  if (process.argv[2] === 'stills') {
    fs.mkdirSync(path.join(DIR, 'stills'), { recursive: true });
    for (const s of process.argv.slice(3)) {
      fs.writeFileSync(path.join(DIR, 'stills', `t${s}.png`), await grab(+s, 'image/png'));
      console.log('still', s);
    }
    await browser.close();
    return;
  }

  const out = path.join(DIR, 'stresssense-showreel.mp4');
  const ff = spawn('ffmpeg', ['-y', '-f', 'image2pipe', '-framerate', String(FPS), '-c:v', 'mjpeg', '-i', '-',
    '-i', path.join(DIR, 'reel.wav'),
    '-c:v', 'libx264', '-preset', 'slow', '-crf', '16', '-pix_fmt', 'yuv420p', '-movflags', '+faststart',
    '-c:a', 'aac', '-b:a', '256k', '-shortest', out], { stdio: ['pipe', 'inherit', 'pipe'] });
  let err = ''; ff.stderr.on('data', d => { err += d; if (err.length > 20000) err = err.slice(-8000); });
  const total = FPS * DUR;
  for (let f = 0; f < total; f++) {
    const jpg = await grab(f / FPS, 'image/jpeg', 0.95);
    if (!ff.stdin.write(jpg)) await new Promise(r => ff.stdin.once('drain', r));
    if (f % 60 === 0) console.log(`frame ${f}/${total}`);
  }
  ff.stdin.end();
  const code = await new Promise(r => ff.on('close', r));
  await browser.close();
  if (code !== 0) { console.error(err.slice(-3000)); process.exit(1); }
  console.log('wrote', out);
})();
