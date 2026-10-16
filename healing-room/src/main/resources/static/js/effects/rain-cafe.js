// 비 오는 창가 카페: 유리창에 맺혔다 흘러내리는 빗방울, 커피 김, 창밖 불빛 번짐. 창을 클릭하면 빗방울이 흐른다
import { wisps, sprite } from './parts.js';
import { rand } from '../scene.js';

const WINDOW = [0.24, 0, 0.96, 0.64];
const CUP = [0.41, 0.64];
const MAX_DROPS = 220;

export function create(scene) {
  const { ctx } = scene;
  const steam = wisps(scene, { u: CUP[0], v: CUP[1], spread: 0.012, size: 46, rise: 0.2, count: 16, alpha: 0.13 });
  const bokeh = sprite('255,200,140');
  const lights = Array.from({ length: 40 }, () => ({ u: rand(0.25, 0.95), v: rand(0.35, 0.5), p: rand(0, 7), s: rand(0.01, 0.03), size: rand(10, 26) }));
  const [u0, v0, u1, v1] = WINDOW;
  const newDrop = () => ({ u: rand(u0, u1), v: rand(v0, v1), r: rand(1.2, 3.2), speed: 0, wait: rand(0, 600) });
  const drops = Array.from({ length: 140 }, newDrop);

  function draw(t) {
    const k = scene.k;
    ctx.globalCompositeOperation = 'lighter';
    // 1) 창밖 불빛이 흐리게 번졌다 잦아든다
    for (const l of lights) {
      const [x, y] = scene.P(l.u, l.v);
      ctx.globalAlpha = 0.08 + Math.max(0, Math.sin(t * l.s + l.p)) * 0.12;
      const s = l.size * k;
      ctx.drawImage(bokeh, x - s / 2, y - s / 2, s, s);
    }
    ctx.globalAlpha = 1;
    // 2) 커피 김
    steam(t);
    ctx.globalCompositeOperation = 'source-over';

    // 3) 유리창 빗방울: 맺혀 있다가 가끔 주르륵 흘러내린다 (창문 안에서만)
    const [x0, y0] = scene.P(u0, v0), [x1, y1] = scene.P(u1, v1);
    ctx.save(); ctx.beginPath(); ctx.rect(x0, y0, x1 - x0, y1 - y0); ctx.clip();
    for (let i = 0; i < drops.length; i++) {
      const d = drops[i];
      if (d.wait > 0) d.wait--;
      else if (d.speed === 0 && Math.random() < 0.004 * d.r) d.speed = rand(0.002, 0.005);
      if (d.speed) {
        d.v += d.speed; d.u += Math.sin(t * 0.1 + i) * 0.0003;
        if (d.v > v1) drops[i] = Object.assign(newDrop(), { v: rand(v0, v1 * 0.3), wait: 0 });
      }
      const [x, y] = scene.P(d.u, d.v);
      const r = d.r * k;
      if (d.speed) { // 흘러내린 자국
        ctx.strokeStyle = 'rgba(220,230,240,.12)'; ctx.lineWidth = r * 0.8;
        ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x, y - r * 10); ctx.stroke();
      }
      ctx.fillStyle = 'rgba(15,20,30,.25)';
      ctx.beginPath(); ctx.arc(x, y + r * 0.2, r, 0, 7); ctx.fill();
      ctx.fillStyle = 'rgba(235,240,250,.45)';
      ctx.beginPath(); ctx.arc(x - r * 0.25, y - r * 0.25, r * 0.45, 0, 7); ctx.fill();
    }
    ctx.restore();
  }

  function click(u, v) {
    if (u > u0 && u < u1 && v > v0 && v < v1) drops.push({ u, v, r: rand(2.5, 4), speed: 0.004, wait: 0 });
    if (drops.length > MAX_DROPS) drops.splice(0, drops.length - MAX_DROPS);
  }

  return { draw, click };
}
