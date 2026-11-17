// Synthesises the soundtrack from the same timeline the visuals use.
const fs = require('fs');
const path = require('path');
const TL = require('./timeline.js');

const SR = 48000, N = Math.ceil(TL.DUR * SR);
const L = new Float32Array(N), R = new Float32Array(N);
let seed = 7;
const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647 * 2 - 1;
const add = (i, l, r = l) => { if (i >= 0 && i < N) { L[i] += l; R[i] += r; } };

// heartbeat lub-dub on every beat
function thump(t0, gain, f0) {
  const len = Math.floor(0.22 * SR);
  let ph = 0;
  for (let k = 0; k < len; k++) {
    const x = k / SR, f = f0 * (1 + 1.4 * Math.exp(-x * 40));
    ph += 2 * Math.PI * f / SR;
    const env = Math.min(1, x / 0.004) * Math.exp(-x * 22);
    add(Math.floor(t0 * SR) + k, Math.sin(ph) * env * gain);
  }
}
for (const b of TL.beats) {
  const calm = b > 12.8 ? 0.7 : 1;
  thump(b, 0.55 * calm, 52);
  thump(b + 0.13, 0.32 * calm, 44);
}

// sub drone that follows tension
let dph = 0, dph2 = 0;
for (let i = 0; i < N; i++) {
  const t = i / SR;
  const ten = t < 10.2 ? Math.min(1, t / 6) : Math.max(0.15, 1 - (t - 10.2) / 2.6);
  const f = 36 + 10 * ten;
  dph += 2 * Math.PI * f / SR; dph2 += 2 * Math.PI * f * 1.5 / SR;
  const fade = Math.min(1, t / 0.6) * Math.min(1, (TL.DUR - t) / 0.4);
  const v = (Math.sin(dph) * 0.10 + Math.sin(dph2) * 0.035 * ten) * fade;
  add(i, v * 0.9, v);
}

// filtered-noise risers into each cut
for (const [a, b] of TL.risers) {
  let lp = 0, lp2 = 0;
  for (let i = Math.floor(a * SR); i < Math.floor(b * SR); i++) {
    const p = (i / SR - a) / (b - a);
    const k = 0.01 + 0.35 * p * p;
    lp += k * (rnd() - lp); lp2 += k * (rnd() - lp2);
    const g = 0.22 * p * p;
    add(i, lp * g, lp2 * g);
  }
}

// impacts: low boom + noise burst
for (const h of TL.hits) {
  let ph = 0, lp = 0;
  const big = h === 8.55 || h === 12.8;
  for (let k = 0; k < Math.floor(0.9 * SR); k++) {
    const x = k / SR, f = 48 + 110 * Math.exp(-x * 18);
    ph += 2 * Math.PI * f / SR;
    const boom = Math.sin(ph) * Math.exp(-x * (big ? 3.5 : 5.5)) * (big ? 0.75 : 0.55);
    lp += 0.25 * (rnd() - lp);
    const nz = lp * Math.exp(-x * 30) * 0.5;
    add(Math.floor(h * SR) + k, boom + nz, boom + nz * 0.8);
  }
}

// UI blips on ticks
TL.ticks.forEach((tk, j) => {
  const f = 1400 + (j % 5) * 220;
  for (let k = 0; k < Math.floor(0.06 * SR); k++) {
    const x = k / SR, env = Math.exp(-x * 70) * 0.13;
    const v = Math.sin(2 * Math.PI * f * x) * env;
    add(Math.floor(tk * SR) + k, v * (j % 2 ? 0.6 : 1), v * (j % 2 ? 1 : 0.6));
  }
});

// final chord bloom on the title
[220, 277.18, 329.63, 440, 554.37].forEach((f, j) => {
  const t0 = 12.8 + j * 0.02;
  for (let i = Math.floor(t0 * SR); i < N; i++) {
    const x = i / SR - t0;
    const env = Math.min(1, x / 0.25) * Math.exp(-x * 0.9) * Math.min(1, (TL.DUR - i / SR) / 0.5) * 0.045;
    const v = Math.sin(2 * Math.PI * f * x + Math.sin(2 * Math.PI * 0.3 * x) * 0.5) * env;
    add(i, v * (j % 2 ? 0.7 : 1), v * (j % 2 ? 1 : 0.7));
  }
});

// soft limiter + write 16-bit WAV
let peak = 0;
for (let i = 0; i < N; i++) peak = Math.max(peak, Math.abs(L[i]), Math.abs(R[i]));
const g = 0.89 / Math.max(peak, 1e-9);
const buf = Buffer.alloc(44 + N * 4);
buf.write('RIFF', 0); buf.writeUInt32LE(36 + N * 4, 4); buf.write('WAVE', 8);
buf.write('fmt ', 12); buf.writeUInt32LE(16, 16); buf.writeUInt16LE(1, 20); buf.writeUInt16LE(2, 22);
buf.writeUInt32LE(SR, 24); buf.writeUInt32LE(SR * 4, 28); buf.writeUInt16LE(4, 32); buf.writeUInt16LE(16, 34);
buf.write('data', 36); buf.writeUInt32LE(N * 4, 40);
for (let i = 0; i < N; i++) {
  buf.writeInt16LE(Math.round(Math.tanh(L[i] * g * 1.2) * 32000), 44 + i * 4);
  buf.writeInt16LE(Math.round(Math.tanh(R[i] * g * 1.2) * 32000), 46 + i * 4);
}
fs.writeFileSync(path.join(__dirname, 'reel.wav'), buf);
console.log('wrote reel.wav', (buf.length / 1e6).toFixed(1) + ' MB');
