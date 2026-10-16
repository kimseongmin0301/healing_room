// 깊은 우주: 별 반짝임, 은하 중심 빛 맥동, 중심을 천천히 도는 별가루, 별똥별. 클릭하면 별똥별
import { stars, meteors, sprite } from './parts.js';
import { rand } from '../scene.js';

const CORE = [0.496, 0.425];

export function create(scene) {
  const { ctx } = scene;
  const sky = stars(scene, [0, 0, 1, 1], 260);
  const shooting = meteors(scene, [0, 0, 1, 0.6], 0.004);
  const dust = sprite('190,170,255');
  const motes = Array.from({ length: 90 }, () => ({
    a: rand(0, 7), r: rand(0.03, 0.45), s: rand(0.00004, 0.00015), size: rand(4, 12), p: rand(0, 7),
  }));

  function draw(t) {
    const k = scene.k;
    ctx.globalCompositeOperation = 'lighter';
    sky(t);
    const [cx, cy] = scene.P(...CORE);
    scene.glow(cx, cy, 260 * k, '255,200,150', 0.1 + Math.sin(t * 0.012) * 0.04);
    scene.glow(cx, cy, 70 * k, '255,240,220', 0.18 + Math.sin(t * 0.03) * 0.06);
    for (const m of motes) {
      m.a += m.s * (0.5 / m.r); // 중심에 가까울수록 빨리 돈다
      const [x, y] = scene.P(CORE[0] + Math.cos(m.a) * m.r, CORE[1] + Math.sin(m.a) * m.r * 0.9);
      ctx.globalAlpha = 0.15 + Math.max(0, Math.sin(t * 0.02 + m.p)) * 0.35;
      const s = m.size * k;
      ctx.drawImage(dust, x - s / 2, y - s / 2, s, s);
    }
    ctx.globalAlpha = 1;
    shooting();
    ctx.globalCompositeOperation = 'source-over';
  }

  return { draw, click: (u, v) => shooting.add(u, v) };
}
