// 노을 해변: 파도 일렁임, 해 반사 반짝임, 해 빛 맥동, 날아가는 갈매기. 하늘을 클릭하면 갈매기가 날아간다
import { glints } from './parts.js';
import { rand } from '../scene.js';

const SUN = [0.501, 0.397];
const HORIZON = 0.496;

export function create(scene) {
  const { ctx } = scene;
  const shine = glints(scene, { u: SUN[0], from: HORIZON + 0.005, to: 1, narrow: 0.01, wide: 0.06, rgb: '255,220,160', chance: 0.9 });
  const birds = [];
  const addBird = (u, v) => birds.push({ u, v, du: rand(0.0006, 0.0012) * (u > 0.5 ? -1 : 1), p: rand(0, 7), size: rand(0.7, 1.3) });

  function draw(t) {
    const k = scene.k;
    // 1) 바다와 밀려오는 물: 가까울수록 크게 흔들림
    scene.wobble(0, HORIZON + 0.002, 1, 1, 2, (v, p) =>
      (Math.sin(v * 220 - t * 0.04) * 0.6 + Math.sin(v * 70 + t * 0.025) * 0.4) * (0.4 + p * 4) * k);

    ctx.globalCompositeOperation = 'lighter';
    shine();
    const [sx, sy] = scene.P(...SUN);
    scene.glow(sx, sy, 180 * k, '255,190,120', 0.12 + Math.sin(t * 0.02) * 0.04);
    ctx.globalCompositeOperation = 'source-over';

    // 2) 갈매기 실루엣: 날개를 천천히 퍼덕이며 지나간다
    if (Math.random() < 0.003 && birds.length < 6) addBird(Math.random() < 0.5 ? -0.02 : 1.02, rand(0.12, 0.38));
    ctx.strokeStyle = 'rgba(40,20,20,.75)'; ctx.lineWidth = 1.6 * k; ctx.lineCap = 'round';
    for (let i = birds.length - 1; i >= 0; i--) {
      const b = birds[i];
      if ((b.u < -0.03 && b.du < 0) || (b.u > 1.03 && b.du > 0)) { birds.splice(i, 1); continue; }
      b.u += b.du; b.v += Math.sin(t * 0.03 + b.p) * 0.0002;
      const [x, y] = scene.P(b.u, b.v);
      const w = 9 * k * b.size, flap = Math.sin(t * 0.25 + b.p) * 4 * k * b.size;
      ctx.beginPath();
      ctx.moveTo(x - w, y - flap); ctx.quadraticCurveTo(x - w / 2, y - w / 2, x, y);
      ctx.quadraticCurveTo(x + w / 2, y - w / 2, x + w, y - flap);
      ctx.stroke();
    }
  }

  return { draw, click: (u, v) => { if (v < HORIZON) addBird(u, v); } };
}
