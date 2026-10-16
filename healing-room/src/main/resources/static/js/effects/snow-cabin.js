// 눈 내리는 산장: 내리는 눈, 오로라 일렁임, 창문·랜턴 불빛, 얼어붙은 호수 반짝임. 클릭하면 바람이 분다
import { snow, flicker, glints, sprite } from './parts.js';

const AURORA = [[0.48, 0.12], [0.56, 0.08], [0.63, 0.14], [0.7, 0.1], [0.76, 0.16]];

export function create(scene) {
  const { ctx } = scene;
  const flakes = snow(scene, 320);
  const windows = flicker(scene, [[0.05, 0.22, 0.06], [0.12, 0.25, 0.04]], '255,170,80', 0.16);
  const lanterns = flicker(scene, [[0.25, 0.565, 0.035], [0.012, 0.6, 0.03], [0.458, 0.80, 0.04]]);
  const lake = glints(scene, { u: 0.66, from: 0.63, to: 0.73, narrow: 0.15, wide: 0.05, rgb: '200,230,255', chance: 0.4 });
  const veil = sprite('80,255,180');

  function draw(t) {
    const k = scene.k;
    ctx.globalCompositeOperation = 'lighter';
    // 1) 오로라: 커다란 초록 빛이 천천히 일렁인다
    for (let i = 0; i < AURORA.length; i++) {
      const [u, v] = AURORA[i];
      const [x, y] = scene.P(u + Math.sin(t * 0.006 + i) * 0.02, v);
      const w = 260 * k, h = 420 * k;
      ctx.globalAlpha = 0.05 + Math.max(0, Math.sin(t * 0.01 + i * 1.3)) * 0.12;
      ctx.drawImage(veil, x - w / 2, y - h / 3, w, h);
    }
    ctx.globalAlpha = 1;
    // 2) 호수 반짝임, 창문·랜턴 불빛
    lake();
    windows(t);
    lanterns(t);
    ctx.globalCompositeOperation = 'source-over';
    // 3) 눈
    flakes(t);
  }

  return { draw, click: () => flakes.gust() };
}
