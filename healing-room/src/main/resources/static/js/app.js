// 진입점: 주소의 #방id 에 따라 로비 또는 방을 보여준다.
//  - 로비: 방 카드 목록 (lobby.js)
//  - 방: 배경 + 효과(scene.js, effects/*), 함께 듣는 음악(music.js), 환경음(audio/mixer.js), 실시간 연결(room-socket.js)
import { Scene } from './scene.js';
import { api } from './api.js';
import { connectRoom } from './room-socket.js';
import { showLobby, hideLobby } from './lobby.js';
import { initMusic, enterMusic, leaveMusic, applyState, setMusicMuted } from './music.js';
import { enterMixer, leaveMixer, setAmbientEnabled } from './audio/mixer.js';

const $ = id => document.getElementById(id);
const scene = new Scene($('scene'));

let current = null;   // { id, effect, media }
let socket = null;
let soundOn = false;
let enterSeq = 0;     // 방을 빠르게 바꿀 때 늦게 끝난 로딩을 버리기 위한 번호
let t = 0;

// ---------------------------------------------------------------- 배경

function loadImage(src) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = reject;
    img.src = src;
  });
}

function loadVideo(src) {
  return new Promise((resolve, reject) => {
    const video = Object.assign(document.createElement('video'), { muted: true, loop: true, playsInline: true, src });
    video.addEventListener('loadeddata', () => { video.play().catch(() => {}); resolve(video); }, { once: true });
    video.addEventListener('error', reject, { once: true });
  });
}

// 배경 영상이 있으면 영상, 없거나 못 불러오면 이미지
const loadBackground = room => room.backgroundVideo
  ? loadVideo(room.backgroundVideo).catch(() => loadImage(room.background))
  : loadImage(room.background);

// ---------------------------------------------------------------- 화면 전환

function setPresence(count) {
  $('presence-text').textContent = count <= 1 ? '지금은 혼자 쉬는 중' : `${count}명이 함께 쉬는 중`;
}

async function enterRoom(id) {
  const seq = ++enterSeq;
  let detail;
  try {
    detail = await api('GET', `/api/rooms/${encodeURIComponent(id)}`);
  } catch {
    location.hash = ''; // 없는 방이면 로비로
    return;
  }
  const { room, ambients } = detail;
  const [media, effectModule] = await Promise.all([loadBackground(room), import(`./effects/${room.effect}.js`)]);
  if (seq !== enterSeq) return;

  leaveRoom();
  hideLobby();
  $('lobby').hidden = true;
  $('room').hidden = false;
  document.body.classList.add('in-room');

  scene.setImage(media);
  current = { id: room.id, effect: effectModule.create(scene), media };
  $('title').textContent = room.title;
  $('subtitle').textContent = room.subtitle;
  $('hint').textContent = room.hint;
  $('presence-text').textContent = '연결 중…';
  document.title = `${room.name} · 힐링룸`;

  enterMixer(room.id, ambients);
  enterMusic(room.id);
  socket = connectRoom(room.id, {
    onState: state => { applyState(state); setPresence(state.listeners); },
    onStatus: status => { if (status === 'reconnecting') $('presence-text').textContent = '다시 연결하는 중…'; },
  });
}

function leaveRoom() {
  socket?.close();
  socket = null;
  leaveMusic();
  leaveMixer();
  if (current?.media instanceof HTMLVideoElement) {
    current.media.pause();
    current.media.removeAttribute('src');
  }
  current = null;
}

function showLobbyView() {
  enterSeq++;
  leaveRoom();
  $('room').hidden = true;
  $('lobby').hidden = false;
  document.body.classList.remove('in-room');
  document.title = '힐링룸';
  showLobby(id => {
    setSound(true); // 카드를 누른 순간(사용자 클릭)이라 브라우저가 소리를 허용한다
    location.hash = id;
  });
}

function route() {
  const id = decodeURIComponent(location.hash.slice(1));
  if (id) enterRoom(id); else showLobbyView();
}

// ---------------------------------------------------------------- 소리

function setSound(on) {
  soundOn = on;
  $('sound').textContent = on ? '소리 끄기' : '소리 켜기';
  $('sound').classList.toggle('on', !on); // 꺼져 있을 때 눈에 띄게
  setAmbientEnabled(on);
  setMusicMuted(!on);
}

// ---------------------------------------------------------------- 시작

$('back').onclick = () => { location.hash = ''; };
$('sound').onclick = () => setSound(!soundOn);
$('fx').onclick = e => {
  scene.fxOn = !scene.fxOn;
  e.target.textContent = scene.fxOn ? '효과 끄기' : '효과 켜기';
};
scene.canvas.onclick = e => current?.effect.click(...scene.toUV(e.clientX, e.clientY));
addEventListener('hashchange', route);

initMusic();
setSound(false);
route();

(function loop() {
  t++;
  if (current) {
    scene.drawBackground();
    if (scene.fxOn) current.effect.draw(t);
  }
  requestAnimationFrame(loop);
})();
