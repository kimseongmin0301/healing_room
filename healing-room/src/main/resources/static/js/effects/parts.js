// 여러 테마가 함께 쓰는 효과 조각들 (별, 별똥별, 눈, 비, 반딧불, 빛 알갱이, 김·안개)
// 이 파일은 효과 모듈이 아니라 도우미다. 좌표는 모두 배경 기준 0~1 (u, v)
import { rand } from '../scene.js';

/** 부드러운 빛 알갱이 이미지 (가운데 밝고 가장자리로 사라짐). 크기만 바꿔 그린다 */
export function sprite(rgb) {
  const c = Object.assign(document.createElement('canvas'), { width: 32, height: 32 });
  const g = c.getContext('2d');
  const gradient = g.createRadialGradient(16, 16, 0, 16, 16, 16);
  gradient.addColorStop(0, `rgba(${rgb},1)`);
  gradient.addColorStop(0.35, `rgba(${rgb},.5)`);
  gradient.addColorStop(1, `rgba(${rgb},0)`);
  g.fillStyle = gradient;
  g.fillRect(0, 0, 32, 32);
  return c;
}

/** 반짝이는 별. area = [u0, v0, u1, v1] */
export function stars(scene, area, count, rgb = '230,236,255') {
  const [u0, v0, u1, v1] = area;
  const list = Array.from({ length: count }, () => ({
    u: rand(u0, u1), v: rand(v0, v1), r: rand(.6, 1.6), p: rand(0, 7), s: rand(.02, .06),
  }));
  return t => {
    const { ctx, k } = scene;
    for (const s of list) {
      const a = Math.max(0, Math.sin(t * s.s + s.p)) ** 3;
      if (a < 0.02) continue;
      const [x, y] = scene.P(s.u, s.v);
      ctx.fillStyle = `rgba(${rgb},${a})`;
      ctx.beginPath(); ctx.arc(x, y, s.r * k * 1.3, 0, 7); ctx.fill();
    }
  };
}

/** 별똥별: 가끔 저절로(area 안에서) + add(u, v) 로 클릭한 곳에서 */
export function meteors(scene, area, chance = 0.004) {
  const list = [];
  const [u0, v0, u1, v1] = area;
  const draw = () => {
    const { ctx, k } = scene;
    if (Math.random() < chance) list.push({ u: rand(u0 + (u1 - u0) * 0.3, u1), v: rand(v0, v0 + (v1 - v0) * 0.5), l: 1 });
    for (let i = list.length - 1; i >= 0; i--) {
      const m = list[i]; m.u -= 0.006; m.v += 0.0035; m.l -= 0.02;
      if (m.l <= 0) { list.splice(i, 1); continue; }
      const [x, y] = scene.P(m.u, m.v);
      const lg = ctx.createLinearGradient(x, y, x + 90 * k, y - 50 * k);
      lg.addColorStop(0, `rgba(255,255,255,${m.l})`); lg.addColorStop(1, 'rgba(255,255,255,0)');
      ctx.strokeStyle = lg; ctx.lineWidth = 2 * k; ctx.lineCap = 'round';
      ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x + 90 * k, y - 50 * k); ctx.stroke();
    }
  };
  draw.add = (u, v) => list.push({ u, v, l: 1 });
  return draw;
}

/** 내리는 눈. 화면 전체를 천천히 떨어지며 좌우로 흔들린다. gust() 로 잠깐 세게 */
export function snow(scene, count) {
  const list = Array.from({ length: count }, () => ({
    u: Math.random(), v: Math.random(), r: rand(.6, 2.6), s: rand(.0006, .0018), w: rand(0, 6),
  }));
  let wind = 0;
  const draw = t => {
    const { ctx, k } = scene;
    wind *= 0.985;
    ctx.fillStyle = 'rgba(240,245,255,.85)';
    for (const s of list) {
      s.v += s.s * (1 + wind * 2); s.u += wind * 0.002 * s.r;
      if (s.v > 1.02) { s.v = -0.02; s.u = Math.random(); }
      if (s.u > 1.02) s.u -= 1.04;
      const [x, y] = scene.P(s.u + Math.sin(t * 0.02 + s.w) * 0.004, s.v);
      ctx.beginPath(); ctx.arc(x, y, s.r * k, 0, 7); ctx.fill();
    }
  };
  draw.gust = () => { wind = 1; };
  return draw;
}

/** 빗줄기: 비스듬히 떨어지는 가는 선. area 안에서만 */
export function rain(scene, count, area = [0, 0, 1, 1], slant = 0.12) {
  const [u0, v0, u1, v1] = area;
  const list = Array.from({ length: count }, () => ({ u: rand(u0, u1), v: rand(v0, v1), s: rand(.018, .03), l: rand(.025, .05), a: rand(.1, .3) }));
  return () => {
    const { ctx, k } = scene;
    ctx.lineWidth = Math.max(0.6, 1.1 * k);
    for (const d of list) {
      d.v += d.s; d.u += d.s * slant * 0.56;
      if (d.v > v1) { d.v = v0 - d.l; d.u = rand(u0, u1); }
      const [x0, y0] = scene.P(d.u, d.v), [x1, y1] = scene.P(d.u + d.l * slant * 0.56, d.v + d.l);
      ctx.strokeStyle = `rgba(200,215,235,${d.a})`;
      ctx.beginPath(); ctx.moveTo(x0, y0); ctx.lineTo(x1, y1); ctx.stroke();
    }
  };
}

