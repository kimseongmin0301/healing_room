// 바다멍 · 달빛 바다: 물결, 달빛 반짝임, 달 맥동, 별 반짝임, 별똥별
import { rand } from '../scene.js';

const HORIZON = 0.634;
const MOON = [0.4995, 0.571];

export function create(scene) {
  const { ctx } = scene;
  const stars = Array.from({ length: 160 }, () => ({
    u: Math.random(), v: Math.random() * 0.58, r: rand(.6, 1.6), p: rand(0, 7), s: rand(.02, .06),
  }));
  const meteors = [];
  const glints = [];

  function draw(t) {
    const k = scene.k;

    // 1) 물결: 수평선 아래를 줄마다 좌우로 흔듦 (가까울수록 크게)
    scene.wobble(0, HORIZON + 0.002, 1, 1, 2, (v, p) =>
      (Math.sin(v * 260 - t * 0.045) * 0.6 + Math.sin(v * 90 + t * 0.03) * 0.4) * (0.4 + p * 4) * k);

    ctx.globalCompositeOperation = 'lighter';
    // 2) 달빛 반사 반짝임
    if (Math.random() < 0.9) glints.push({ u: 0.5 + rand(-1, 1) * rand(0, 1) * 0.05, v: rand(HORIZON + 0.005, 1), l: 1 });
    for (let i = glints.length - 1; i >= 0; i--) {
      const g = glints[i]; g.l -= 0.04;
      if (g.l <= 0) { glints.splice(i, 1); continue; }
      const spread = (g.v - HORIZON) / (1 - HORIZON);
      const [gx, gy] = scene.P(0.5 + (g.u - 0.5) * (0.4 + spread * 2), g.v);
      const w = 12 * k * (1 + spread * 2);
      ctx.fillStyle = `rgba(255,236,190,${g.l * 0.7})`;
      ctx.fillRect(gx - w / 2, gy, w, 1.4);
    }

    // 3) 달 은은한 맥동
    const [mx, my] = scene.P(...MOON);
    scene.glow(mx, my, 160 * k, '255,236,190', 0.08 + Math.sin(t * 0.02) * 0.03);

    // 4) 별 반짝임
    for (const s of stars) {
      const a = Math.max(0, Math.sin(t * s.s + s.p)) ** 3;
      const [sx, sy] = scene.P(s.u, s.v);
      ctx.fillStyle = `rgba(230,236,255,${a})`;
      ctx.beginPath(); ctx.arc(sx, sy, s.r * k * 1.3, 0, 7); ctx.fill();
    }

    // 5) 별똥별: 가끔 저절로 + 클릭한 곳에서
    if (Math.random() < 0.004) meteors.push({ u: rand(0.3, 0.95), v: rand(0.03, 0.25), l: 1 });
    for (let i = meteors.length - 1; i >= 0; i--) {
      const m = meteors[i]; m.u -= 0.006; m.v += 0.0035; m.l -= 0.02;
      if (m.l <= 0) { meteors.splice(i, 1); continue; }
      const [x, y] = scene.P(m.u, m.v);
      const lg = ctx.createLinearGradient(x, y, x + 90 * k, y - 50 * k);
      lg.addColorStop(0, `rgba(255,255,255,${m.l})`); lg.addColorStop(1, 'rgba(255,255,255,0)');
      ctx.strokeStyle = lg; ctx.lineWidth = 2 * k; ctx.lineCap = 'round';
      ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x + 90 * k, y - 50 * k); ctx.stroke();
    }
    ctx.globalCompositeOperation = 'source-over';
  }

  function click(u, v) {
    if (v < HORIZON) meteors.push({ u, v, l: 1 });
  }

  return { draw, click };
}
