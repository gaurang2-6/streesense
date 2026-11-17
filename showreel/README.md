# StressSense — 15s motion showreel

A 15-second, 1080p60 motion piece about how StressSense works: four body signals, a heavy gradient-boosted teacher (93.5%), and the student it's distilled into, an INT8 model of 5.77 KB at 91.2%. The student runs on the phone in under 1.8 ms.

Every frame is drawn by `index.html` as a pure function of time. The soundtrack is synthesised by `audio.js` from the same `timeline.js`, so each heartbeat and impact lands on its frame.

- `index.html`: open it in a browser to watch it live; add `?t=8.6` to jump to a time.
- `node audio.js`: writes `reel.wav`.
- `node render.js`: renders the MP4 (needs Playwright and ffmpeg; set `PLAYWRIGHT_PATH` if Playwright isn't installed locally).