/** 물웅덩이에 떨어지는 빗방울 동그라미 */
export function ripples(scene, area, chance = 0.4) {
  const [u0, v0, u1, v1] = area;
  const list = [];
  const draw = () => {
    const { ctx, k } = scene;
    if (Math.random() < chance) list.push({ u: rand(u0, u1), v: rand(v0, v1), age: 0 });
    ctx.lineWidth = Math.max(0.6, k);
    for (let i = list.length - 1; i >= 0; i--) {
      const r = list[i], p = ++r.age / 40;
      if (p >= 1) { list.splice(i, 1); continue; }
      const [x, y] = scene.P(r.u, r.v);
      const size = (4 + p * 22) * k * (0.5 + (r.v - v0) / (v1 - v0 + 1e-6));
      ctx.strokeStyle = `rgba(210,225,240,${0.35 * (1 - p)})`;
      ctx.beginPath(); ctx.ellipse(x, y, size, size * 0.3, 0, 0, 7); ctx.stroke();
    }
  };
  draw.add = (u, v) => list.push({ u, v, age: 0 });
  return draw;
}

/** 반딧불: 천천히 떠다니며 숨 쉬듯 깜빡인다. burst(u, v) 로 한 무리 날아오름 */
export function fireflies(scene, area, count, rgb = '200,255,140') {
  const [u0, v0, u1, v1] = area;
  const img = sprite(rgb);
  const make = (u, v) => ({ u, v, du: rand(-1, 1) * 0.0004, dv: rand(-1, 1) * 0.0003, p: rand(0, 7), s: rand(.015, .04), life: Infinity });
  const list = Array.from({ length: count }, () => make(rand(u0, u1), rand(v0, v1)));
  const draw = t => {
    const { ctx, k } = scene;
    for (let i = list.length - 1; i >= 0; i--) {
      const f = list[i];
      f.du += rand(-1, 1) * 0.00004; f.dv += rand(-1, 1) * 0.00004;
      f.du = Math.max(-0.0006, Math.min(0.0006, f.du)); f.dv = Math.max(-0.0005, Math.min(0.0005, f.dv));
      f.u += f.du; f.v += f.dv;
      if (f.u < u0 || f.u > u1) f.du *= -1;
      if (f.v < v0 || f.v > v1) f.dv *= -1;
      if (--f.life <= 0) { list.splice(i, 1); continue; }
      const a = (0.25 + 0.75 * Math.max(0, Math.sin(t * f.s + f.p)) ** 2) * Math.min(1, f.life / 60);
      const [x, y] = scene.P(f.u, f.v);
      const size = 14 * k;
      ctx.globalAlpha = a;
      ctx.drawImage(img, x - size / 2, y - size / 2, size, size);
    }
    ctx.globalAlpha = 1;
  };
  draw.burst = (u, v) => {
    for (let i = 0; i < 12; i++) {
      const f = make(u + rand(-0.02, 0.02), v + rand(-0.02, 0.02));
      f.dv = -rand(0.0005, 0.0012); f.life = rand(200, 400);
      list.push(f);
    }
  };
  return draw;
}

/** 수면 위 반짝임: 빛 길(center u)을 따라 빛 알갱이가 켜졌다 꺼진다 */
export function glints(scene, { u, from, to, narrow = 0.012, wide = 0.05, rgb = '255,236,200', chance = 0.7 }) {
  const img = sprite(rgb);
  const list = [];
  return () => {
    const { ctx, k } = scene;
    if (Math.random() < chance) {
      list.push({ off: rand(-1, 1) * Math.random(), v: from + Math.random() ** 1.3 * (to - from), age: 0, life: rand(25, 60), size: rand(0.6, 1.4) });
    }
    for (let i = list.length - 1; i >= 0; i--) {
      const g = list[i];
      const p = ++g.age / g.life;
      if (p >= 1) { list.splice(i, 1); continue; }
      const spread = (g.v - from) / (to - from);
      const [x, y] = scene.P(u + g.off * (narrow + spread * wide), g.v);
      const w = (6 + spread * 14) * k * g.size, h = w * 0.35;
      ctx.globalAlpha = Math.sin(Math.PI * p) * 0.55;
      ctx.drawImage(img, x - w / 2, y - h / 2, w, h);
    }
    ctx.globalAlpha = 1;
  };
}

/** 김·안개: 크고 흐린 덩어리가 천천히 흘러간다. rise 가 있으면 위로 올라가며 사라진다(커피 김) */
export function wisps(scene, { u, v, spread = 0.01, size = 30, rise = 0, drift = 0, count = 12, alpha = 0.12, rgb = '255,255,255' }) {
  const img = sprite(rgb);
  const fresh = () => ({ u: u + rand(-spread, spread), v, age: rand(0, 1), speed: rand(0.6, 1.4), w: rand(0, 6) });
  const list = Array.from({ length: count }, fresh);
  return t => {
    const { ctx, k } = scene;
    for (const m of list) {
      m.age += 0.0025 * m.speed;
      if (m.age >= 1) Object.assign(m, fresh(), { age: 0 });
      const x = m.u + Math.sin(t * 0.01 + m.w) * spread * 2 + drift * m.age;
      const y = m.v - rise * m.age;
      const [px, py] = scene.P(x, y);
      const s = size * k * (0.6 + m.age * (rise ? 1.6 : 0.6));
      ctx.globalAlpha = alpha * Math.sin(Math.PI * m.age);
      ctx.drawImage(img, px - s / 2, py - s / 2, s, s);
    }
    ctx.globalAlpha = 1;
  };
}

/** 깜빡이는 불빛 (랜턴, 창문). lights = [[u, v, 반지름(배경 너비 비율)], ...] */
export function flicker(scene, lights, rgb = '255,180,80', strength = 0.22) {
  return t => {
    for (const [u, v, r] of lights) {
      const [x, y] = scene.P(u, v);
      scene.glow(x, y, r * scene.fit.w, rgb, strength + Math.random() * strength * 0.5 + Math.sin(t * 0.2 + u * 50) * strength * 0.25);
    }
  };
}
