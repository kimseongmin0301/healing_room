// 라운지 클럽: 박자에 맞춰 숨 쉬는 조명(분당 100박, 환경음 beat 와 같은 빠르기), 천천히 바뀌는 색 조명,
// 천장 링에서 흔들리며 내려오는 빛줄기, 공기 중 반짝이, 바닥 반사 일렁임. 클릭하면 플래시
import { sprite } from './parts.js';

const RING = [0.5, 0.05];
const PILLARS = [0.097, 0.9];
const BEAT_MS = 600; // 분당 100박
// 반짝이 위치 (매번 같은 자리에서 반짝이도록 미리 정해 둔다)
const SPARKS = Array.from({ length: 24 }, () => [Math.random(), 0.15 + Math.random() * 0.55, Math.random() * 7]);

export function create(scene) {
  const { ctx } = scene;
  const dot = sprite('255,255,255');
  let flash = 0;

  function draw(t) {
    const k = scene.k;
    const now = performance.now();
    const pulse = (1 - (now % BEAT_MS) / BEAT_MS) ** 3; // 박자마다 1 → 0
    const hue = (now / 80) % 360;

    // 1) 반짝이는 바닥 반사 일렁임
    scene.wobble(0, 0.6, 1, 1, 2, (v, p) => Math.sin(v * 240 - t * 0.06) * (0.4 + p * 2) * k);

    ctx.globalCompositeOperation = 'lighter';
    // 2) 천장 링과 기둥 네온이 박자에 맞춰 밝아진다
    const [rx, ry] = scene.P(...RING);
    scene.glow(rx, ry, 220 * k, '200,120,255', 0.08 + pulse * 0.12);
    for (const u of PILLARS) {
      for (let v = 0.04; v < 0.42; v += 0.06) {
        const [x, y] = scene.P(u, v);
        scene.glow(x, y, 40 * k, '120,180,255', 0.05 + pulse * 0.08);
      }
    }
    // 3) 빛줄기: 링에서 아래로 흔들리며 내려온다
    for (let i = 0; i < 4; i++) {
      const angle = Math.sin(t * 0.01 + i * 1.7) * 0.5;
      const [x0, y0] = scene.P(RING[0] + (i - 1.5) * 0.03, RING[1] + 0.02);
      const len = scene.fit.h * 0.6;
      const x1 = x0 + Math.sin(angle) * len, y1 = y0 + Math.cos(angle) * len;
      const color = (hue + i * 60) % 360;
      const g = ctx.createLinearGradient(x0, y0, x1, y1);
      g.addColorStop(0, `hsla(${color},90%,65%,${0.12 + pulse * 0.1})`);
      g.addColorStop(1, `hsla(${color},90%,65%,0)`);
      ctx.strokeStyle = g; ctx.lineWidth = 3 * k;
      ctx.beginPath(); ctx.moveTo(x0, y0); ctx.lineTo(x1, y1); ctx.stroke();
    }
    // 4) 방 전체 색 조명이 천천히 바뀐다
    const [cx, cy] = scene.P(0.5, 0.5);
    const wash = ctx.createRadialGradient(cx, cy, 0, cx, cy, scene.fit.w * 0.6);
    wash.addColorStop(0, `hsla(${hue},80%,55%,${0.06 + pulse * 0.05 + flash * 0.3})`);
    wash.addColorStop(1, `hsla(${hue},80%,55%,0)`);
    ctx.fillStyle = wash; ctx.fillRect(0, 0, scene.W, scene.H);
    // 5) 공기 중 반짝이 (미러볼 조각)
    for (const [u, v, p] of SPARKS) {
      const a = Math.max(0, Math.sin(t * 0.05 + p)) ** 6 * 0.7;
      if (a < 0.02) continue;
      const [x, y] = scene.P((u + t * 0.0002) % 1, v);
      ctx.globalAlpha = a;
      ctx.drawImage(dot, x - 5 * k, y - 5 * k, 10 * k, 10 * k);
    }
    ctx.globalAlpha = 1;
    ctx.globalCompositeOperation = 'source-over';
    flash = Math.max(0, flash - 0.04);
  }

  return { draw, click: () => { flash = 1; } };
}
