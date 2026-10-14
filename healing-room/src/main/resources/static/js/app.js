// 서버(/api/rooms)에서 방 목록을 받아 탭을 만들고, 선택한 방의 효과를 실행한다.
import { Scene } from './scene.js';
import { joinRoom } from './presence.js';

const scene = new Scene(document.getElementById('scene'));
const $ = id => document.getElementById(id);
const cache = {};          // 방 id → { img, effect }
let current = null;
let t = 0;

async function loadRoom(room) {
  if (cache[room.id]) return cache[room.id];
  const [img, mod] = await Promise.all([
    new Promise((resolve, reject) => {
      const i = new Image();
      i.onload = () => resolve(i); i.onerror = reject;
      i.src = room.background;
    }),
    import(`./effects/${room.effect}.js`),
  ]);
  return (cache[room.id] = { img, effect: mod.create(scene) });
}

async function enter(room) {
  const loaded = await loadRoom(room);
  scene.setImage(loaded.img);
  current = loaded;
  $('title').textContent = room.title;
  $('subtitle').textContent = room.subtitle;
  $('hint').textContent = room.hint;
  $('presence').hidden = true;
  joinRoom(room.id, count => {
    $('presence-text').textContent = count <= 1 ? '지금은 혼자 쉬는 중' : `${count}명이 함께 쉬는 중`;
    $('presence').hidden = false;
  });
  document.querySelectorAll('[data-room]').forEach(b => b.classList.toggle('on', b.dataset.room === room.id));
  history.replaceState(null, '', `#${room.id}`);
}

async function main() {
  const rooms = await fetch('/api/rooms').then(r => r.json());
  const tabs = $('tabs');
  for (const room of rooms) {
    const b = document.createElement('button');
    b.textContent = room.name; b.dataset.room = room.id;
    b.onclick = () => enter(room);
    tabs.insertBefore(b, $('fx'));
  }
  $('fx').onclick = e => {
    scene.fxOn = !scene.fxOn;
    e.target.textContent = scene.fxOn ? '효과 끄기' : '효과 켜기';
  };
  scene.canvas.onclick = e => current?.effect.click(...scene.toUV(e.clientX, e.clientY));

  // 주소 끝의 #cabin, #sea 로 방을 바로 열 수 있다
  const fromHash = () => rooms.find(r => r.id === location.hash.slice(1));
  addEventListener('hashchange', () => fromHash() && enter(fromHash()));
  await enter(fromHash() ?? rooms[0]);

  (function loop() {
    t++;
    if (current) {
      scene.drawBackground();
      if (scene.fxOn) current.effect.draw(t);
    }
    requestAnimationFrame(loop);
  })();
}

main().catch(err => {
  $('title').textContent = '방을 불러오지 못했어요';
  $('subtitle').textContent = err.message;
});
