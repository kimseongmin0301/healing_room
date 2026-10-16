// 진입점: 주소의 #방id 에 따라 로비 또는 방을 보여준다.
//  - 로비: 방 카드 목록 (lobby.js)
//  - 방: 배경 + 효과(scene.js, effects/*), 함께 듣는 음악(music.js), 환경음(audio/mixer.js), 채팅(chat.js), 실시간 연결(room-socket.js)
import { Scene } from './scene.js';
import { api, store } from './api.js';
import { connectRoom } from './room-socket.js';
import { showLobby, hideLobby } from './lobby.js';
import { initMusic, enterMusic, leaveMusic, applyState, setMusicMuted, onMusicAudible } from './music.js';
import { enterMixer, leaveMixer, setAmbientEnabled } from './audio/mixer.js';
import { initChat, enterChat, leaveChat, receiveChat, clearChat, getNickname } from './chat.js';
import { renderPeople, resetPeople } from './people.js';
import { initSupport } from './support.js';
import { initFeedback } from './feedback.js';

const $ = id => document.getElementById(id);
const scene = new Scene($('scene'));

let current = null;   // { id, effect, media }
let socket = null;
// 소리 켜짐/꺼짐은 브라우저에 기억하고, 처음이면 둘 다 켠다
let musicOn = store.get('musicOn', true);      // 음악(YouTube) 소리
let ambientOn = store.get('ambientOn', true);  // 환경음
// 브라우저는 사용자가 한 번 누르기 전에는 소리를 막는다. 새로고침·주소로 들어오면 음소거로 틀어 두었다가
// 페이지 어디든 처음 누를 때(unlockSound) 켜 둔 소리를 낸다
let soundUnlocked = navigator.userActivation?.hasBeenActive ?? false;
let unlockTarget = null; // 소리를 처음 풀어 준 누름이 어느 요소였는지
let ambientPaused = false; // 음악 때문에 환경음을 자동으로 끈 상태 (음악이 멈추면 다시 켠다)
let lobbyMessage = '';   // 로비로 돌아갈 때 보여줄 안내 (닫힌 방 등)
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

// 테마에 배경 영상이 있으면 영상, 없거나 못 불러오면 이미지
const loadBackground = theme => theme.backgroundVideo
  ? loadVideo(theme.backgroundVideo).catch(() => loadImage(theme.background))
  : loadImage(theme.background);

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
    lobbyMessage = '닫혔거나 없는 방이에요. 다른 방에 들어가거나 새로 만들어 보세요.';
    location.hash = ''; // 없거나 닫힌 방이면 로비로
    return;
  }
  const { room, ambients } = detail;
  const { theme } = room;
  const [media, effectModule] = await Promise.all([loadBackground(theme), import(`./effects/${theme.effect}.js`)]);
  if (seq !== enterSeq) return;

  leaveRoom();
  hideLobby();
  $('lobby').hidden = true;
  $('room').hidden = false;
  document.body.classList.add('in-room');

  scene.setImage(media);
  current = { id: room.id, isDefault: room.isDefault, effect: effectModule.create(scene), media };
  $('title').textContent = room.name;
  $('subtitle').textContent = room.name === theme.title ? theme.subtitle : `${theme.title} · ${theme.subtitle}`;
  $('hint').textContent = theme.hint;
  $('presence-text').textContent = '연결 중…';
  $('host-chip').hidden = true;
  $('reset-room').hidden = true;
  document.title = `${room.name} · 힐링룸`;

  enterMixer(room.id, ambients);
  enterMusic(room.id);
  resetPeople();
  socket = connectRoom(room.id, {
    nickname: getNickname,
    onChat: receiveChat,
    onChatClear: clearChat,
    onPresence: renderPeople,
    onState: state => {
      applyState(state);
      setPresence(state.listeners);
      $('host-chip').hidden = !state.hostIsMe;
      $('reset-room').hidden = !(current?.isDefault && state.hostIsMe); // 기본 방의 방장만
    },
    // 소켓이 연결된 뒤(=방에 들어온 뒤)에 지난 대화를 불러온다
    onStatus: status => {
      if (status === 'open') enterChat(room.id);
      if (status === 'reconnecting') $('presence-text').textContent = '다시 연결하는 중…';
    },
  });
}

function leaveRoom() {
  socket?.close();
  socket = null;
  leaveMusic();
  leaveMixer();
  leaveChat();
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
  document.title = '힐링룸 · 불멍 바다멍 하며 함께 음악 듣는 방';
  showLobby(id => {
    // 카드를 누른 순간(사용자 클릭)이라 브라우저가 소리를 허용한다. 마지막으로 고른 켜짐/꺼짐을 따른다
    setMusic(store.get('musicOn', true));
    setAmbient(store.get('ambientOn', true));
    location.hash = id;
  }, lobbyMessage);
  lobbyMessage = '';
}

