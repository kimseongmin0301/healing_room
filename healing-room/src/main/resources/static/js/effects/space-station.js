// 우주정거장 전망대: 별 반짝임, 별똥별, 행성 대기 빛 맥동, 밤 지역 도시 불빛, 실내 조명. 하늘을 클릭하면 별똥별
import { stars, meteors, flicker, sprite } from './parts.js';

// 행성 가장자리(대기) 곡선: 창문 왼쪽 끝 · 가운데 · 오른쪽 끝 [u, v] 를 지나는 2차 곡선
const LIMB = [[0.12, 0.535], [0.5, 0.43], [0.95, 0.535]];
const limbPoint = p => {
  const [[u0, a], [, b], [u1, c]] = LIMB;
  const control = 2 * b - (a + c) / 2;
  return [u0 + (u1 - u0) * p, (1 - p) ** 2 * a + 2 * (1 - p) * p * control + p * p * c];
};

export function create(scene) {
  const { ctx } = scene;
  const sky = stars(scene, [0.12, 0.06, 0.92, 0.4], 160);
  const shooting = meteors(scene, [0.15, 0.06, 0.9, 0.35], 0.005);
  const lamps = flicker(scene, [[0.154, 0.73, 0.03], [0.893, 0.65, 0.03], [0.48, 0.77, 0.045]], '255,190,110', 0.15);
  const dot = sprite('255,210,150');
  const city = Array.from({ length: 70 }, () => ({
    u: 0.3 + Math.random() * 0.4, v: 0.55 + Math.random() * 0.13, p: Math.random() * 7, s: 0.02 + Math.random() * 0.05,
  }));

  function draw(t) {
    const k = scene.k;
    ctx.globalCompositeOperation = 'lighter';
    sky(t);
    shooting();

    // 행성 대기: 가장자리를 따라 푸른 빛이 천천히 숨 쉬듯
    const breathe = 0.05 + Math.sin(t * 0.015) * 0.02;
    for (let p = 0; p <= 1; p += 0.04) {
      const [x, y] = scene.P(...limbPoint(p));
      scene.glow(x, y, 55 * k, '110,170,255', breathe);
    }
    // 밤 지역 도시 불빛 반짝임
    for (const c of city) {
      const [x, y] = scene.P(c.u, c.v);
      ctx.globalAlpha = 0.15 + Math.max(0, Math.sin(t * c.s + c.p)) ** 4 * 0.6;
      ctx.drawImage(dot, x - 4 * k, y - 4 * k, 8 * k, 8 * k);
    }
    ctx.globalAlpha = 1;
    lamps(t);
    ctx.globalCompositeOperation = 'source-over';
  }

  return { draw, click: (u, v) => { if (v < 0.42) shooting.add(u, v); } };
}
