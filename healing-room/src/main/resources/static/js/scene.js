// 캔버스와 배경(이미지 또는 루프 영상)을 관리하고, 효과 모듈이 쓰는 공통 도구를 제공한다.
export const rand = (a, b) => a + Math.random() * (b - a);

export class Scene {
  constructor(canvas) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.dpr = Math.min(devicePixelRatio || 1, 2);
    this.fit = { x: 0, y: 0, w: 1, h: 1 };
    this.img = null;       // 배경 요소: <img> 또는 <video>
    this.fxOn = true;
    addEventListener('resize', () => this.resize());
    this.resize();
  }

  get W() { return innerWidth; }
  get H() { return innerHeight; }
  // 배경 원본 크기 (이미지와 영상 모두 지원)
  get mediaW() { return this.img?.naturalWidth || this.img?.videoWidth || 1672; }
  get mediaH() { return this.img?.naturalHeight || this.img?.videoHeight || 941; }
  get isVideo() { return this.img instanceof HTMLVideoElement; }
  // 원본 배경 너비 기준 배율. 효과 크기를 화면에 맞출 때 사용
  get k() { return this.fit.w / this.mediaW; }

  resize() {
    this.canvas.width = this.W * this.dpr;
    this.canvas.height = this.H * this.dpr;
    this.ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
    if (this.img) this.computeFit();
  }

  setImage(img) {
    this.img = img;
    this.computeFit();
  }

  // 이미지를 화면에 꽉 채우기 (CSS object-fit: cover 와 같은 계산)
  computeFit() {
    const iw = this.mediaW, ih = this.mediaH;
    const s = Math.max(this.W / iw, this.H / ih);
    this.fit.w = iw * s; this.fit.h = ih * s;
    this.fit.x = (this.W - this.fit.w) / 2; this.fit.y = (this.H - this.fit.h) / 2;
  }

  // 이미지 기준 좌표(0~1) → 화면 좌표
  P(u, v) { return [this.fit.x + u * this.fit.w, this.fit.y + v * this.fit.h]; }

  // 화면 좌표 → 이미지 기준 좌표(0~1)
  toUV(x, y) { return [(x - this.fit.x) / this.fit.w, (y - this.fit.y) / this.fit.h]; }

  drawBackground() {
    const { ctx, fit } = this;
    ctx.clearRect(0, 0, this.W, this.H);
    ctx.drawImage(this.img, fit.x, fit.y, fit.w, fit.h);
  }

  // 이미지 일부 영역을 가로줄 단위로 좌우로 흔들어 다시 그린다 (불꽃 일렁임, 물결)
  // offsetFn(v, progress) → 해당 줄의 x 이동량(px). progress 는 영역 위(0)~아래(1)
  wobble(u0, v0, u1, v1, step, offsetFn) {
    if (this.isVideo) return; // 영상은 이미 실제로 움직이므로 흔들지 않는다
    const { ctx, img, fit } = this;
    const iw = this.mediaW, ih = this.mediaH;
    const [x0, y0] = this.P(u0, v0), [x1, y1] = this.P(u1, v1);
    const sx = u0 * iw, sw = (u1 - u0) * iw;
    for (let y = y0; y < y1; y += step) {
      const v = (y - fit.y) / fit.h;
      const dx = offsetFn(v, (y - y0) / (y1 - y0));
      ctx.drawImage(img, sx, v * ih, sw, step / fit.h * ih, x0 + dx, y, x1 - x0, step + 0.5);
    }
  }

  // 은은한 빛 번짐 (가산 합성 상태에서 호출)
  glow(x, y, r, rgb, alpha) {
    const g = this.ctx.createRadialGradient(x, y, 0, x, y, r);
    g.addColorStop(0, `rgba(${rgb},${alpha})`);
    g.addColorStop(1, `rgba(${rgb},0)`);
    this.ctx.fillStyle = g;
    this.ctx.fillRect(x - r, y - r, r * 2, r * 2);
  }
}
