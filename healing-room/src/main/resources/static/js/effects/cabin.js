// 불멍 · 겨울 오두막: 불꽃 일렁임, 불빛·랜턴 깜빡임, 불씨, 창밖 눈
import { rand } from '../scene.js';

const FIRE = [0.574, 0.60];
const WINDOWS = [[0.075, 0.03, 0.285, 0.40], [0.705, 0.10, 0.95, 0.455]];
const LIGHTS = [[0.063, 0.553, 0.035], [0.388, 0.40, 0.025], [0.413, 0.245, 0.025], [0.969, 0.553, 0.03]];

export function create(scene) {
  const { ctx } = scene;
  const embers = [];
  const snow = Array.from({ length: 260 }, () => ({
    u: Math.random(), v: Math.random(), r: rand(.6, 2.2), s: rand(.0006, .0018), w: rand(0, 6),
  }));
  let boost = 0;

  function draw(t) {
    const k = scene.k;
    const [fu, fv] = FIRE;
    const b = boost; boost = Math.max(0, boost - 1);

    // 1) 불꽃 일렁임: 아래는 고정, 위로 갈수록 크게 흔들림
    scene.wobble(0.515, 0.36, 0.64, 0.615, 2, (v, p) =>
      Math.sin(v * 140 - t * 0.22) * 3.2 * Math.sin(p * Math.PI) * (1 - p * 0.6) * k);

    ctx.globalCompositeOperation = 'lighter';
    // 2) 방 전체 불빛 깜빡임
    const flick = 0.5 + 0.25 * Math.sin(t * 0.13) + 0.15 * Math.sin(t * 0.31 + 1) + Math.random() * 0.12 + b / 200;
    const [gx, gy] = scene.P(fu, fv - 0.08);
    scene.glow(gx, gy, scene.fit.w * 0.45, '255,120,35', 0.16 * flick);

    // 3) 랜턴·촛불 깜빡임
    for (const [u, v, r] of LIGHTS) {
      const [lx, ly] = scene.P(u, v);
      scene.glow(lx, ly, r * scene.fit.w, '255,180,80', 0.22 + Math.random() * 0.12 + Math.sin(t * 0.2 + u * 50) * 0.06);
    }

    // 4) 불씨: 장작을 넣으면(boost) 더 많이, 더 빠르게
    const count = (Math.random() < 0.5 ? 1 : 0) + (b > 0 ? 3 : 0);
    for (let i = 0; i < count; i++) {
      embers.push({ u: fu + rand(-0.035, 0.035), v: fv - 0.03, vu: rand(-0.0006, 0.0006),
        vv: -rand(0.002, 0.0045) * (1 + b / 60), l: 1, r: rand(1, 2.4) });
    }
    for (let i = embers.length - 1; i >= 0; i--) {
      const e = embers[i];
      e.u += e.vu + Math.sin(t * 0.05 + i) * 0.0004; e.v += e.vv; e.l -= 0.007;
      if (e.l <= 0) { embers.splice(i, 1); continue; }
      const [ex, ey] = scene.P(e.u, e.v);
      ctx.fillStyle = `rgba(255,${140 + e.l * 90},60,${e.l})`;
      ctx.beginPath(); ctx.arc(ex, ey, e.r * k, 0, 7); ctx.fill();
    }
    ctx.globalCompositeOperation = 'source-over';

    // 5) 창밖 눈: 창문 영역 안에서만 그린다
    ctx.fillStyle = 'rgba(240,245,255,.85)';
    for (const [u0, v0, u1, v1] of WINDOWS) {
      const [x0, y0] = scene.P(u0, v0), [x1, y1] = scene.P(u1, v1);
      ctx.save(); ctx.beginPath(); ctx.rect(x0, y0, x1 - x0, y1 - y0); ctx.clip();
      for (const s of snow) {
        const [sx, sy] = scene.P(
          u0 + s.u * (u1 - u0) + Math.sin(t * 0.02 + s.w) * 0.004,
          v0 + ((s.v + t * s.s) % 1) * (v1 - v0));
        ctx.globalAlpha = 0.5 + s.r / 5;
        ctx.beginPath(); ctx.arc(sx, sy, s.r * k, 0, 7); ctx.fill();
      }
      ctx.restore();
    }
    ctx.globalAlpha = 1;
  }

  // 클릭하면 장작 넣기
  function click() { boost = 90; }

  return { draw, click };
}
