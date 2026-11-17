// Shared timeline: the page and the audio synth read the same beats and hits,
// so every thump on screen lands on a thump in the soundtrack.
(function (g) {
  const DUR = 15;
  // heart rate: rises under stress, holds, then calms once the model is on device
  function bpm(t) {
    const ss = x => x <= 0 ? 0 : x >= 1 ? 1 : x * x * (3 - 2 * x);
    if (t < 2.2) return 70 + 56 * ss(t / 2.2);
    if (t < 10.2) return 126 + 5 * Math.sin(t * 2.1);
    if (t < 12.8) return 126 - 52 * ss((t - 10.2) / 2.6);
    return 72;
  }
  const beats = [];
  let ph = 0.62;
  for (let t = 0; t < DUR; t += 0.001) {
    const n = ph + bpm(t) / 60 * 0.001;
    if (Math.floor(n) > Math.floor(ph)) beats.push(+t.toFixed(3));
    ph = n;
  }
  // scene boundaries and impact hits (cuts, slams)
  const scenes = [
    { id: 'pulse',   a: 0.0,  b: 2.2,  n: '00', label: 'Field recording' },
    { id: 'signals', a: 2.2,  b: 4.7,  n: '01', label: 'Signals' },
    { id: 'teacher', a: 4.7,  b: 7.3,  n: '02', label: 'Teacher' },
    { id: 'distil',  a: 7.3,  b: 10.2, n: '03', label: 'Distil + INT8' },
    { id: 'device',  a: 10.2, b: 12.8, n: '04', label: 'On device' },
    { id: 'title',   a: 12.8, b: 15.0, n: '05', label: 'StressSense' },
  ];
  const hits = [2.2, 4.7, 7.3, 8.55, 10.2, 12.8, 13.25];
  const ticks = [2.55, 2.75, 2.95, 3.15, 5.0, 5.9, 6.6, 9.0, 10.95, 11.05, 11.15, 11.25, 11.35, 11.45, 11.55, 11.65, 11.75, 13.9, 14.05, 14.2];
  const risers = [[1.6, 2.2], [4.2, 4.7], [6.8, 7.3], [9.7, 10.2], [12.2, 12.8]];
  const TL = { DUR, bpm, beats, scenes, hits, ticks, risers };
  g.TL = TL;
  if (typeof module !== 'undefined') module.exports = TL;
})(typeof window !== 'undefined' ? window : globalThis);
