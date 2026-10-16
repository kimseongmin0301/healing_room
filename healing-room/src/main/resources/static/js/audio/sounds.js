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

// 부드럽게 커졌다 작아지는 곡선 (Hann 창). 딱딱 끊기는 기계음 대신 자연스러운 소리가 난다
const HANN = Float32Array.from({ length: 32 }, (_, i) => Math.sin((Math.PI * i) / 31) ** 2);

/** 귀뚜라미 "찍" 한 번: 기본음 + 배음, 음이 살짝 내려가며 부드럽게 커졌다 작아진다 */
function chirp(ctx, output, time, freq) {
  const length = rand(0.012, 0.02);
  const env = gain(ctx, 0);
  env.gain.setValueCurveAtTime(HANN, time, length);
  env.connect(output);
  for (const [multiple, level] of [[1, 1], [2, 0.18]]) {
    const osc = ctx.createOscillator();
    osc.frequency.setValueAtTime(freq * multiple, time);
    osc.frequency.exponentialRampToValueAtTime(freq * multiple * 0.96, time + length);
    const g = gain(ctx, level);
    osc.connect(g).connect(env);
    osc.start(time);
    osc.stop(time + length + 0.01);
  }
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

  /**
   * 풀벌레 (실제 귀뚜라미처럼)
   *  ① 한 번 울 때 아주 짧은 "찍"(음절) 3~5개. 음절마다 부드럽게 커졌다 작아지고 음이 살짝 내려가며 배음이 섞인다
   *  ② 높이·박자·거리·좌우가 다른 여러 마리. 멀수록 작고 먹먹하게, 가끔 쉬었다 운다
   *  ③ 멀리서 들리는 "쓰르르" 합창이 아주 작게 깔린다
   */
  crickets(ctx, out) {
    const stops = [];

    // ③ 멀리서 들리는 합창: 4~5kHz 대역의 잡음을 빠르게 떨리게
    const bed = noise(ctx, 'white');
    const band = filter(ctx, 'bandpass', 4600, 8);
    const trillGain = gain(ctx, 0.5);
    const bedLevel = gain(ctx, 0.05);
    bed.connect(band).connect(trillGain).connect(bedLevel).connect(out);
    const trill = lfo(ctx, trillGain.gain, 38, 0.5);   // 초당 38번 떨림
    const swell = lfo(ctx, bedLevel.gain, 0.07, 0.03); // 천천히 커졌다 작아짐
    stops.push(() => { bed.stop(); trill.stop(); swell.stop(); });

    // ①② 가까이·멀리 있는 여러 마리
    for (let i = 0; i < 5; i++) {
      const distance = Math.random();                       // 0 가까움 ~ 1 멀리
      const level = gain(ctx, 0.09 * (1 - distance * 0.75));
      const muffle = filter(ctx, 'lowpass', 9000 - distance * 5000);
      level.connect(muffle);
      if (ctx.createStereoPanner) {
        const pan = ctx.createStereoPanner();
        pan.pan.value = rand(-0.85, 0.85);
        muffle.connect(pan).connect(out);
      } else {
        muffle.connect(out);
      }
      const freq = rand(3800, 5200);          // 마리마다 다른 높이
      const gap = rand(0.028, 0.045);         // 음절 사이 간격
      const syllables = 3 + Math.floor(Math.random() * 3);
      stops.push(scheduler(ctx, 90, t => {
        const n = syllables + (Math.random() < 0.3 ? 1 : 0);
        for (let k = 0; k < n; k++) chirp(ctx, level, t + k * gap, freq * rand(0.995, 1.005));
        return t + n * gap + (Math.random() < 0.15 ? rand(1.5, 4) : rand(0.35, 0.9));
      }));
    }
    return { stop() { stops.forEach(stop => stop()); } };
  },

  /** 시냇물: 졸졸 흐르는 물소리 + 가끔 "퐁" 물방울 */
  stream(ctx, out) {
    const src = noise(ctx, 'pink');
    const bp = filter(ctx, 'bandpass', 1200, 0.8);
    const g = gain(ctx, 0.45);
    src.connect(bp).connect(g).connect(out);
    const low = noise(ctx, 'brown');
    const lowGain = gain(ctx, 0.25);
    low.connect(filter(ctx, 'lowpass', 500)).connect(lowGain).connect(out);
    const ripple = lfo(ctx, bp.frequency, 0.4, 350);
    const swell = lfo(ctx, g.gain, 0.17, 0.12);

    const stopBubbles = scheduler(ctx, 60, t => {
      const f = rand(500, 1300), length = rand(0.03, 0.07);
      const osc = ctx.createOscillator();
      osc.frequency.setValueAtTime(f, t);
      osc.frequency.exponentialRampToValueAtTime(f * rand(1.5, 2.2), t + length); // 물방울은 음이 올라간다
      const env = gain(ctx, 0);
      env.gain.setValueCurveAtTime(HANN.map(v => v * rand(0.03, 0.09)), t, length);
      osc.connect(env).connect(out);
      osc.start(t); osc.stop(t + length + 0.01);
      return t + (Math.random() < 0.35 ? rand(0.03, 0.12) : rand(0.15, 0.6));
    });
    return { stop() { stopBubbles(); src.stop(); low.stop(); ripple.stop(); swell.stop(); } };
  },

  /** 천둥: 멀리서 가끔 우르르 (빗소리와 함께 켜 두면 좋다) */
  thunder(ctx, out) {
    const stopRumble = scheduler(ctx, 1000, t => {
      const src = ctx.createBufferSource();
      src.buffer = noiseBuffer(ctx, 'brown');
      const lp = filter(ctx, 'lowpass', rand(140, 320));
      const env = gain(ctx, 0);
      const length = rand(4, 8), peak = rand(0.6, 1.2);
      env.gain.setValueAtTime(0, t);
      env.gain.linearRampToValueAtTime(peak, t + rand(0.2, 0.6));
      // 우르르: 몇 번 다시 커졌다 잦아든다
      for (let s = 1; s < 4; s++) env.gain.setTargetAtTime(peak * rand(0.4, 0.9) / s, t + s * rand(0.5, 1.2), 0.3);
      env.gain.setTargetAtTime(0.0001, t + length * 0.45, length / 5);
      src.connect(lp).connect(env).connect(out);
      src.start(t, Math.random() * 3, length + 0.5);
      return t + rand(14, 35);
    });
    return { stop() { stopRumble(); } };
  },

  /** 우주정거장: 낮게 웅웅 울리는 기계 소리와 공기 순환 소리 */
  hum(ctx, out) {
    const stops = [];
    const toneLevel = gain(ctx, 0.5);
    toneLevel.connect(filter(ctx, 'lowpass', 400)).connect(out);
    for (const [f, level] of [[55, 0.12], [110.4, 0.06], [165, 0.025], [220.7, 0.012]]) {
      const osc = ctx.createOscillator();
      osc.type = 'triangle';
      osc.frequency.value = f;
      const g = gain(ctx, level);
      osc.connect(g).connect(toneLevel);
      osc.start();
      stops.push(() => osc.stop());
    }
    const air = noise(ctx, 'brown');
    const airGain = gain(ctx, 0.35);
    air.connect(filter(ctx, 'lowpass', 260)).connect(airGain).connect(out);
    const breathe = lfo(ctx, toneLevel.gain, 0.05, 0.15);
    stops.push(() => { air.stop(); breathe.stop(); });
    return { stop() { stops.forEach(stop => stop()); } };
  },

  /** 우주: 별빛 같은 신비로운 화음. 5음계라 어떤 음이 겹쳐도 어울린다 */
  pad(ctx, out) {
    const stops = [];
    const lp = filter(ctx, 'lowpass', 1400);
    const master = gain(ctx, 1.1);
    lp.connect(master).connect(out);
    const sweep = lfo(ctx, lp.frequency, 0.03, 600);
    stops.push(() => sweep.stop());
    // A 단조 5음계 (A C D E G)
    for (const f of [110, 164.8, 220, 261.6, 293.7, 329.6, 392, 440, 523.3]) {
      const g = gain(ctx, 0);
      g.connect(lp);
      for (const detune of [-6, 6]) {
        const osc = ctx.createOscillator();
        osc.type = f < 200 ? 'sine' : 'triangle';
        osc.frequency.value = f;
        osc.detune.value = detune;
        osc.connect(g);
        osc.start();
        stops.push(() => osc.stop());
      }
      // 음마다 제각각 천천히 나타났다 사라진다
      stops.push(scheduler(ctx, 500, t => {
        const level = Math.random() < 0.45 ? rand(0.02, 0.06) : 0;
        g.gain.setTargetAtTime(level, t, rand(1.5, 3));
        return t + rand(4, 10);
      }));
    }
    return { stop() { stops.forEach(stop => stop()); } };
  },

  /** 도시: 멀리서 들리는 차 소리와 웅성임, 가끔 지나가는 차 */
  city(ctx, out) {
    const bed = noise(ctx, 'brown');
    const bedGain = gain(ctx, 0.5);
    bed.connect(filter(ctx, 'lowpass', 450)).connect(bedGain).connect(out);
    const murmur = noise(ctx, 'pink');
    const murmurGain = gain(ctx, 0.06);
    murmur.connect(filter(ctx, 'bandpass', 900, 1.2)).connect(murmurGain).connect(out);
    const drift = lfo(ctx, bedGain.gain, 0.06, 0.12);

    const stopCars = scheduler(ctx, 500, t => {
      const src = ctx.createBufferSource();
      src.buffer = noiseBuffer(ctx, 'pink');
      const bp = filter(ctx, 'bandpass', rand(500, 900), 0.9);
      const env = gain(ctx, 0);
      const length = rand(3, 6);
      env.gain.setValueCurveAtTime(HANN.map(v => v * rand(0.15, 0.35)), t, length);
      bp.frequency.setValueAtTime(bp.frequency.value * 1.15, t);
      bp.frequency.linearRampToValueAtTime(bp.frequency.value * 0.85, t + length); // 지나가며 음이 낮아짐
      let last = env;
      if (ctx.createStereoPanner) {
        const pan = ctx.createStereoPanner();
        const from = Math.random() < 0.5 ? -0.8 : 0.8;
        pan.pan.setValueAtTime(from, t);
        pan.pan.linearRampToValueAtTime(-from, t + length);
        env.connect(pan);
        last = pan;
      }
      src.connect(bp).connect(env);
      last.connect(out);
      src.start(t, Math.random() * 3, length + 0.1);
      return t + rand(2, 9);
    });
    return { stop() { stopCars(); bed.stop(); murmur.stop(); drift.stop(); } };
  },

  /** 갈매기: 멀리서 가끔 "끼룩끼룩" */
  seagulls(ctx, out) {
    const call = (t, base, level, pan) => {
      const length = rand(0.25, 0.4);
      const env = gain(ctx, 0);
      env.gain.setValueCurveAtTime(HANN.map(v => v * level), t, length);
      const bp = filter(ctx, 'bandpass', base * 1.3, 2);
      let last = bp;
      if (ctx.createStereoPanner) {
        const p = ctx.createStereoPanner();
        p.pan.value = pan;
        bp.connect(p);
        last = p;
      }
      env.connect(bp);
      last.connect(out);
      for (const detune of [0, 14]) {
        const osc = ctx.createOscillator();
        osc.type = 'sawtooth';
        osc.detune.value = detune;
        osc.frequency.setValueAtTime(base, t);
        osc.frequency.linearRampToValueAtTime(base * 1.35, t + length * 0.3); // 끼-
        osc.frequency.linearRampToValueAtTime(base * 0.8, t + length);        // -룩
        osc.connect(env);
        osc.start(t); osc.stop(t + length + 0.02);
      }
      return length;
    };
    const stopCalls = scheduler(ctx, 500, t => {
      const base = rand(1100, 1500), level = rand(0.1, 0.25), pan = rand(-0.8, 0.8);
      let at = t;
      for (let i = 0, n = 2 + Math.floor(Math.random() * 3); i < n; i++) at += call(at, base * rand(0.97, 1.03), level, pan) + rand(0.05, 0.15);
      return at + rand(5, 15);
    });
    return { stop() { stopCalls(); } };
  },

  /** 라운지 비트: 옆방에서 들리는 듯 먹먹한 하우스 리듬 (분당 100박) */
  beat(ctx, out) {
    const lp = filter(ctx, 'lowpass', 1600);
    const bus = gain(ctx, 0.7);
    lp.connect(bus).connect(out);
    const step = 60 / 100 / 4; // 16분음표
    let n = 0;

    const kick = t => {
      const osc = ctx.createOscillator();
      osc.frequency.setValueAtTime(130, t);
      osc.frequency.exponentialRampToValueAtTime(42, t + 0.12);
      const env = gain(ctx, 0);
      env.gain.setValueAtTime(0.9, t);
      env.gain.exponentialRampToValueAtTime(0.001, t + 0.35);
      osc.connect(env).connect(lp);
      osc.start(t); osc.stop(t + 0.4);
    };
    const bassNotes = [55, 55, 65.4, 49];
    const bass = (t, bar) => {
      const osc = ctx.createOscillator();
      osc.type = 'triangle';
      osc.frequency.value = bassNotes[bar % bassNotes.length];
      const env = gain(ctx, 0);
      env.gain.setValueAtTime(0, t);
      env.gain.linearRampToValueAtTime(0.25, t + 0.02);
      env.gain.exponentialRampToValueAtTime(0.001, t + step * 1.8);
      osc.connect(env).connect(lp);
      osc.start(t); osc.stop(t + step * 2);
    };

    const stopBeat = scheduler(ctx, 50, t => {
      const i = n++ % 16, bar = Math.floor(n / 16);
      if (i % 4 === 0) kick(t);
      if (i % 4 === 2) {
        burst(ctx, lp, t, { freq: 8000, q: 0.8, length: 0.05, level: 0.18 }); // 하이햇
        bass(t, bar);
      }
      if (i === 4 || i === 12) burst(ctx, lp, t, { freq: 1800, q: 0.9, length: 0.15, level: 0.35 }); // 클랩
      if (Math.random() < 0.15) burst(ctx, lp, t, { freq: 9000, q: 1, length: 0.02, level: 0.06 });
      return t + step;
    });
    return { stop() { stopBeat(); } };
  },
};
