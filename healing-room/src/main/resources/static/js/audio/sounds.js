// 환경음을 녹음 파일 없이 Web Audio 로 합성한다. 저작권 걱정이 없고 파일도 필요 없다.
// 각 소리는 create(ctx, output) → { stop() } 형태이며, output(GainNode)으로 소리를 낸다.
// 키(fire, wind ...)는 DB room_ambient.sound 와 같아야 한다.

const rand = (a, b) => a + Math.random() * (b - a);

// ---------------------------------------------------------------- 노이즈 재료

const buffers = new WeakMap();

/** 4초짜리 노이즈 버퍼 (white: 쉬익 / pink: 부드러운 쉬익 / brown: 낮은 웅웅) */
function noiseBuffer(ctx, type) {
  let cache = buffers.get(ctx);
  if (!cache) buffers.set(ctx, cache = {});
  if (cache[type]) return cache[type];

  const length = ctx.sampleRate * 4;
  const buffer = ctx.createBuffer(1, length, ctx.sampleRate);
  const data = buffer.getChannelData(0);
  let b0 = 0, b1 = 0, b2 = 0, last = 0;
  for (let i = 0; i < length; i++) {
    const white = Math.random() * 2 - 1;
    if (type === 'pink') {
      b0 = 0.99765 * b0 + white * 0.0990460;
      b1 = 0.96300 * b1 + white * 0.2965164;
      b2 = 0.57000 * b2 + white * 1.0526913;
      data[i] = (b0 + b1 + b2 + white * 0.1848) * 0.2;
    } else if (type === 'brown') {
      last = (last + 0.02 * white) / 1.02;
      data[i] = last * 3.5;
    } else {
      data[i] = white;
    }
  }
  return (cache[type] = buffer);
}

function noise(ctx, type) {
  const src = ctx.createBufferSource();
  src.buffer = noiseBuffer(ctx, type);
  src.loop = true;
  src.start(0, Math.random() * 3);
  return src;
}

function filter(ctx, type, frequency, Q = 0.7) {
  const f = ctx.createBiquadFilter();
  f.type = type;
  f.frequency.value = frequency;
  f.Q.value = Q;
  return f;
}

function gain(ctx, value) {
  const g = ctx.createGain();
  g.gain.value = value;
  return g;
}

/** 느리게 출렁이는 LFO 를 param 에 연결 (center ± depth) */
function lfo(ctx, param, rate, depth) {
  const osc = ctx.createOscillator();
  osc.frequency.value = rate;
  const amount = gain(ctx, depth);
  osc.connect(amount).connect(param);
  osc.start();
  return osc;
}

/** 짧은 소리(타닥, 톡)를 불규칙하게 계속 예약하는 도우미 */
function scheduler(ctx, everyMs, schedule) {
  let next = ctx.currentTime + 0.1;
  const timer = setInterval(() => {
    while (next < ctx.currentTime + 0.3) next = schedule(next);
  }, everyMs);
  return () => clearInterval(timer);
}

/** time 에 짧은 노이즈 한 번 */
function burst(ctx, output, time, { freq, q = 1, length, level }) {
  const src = ctx.createBufferSource();
  src.buffer = noiseBuffer(ctx, 'white');
  const bp = filter(ctx, 'bandpass', freq, q);
  const env = gain(ctx, 0);
  env.gain.setValueAtTime(0, time);
  env.gain.linearRampToValueAtTime(level, time + 0.002);
  env.gain.exponentialRampToValueAtTime(0.0001, time + length);
  src.connect(bp).connect(env).connect(output);
  src.start(time, Math.random() * 3, length + 0.05);
}

// ---------------------------------------------------------------- 소리들