function route() {
  const id = decodeURIComponent(location.hash.slice(1));
  if (id) enterRoom(id); else showLobbyView();
}

// ---------------------------------------------------------------- 소리: 음악과 환경음은 따로 켜고 끈다

function renderSoundButtons() {
  const locked = !soundUnlocked && musicOn;
  $('music-sound').textContent = locked ? '🔈 소리 켜기' : musicOn ? '음악 끄기' : '음악 켜기';
  $('music-sound').title = locked ? '브라우저 정책상 화면을 한 번 눌러야 소리가 나요' : '';
  $('music-sound').classList.toggle('on', locked || !musicOn); // 소리가 안 날 때 눈에 띄게
  $('ambient-sound').textContent = ambientOn ? '환경음 끄기' : ambientPaused ? '환경음 쉬는 중' : '환경음 켜기';
  $('ambient-sound').classList.toggle('on', !ambientOn && !ambientPaused);
  $('mixer-note').hidden = !ambientPaused;
}

function setMusic(on) {
  musicOn = on;
  setMusicMuted(!(on && soundUnlocked));
  renderSoundButtons();
}

function setAmbient(on) {
  ambientOn = on;
  ambientPaused = false;
  setAmbientEnabled(on && soundUnlocked);
  renderSoundButtons();
}

// 페이지에서 처음 누르거나 키를 칠 때: 켜 둔 음악·환경음 소리를 낸다
function unlockSound(e) {
  if (soundUnlocked) return;
  soundUnlocked = true;
  unlockTarget = e.target; // 소리 버튼을 눌러서 풀렸다면 그 클릭은 "켜기"로만 쓴다 (바로 꺼지지 않게)
  setMusic(musicOn);
  setAmbient(ambientOn);
}
for (const type of ['pointerdown', 'keydown', 'touchstart']) {
  addEventListener(type, unlockSound, { capture: true, passive: true });
}

// 음악이 들리기 시작하면 환경음을 잠시 끄고, 음악이 멈추면 (자동으로 껐던 경우에만) 다시 켠다
onMusicAudible(audible => {
  if (audible && ambientOn) {
    ambientOn = false;
    ambientPaused = true;
    setAmbientEnabled(false);
  } else if (!audible && ambientPaused) {
    ambientOn = true;
    ambientPaused = false;
    setAmbientEnabled(true);
  }
  renderSoundButtons();
});

// ---------------------------------------------------------------- 시작

$('back').onclick = () => { location.hash = ''; };
$('reset-room').onclick = async () => {
  if (!current || !confirm('이 방의 지난 신청곡, 투표, 채팅을 모두 지울까요?\n지금 나오는 곡도 멈추고 기본 음악부터 다시 시작해요.')) return;
  try {
    await api('POST', `/api/rooms/${encodeURIComponent(current.id)}/reset`);
  } catch (e) {
    alert(e.message);
  }
};
addEventListener('nickname-change', e => socket?.sendNickname(e.detail));
// 소리를 풀어 준 바로 그 클릭이면 true (그 클릭으로 켜고 끄지 않는다)
function consumeUnlockClick(button) {
  if (unlockTarget && button.contains(unlockTarget)) { unlockTarget = null; return true; }
  unlockTarget = null;
  return false;
}
$('music-sound').onclick = () => {
  if (consumeUnlockClick($('music-sound'))) return;
  setMusic(!musicOn);
  store.set('musicOn', musicOn);
};
$('ambient-sound').onclick = () => {
  if (consumeUnlockClick($('ambient-sound'))) return;
  // 음악 때문에 쉬는 중에 누르면 "다시 켜기"로 본다 (직접 켠 것은 음악이 나와도 유지)
  setAmbient(!ambientOn);
  store.set('ambientOn', ambientOn);
};
$('fx').onclick = e => {
  scene.fxOn = !scene.fxOn;
  e.target.textContent = scene.fxOn ? '효과 끄기' : '효과 켜기';
};
scene.canvas.onclick = e => current?.effect.click(...scene.toUV(e.clientX, e.clientY));
addEventListener('hashchange', route);

initMusic();
initChat();
initSupport();
initFeedback();
setMusic(musicOn);   // 아직 누르지 않았으면 음소거로 시작하고, 누르는 순간 소리를 낸다
setAmbient(ambientOn);
route();

(function loop() {
  t++;
  if (current) {
    scene.drawBackground();
    if (scene.fxOn) current.effect.draw(t);
  }
  requestAnimationFrame(loop);
})();
