// 숲 · 반딧불 시냇가: 시냇물 일렁임과 반짝임, 반딧불, 낮게 흐르는 안개, 달빛 맥동. 클릭하면 반딧불이 날아오른다
import { fireflies, glints, wisps } from './parts.js';

const MOON = [0.483, 0.128];

export function create(scene) {
  const { ctx } = scene;
  const bugs = fireflies(scene, [0.03, 0.3, 0.97, 0.8], 26);
  const water = glints(scene, { u: 0.52, from: 0.55, to: 0.99, narrow: 0.04, wide: 0.12, rgb: '220,235,255', chance: 0.5 });
  const mist = wisps(scene, { u: 0.5, v: 0.5, spread: 0.45, size: 260, drift: 0.04, count: 14, alpha: 0.07, rgb: '200,215,235' });

  function draw(t) {
    const k = scene.k;
    // 1) 시냇물과 작은 폭포: 줄마다 좌우로 흔들어 흐르는 느낌
    scene.wobble(0.3, 0.55, 0.75, 1, 2, (v, p) => Math.sin(v * 300 - t * 0.12) * (0.5 + p * 2.2) * k);
    scene.wobble(0.59, 0.3, 0.66, 0.48, 2, v => Math.sin(v * 420 - t * 0.3) * 0.9 * k);

    ctx.globalCompositeOperation = 'lighter';
    water();
    mist(t);
    const [mx, my] = scene.P(...MOON);
    scene.glow(mx, my, 120 * k, '220,230,255', 0.07 + Math.sin(t * 0.02) * 0.03);
    bugs(t);
    ctx.globalCompositeOperation = 'source-over';
  }

  return { draw, click: (u, v) => bugs.burst(u, v) };
}