export const SOUNDS = {
  /** 장작: 낮게 타오르는 소리 + 타닥타닥 */
  fire(ctx, out) {
    const roar = noise(ctx, 'brown');
    const lp = filter(ctx, 'lowpass', 380);
    const roarGain = gain(ctx, 0.55);
    roar.connect(lp).connect(roarGain).connect(out);
    const flicker = lfo(ctx, roarGain.gain, 0.3, 0.15);

    const stopCrackle = scheduler(ctx, 80, t => {
      const pop = Math.random() < 0.08;
      burst(ctx, out, t, {
        freq: pop ? rand(600, 1400) : rand(1800, 6000), q: rand(0.8, 2),
        length: pop ? rand(0.05, 0.12) : rand(0.008, 0.03), level: pop ? rand(0.5, 0.9) : rand(0.08, 0.35),
      });
      return t + (Math.random() < 0.3 ? rand(0.01, 0.05) : rand(0.06, 0.35));
    });
    return { stop() { stopCrackle(); roar.stop(); flicker.stop(); } };
  },

  /** 바람: 높낮이와 세기가 천천히 바뀌는 쉬익 */
  wind(ctx, out) {
    const src = noise(ctx, 'pink');
    const bp = filter(ctx, 'bandpass', 500, 0.9);
    const g = gain(ctx, 0.5);
    src.connect(bp).connect(g).connect(out);
    const pitch = lfo(ctx, bp.frequency, 0.07, 260);
    const swell = lfo(ctx, g.gain, 0.11, 0.3);
    return { stop() { src.stop(); pitch.stop(); swell.stop(); } };
  },

  /** 비: 고르게 쏟아지는 소리 + 가끔 톡 */
  rain(ctx, out) {
    const src = noise(ctx, 'white');
    const hp = filter(ctx, 'highpass', 900);
    const lp = filter(ctx, 'lowpass', 7000);
    const g = gain(ctx, 0.22);
    src.connect(hp).connect(lp).connect(g).connect(out);
    const stopDrops = scheduler(ctx, 60, t => {
      burst(ctx, out, t, { freq: rand(2500, 7000), q: 3, length: rand(0.005, 0.015), level: rand(0.05, 0.2) });
      return t + rand(0.01, 0.08);
    });
    return { stop() { stopDrops(); src.stop(); } };
  },

  /** 파도: 밀려왔다(커지고 밝아짐) 빠져나가는 소리를 반복 */
  waves(ctx, out) {
    const src = noise(ctx, 'brown');
    const lp = filter(ctx, 'lowpass', 400);
    const g = gain(ctx, 0.08);
    src.connect(lp).connect(g).connect(out);
    const stopSwell = scheduler(ctx, 500, t => {
      const rise = rand(2.5, 4), fall = rand(3.5, 5.5), peak = rand(0.6, 1);
      g.gain.setTargetAtTime(peak, t, rise / 3);
      lp.frequency.setTargetAtTime(rand(1100, 1800), t, rise / 3);
      g.gain.setTargetAtTime(0.08, t + rise, fall / 3);
      lp.frequency.setTargetAtTime(400, t + rise, fall / 3);
      return t + rise + fall + rand(0, 1.5);
    });
    return { stop() { stopSwell(); src.stop(); } };
  },

  /** 풀벌레: 높은 음으로 찌르르 */
  crickets(ctx, out) {
    const stops = [4300, 5100].map((freq, i) => {
      const osc = ctx.createOscillator();
      osc.frequency.value = freq;
      const env = gain(ctx, 0);
      osc.connect(env).connect(out);
      osc.start();
      const stop = scheduler(ctx, 100, t => {
        // 한 번 울 때 짧은 펄스 3~4개
        const pulses = 3 + (Math.random() < 0.5 ? 1 : 0);
        for (let p = 0; p < pulses; p++) {
          const s = t + p * 0.045;
          env.gain.setValueAtTime(0, s);
          env.gain.linearRampToValueAtTime(0.12, s + 0.008);
          env.gain.linearRampToValueAtTime(0, s + 0.03);
        }
        return t + rand(0.5, 1.1) + i * 0.13;
      });
      return () => { stop(); osc.stop(); };
    });
    return { stop() { stops.forEach(s => s()); } };
  },
};
