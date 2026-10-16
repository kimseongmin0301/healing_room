// 비 오는 숲: 빗줄기, 시냇물·폭포 흐름, 바닥 빗방울, 처마 랜턴 깜빡임, 비구름 사이 빛. 하늘을 클릭하면 번개
import { rain, ripples, flicker, wisps } from './parts.js';

export function create(scene) {
  const { ctx } = scene;
  const drops = rain(scene, 260);
  const puddles = ripples(scene, [0.2, 0.8, 0.97, 0.99], 0.6);
  const lights = flicker(scene, [[0.854, 0.27, 0.035], [0.854, 0.27, 0.08]]);
  const fog = wisps(scene, { u: 0.55, v: 0.5, spread: 0.35, size: 280, drift: 0.05, count: 12, alpha: 0.06, rgb: '190,205,225' });
  let lightning = 0;

  function draw(t) {
    const k = scene.k;
    // 1) 시냇물이 흐르고, 젖은 바닥 반사가 살짝 일렁임
    scene.wobble(0.42, 0.55, 0.72, 0.8, 2, (v, p) => Math.sin(v * 300 - t * 0.12) * (0.5 + p * 1.5) * k);
    scene.wobble(0.52, 0.42, 0.6, 0.55, 2, v => Math.sin(v * 420 - t * 0.3) * 0.8 * k);
    scene.wobble(0.2, 0.8, 1, 1, 2, (v, p) => Math.sin(v * 260 - t * 0.05) * (0.3 + p * 1.2) * k);

    ctx.globalCompositeOperation = 'lighter';
    lights(t);
    fog(t);
    const [lx, ly] = scene.P(0.48, 0.2);
    scene.glow(lx, ly, 200 * k, '200,215,255', 0.06 + Math.sin(t * 0.015) * 0.025);
    puddles();
    ctx.globalCompositeOperation = 'source-over';
    drops();

    // 2) 번개: 아주 가끔 저절로 + 클릭하면
    if (Math.random() < 0.0007) lightning = 1;
    if (lightning > 0) {
      ctx.fillStyle = `rgba(210,220,255,${lightning * (Math.random() < 0.5 ? 0.35 : 0.15)})`;
      ctx.fillRect(0, 0, scene.W, scene.H);
      lightning = Math.max(0, lightning - 0.05);
    }
  }

  return { draw, click: (u, v) => { if (v > 0.8) puddles.add(u, v); else lightning = 1; } };
}
