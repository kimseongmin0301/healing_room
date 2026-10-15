// 바다멍 · 달빛 바다: 물결, 달빛 반짝임, 달 맥동, 별 반짝임, 별똥별
import { rand } from '../scene.js';

const HORIZON = 0.634;
const MOON = [0.4995, 0.571];

// 부드러운 빛 알갱이 이미지 (가운데 밝고 가장자리로 사라짐). 한 번만 만들어 두고 크기만 바꿔 그린다
const SPARKLE = (() => {
  const c = Object.assign(document.createElement('canvas'), { width: 32, height: 32 });
  const g = c.getContext('2d');
  const gradient = g.createRadialGradient(16, 16, 0, 16, 16, 16);
  gradient.addColorStop(0, 'rgba(255,242,210,1)');
  gradient.addColorStop(0.35, 'rgba(255,230,180,.55)');
  gradient.addColorStop(1, 'rgba(255,220,160,0)');
  g.fillStyle = gradient;
  g.fillRect(0, 0, 32, 32);
  return c;
})();

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
    // 2) 달빛 반사 반짝임: 가장자리가 흐린 빛 알갱이가 달빛 길 안에서 서서히 켜졌다 꺼진다
    if (Math.random() < 0.7) {
      glints.push({
        off: rand(-1, 1) * Math.random(),                                  // 달빛 길 가운데에 더 많이
        v: HORIZON + 0.005 + Math.random() ** 1.3 * (1 - HORIZON - 0.005), // 수평선 가까이 조금 더 촘촘히
        age: 0, life: rand(25, 60), size: rand(0.6, 1.4),
      });
    }
    for (let i = glints.length - 1; i >= 0; i--) {
      const g = glints[i];
      const p = ++g.age / g.life;
      if (p >= 1) { glints.splice(i, 1); continue; }
      const spread = (g.v - HORIZON) / (1 - HORIZON);                // 0 수평선 ~ 1 화면 아래
      const [gx, gy] = scene.P(MOON[0] + g.off * (0.012 + spread * 0.05), g.v); // 가까울수록 달빛 길이 넓어진다
      const w = (6 + spread * 14) * k * g.size, h = w * 0.35;
      ctx.globalAlpha = Math.sin(Math.PI * p) * 0.55;
      ctx.drawImage(SPARKLE, gx - w / 2, gy - h / 2, w, h);
    }
    ctx.globalAlpha = 1;

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
