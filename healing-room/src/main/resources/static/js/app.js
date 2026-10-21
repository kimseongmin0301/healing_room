// 진입점: 주소의 #방id 에 따라 로비 또는 방을 보여준다.
//  - 로비: 방 카드 목록 (lobby.js)
//  - 방: 배경 + 효과(scene.js, effects/*), 함께 듣는 음악(music.js), 환경음(audio/mixer.js), 채팅(chat.js), 실시간 연결(room-socket.js)
import { Scene } from './scene.js';
import { api, store, syncVisitor, whenOutdated } from './api.js';
import { connectRoom } from './room-socket.js';
import { showLobby, hideLobby, openRoomSettings } from './lobby.js';
import { initMusic, enterMusic, leaveMusic, applyState, setMusicMuted, onMusicAudible, isMusicStuck } from './music.js';
import { enterMixer, leaveMixer, setAmbientEnabled, onMixerInput } from './audio/mixer.js';
import { initChat, enterChat, leaveChat, receiveChat, clearChat, getNickname, resetNickname } from './chat.js';
import { renderPeople, resetPeople } from './people.js';
import { initSupport } from './support.js';
import { initFeedback } from './feedback.js';
import { track } from './analytics.js';

const $ = id => document.getElementById(id);
const scene = new Scene($('scene'));

let current = null;   // { id, effect, media }
let socket = null;
// 소리 켜짐/꺼짐은 브라우저에 기억하고, 처음이면 둘 다 켠다
let musicOn = store.get('musicOn', true);      // 음악(YouTube) 소리
let ambientOn = store.get('ambientOn', true);  // 환경음
// 음악이 나오는 중에 사용자가 직접 환경음을 켰으면 음악과 함께 듣고 싶은 것이므로, 다음 곡에서도 자동으로 끄지 않는다
let ambientWithMusic = store.get('ambientWithMusic', false);
let musicAudible = false;
// 브라우저는 사용자가 한 번 누르기 전에는 소리를 막는다. 새로고침·주소로 들어오면 음소거로 틀어 두었다가
// 페이지 어디든 처음 누를 때(unlockSound) 켜 둔 소리를 낸다.
// 다만 브라우저가 클릭 없이도 소리를 허락하면(크롬: 이 사이트에서 소리를 자주 들었을 때, 엣지·파이어폭스: 자동 재생 허용) 바로 소리를 낸다
function autoplayAllowed() {
  try {
    if (navigator.getAutoplayPolicy) return navigator.getAutoplayPolicy('mediaelement') === 'allowed'; // 파이어폭스
    const Ctx = window.AudioContext || window.webkitAudioContext;
    const ctx = new Ctx(); // 허락되지 않으면 'suspended' 로 만들어진다
    const allowed = ctx.state === 'running';
    ctx.close();
    return allowed;
  } catch {
    return false;
  }
}
const activated = () => navigator.userActivation?.hasBeenActive ?? false;
let soundUnlocked = activated() || autoplayAllowed();
const soundGuessed = soundUnlocked && !activated(); // 누르지 않았는데 허락된다고 보고 소리를 낸 경우
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
    go('/', { replace: true }); // 없거나 닫힌 방이면 로비로
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
  current = { id: room.id, isDefault: room.isDefault, room, effect: effectModule.create(scene), media };
  track('room_enter', { theme: room.theme.id, room_type: room.isDefault ? 'default' : 'user' });
  showRoomTitle(room);
  $('hint').textContent = theme.hint;
  $('presence-text').textContent = '연결 중…';
  $('host-chip').hidden = true;
  $('reset-room').hidden = true;
  $('room-settings').hidden = true;

  enterMixer(room.id, ambients);
  enterMusic(room.id);
  resetPeople();
  socket = connectRoom(room.id, {
    nickname: getNickname,
    onChat: receiveChat,
    onChatClear: clearChat,
    onPresence: people => renderPeople(people, room.id),
    // 내보내지면 로비로 가서 슬픈 고양이 창을 띄운다 (다시 연결하지 않는다)
    onKicked: info => { showKicked(info); go('/', { replace: true }); },
    // 방장·관리자가 방을 닫았거나 방이 꽉 찼으면 로비로 가서 알린다
    onRoomGone: info => {
      if (info.type === 'room-full') showNotice('방이 꽉 찼어요', `이 방은 최대 ${info.max}명까지 들어갈 수 있어요. 자리가 나면 다시 와 주세요.`);
      else showNotice('방이 닫혔어요', `${info.by === 'admin' ? '관리자가' : '방장이'} 이 방을 닫았어요. 다른 방에서 쉬어 가세요.`);
      go('/', { replace: true });
    },
    // 방장·관리자가 방 설정을 바꾸면 새로 불러온다
    onRoomUpdated: () => refreshRoom(room.id),
    onState: state => {
      applyState(state);
      setPresence(state.listeners);
      $('host-chip').hidden = !state.hostIsMe;
      $('reset-room').hidden = !(current?.isDefault && state.hostIsMe); // 기본 방의 방장만
      $('room-settings').hidden = !(current && !current.isDefault && state.hostIsMe); // 사람이 만든 방의 방장만
    },
    // 소켓이 연결된 뒤(=방에 들어온 뒤)에 지난 대화를 불러온다
    onStatus: status => {
      if (status === 'open') enterChat(room.id);
      if (status === 'reconnecting') $('presence-text').textContent = '다시 연결하는 중…';
    },
  });
}

function showRoomTitle(room) {
  const { theme } = room;
  $('title').textContent = room.name;
  $('subtitle').textContent = room.name === theme.title ? theme.subtitle : `${theme.title} · ${theme.subtitle}`;
  $('hint').textContent = theme.hint;
  document.title = `${room.name} · 힐링룸`;
}

/** 방장이 설정을 바꿨을 때: 테마가 바뀌었으면 배경·효과·환경음까지 새로 들어가고, 아니면 이름만 바꾼다 */
async function refreshRoom(id) {
  let detail;
  try {
    detail = await api('GET', `/api/rooms/${encodeURIComponent(id)}`);
  } catch {
    return;
  }
  if (current?.id !== id) return;
  if (detail.room.theme.id !== current.room.theme.id) {
    enterRoom(id); // 새로고침처럼 잠깐 다시 연결 (20초 안이라 방장 자리는 그대로)
    return;
  }
  current.room = detail.room;
  showRoomTitle(detail.room);
}

/** 슬픈 고양이 알림 창 (내보내짐, 방 닫힘, 방 꽉 참) */
function showNotice(title, text) {
  $('kicked-title').textContent = title;
  $('kicked-text').textContent = text;
  $('kicked-dialog').hidden = false;
  $('kicked-ok').focus();
}

/** 내보내졌을 때 창: 누가 내보냈는지, 언제 다시 들어올 수 있는지 */
function showKicked({ by, until }) {
  const at = new Date(until).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });
  if (by === 'profanity') {
    resetNickname();
    showNotice('이용이 잠시 제한됐어요', `욕설이나 음란한 말을 써서 ${at}까지 채팅·방 만들기·방 입장이 제한됐어요. 모두가 쉬어 가는 곳이니 고운 말 부탁해요.`);
    return;
  }
  showNotice(by === 'ban' ? '아직 이 방에 들어갈 수 없어요' : '방에서 내보내졌어요',
    by === 'host' ? `방장이 이 방에서 내보냈어요. ${at} 이후에 다시 들어올 수 있어요.`
      : by === 'admin' ? `관리자가 이 방에서 내보냈어요. ${at} 이후에 다시 들어올 수 있어요.`
        : `얼마 전에 이 방에서 내보내졌어요. ${at} 이후에 다시 와 주세요.`);
}
$('kicked-ok').onclick = () => { $('kicked-dialog').hidden = true; };
$('kicked-dialog').addEventListener('click', e => { if (e.target === $('kicked-dialog')) $('kicked-dialog').hidden = true; });

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
    go(`/${id}`);
  }, lobbyMessage);
  lobbyMessage = '';
}

// ---------------------------------------------------------------- 주소: 방은 /{방id} (예: /cafe). # 없이 history API 로 옮긴다

/** 주소를 바꾸고 그 화면을 보여준다 (새로고침 없이) */
function go(path, { replace = false } = {}) {
  if (location.pathname !== path) history[replace ? 'replaceState' : 'pushState'](null, '', path);
  route();
}

function route() {
  // 예전 주소(/#cafe)로 들어오면 새 주소(/cafe)로 다시 연다 (서버가 방이 있는지 보고, 없으면 404 화면)
  if (location.hash.length > 1) {
    location.replace(`/${encodeURIComponent(decodeURIComponent(location.hash.slice(1)))}`);
    return;
  }
  const id = decodeURIComponent(location.pathname.slice(1));
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

// 클릭 없이 소리를 내 봤는데 브라우저가 막았으면(8초 넘게 재생이 안 되거나 YouTube 가 음소거해 둠)
// 음소거로 돌려 일단 영상이 흐르게 하고, 처음 누를 때 소리를 낸다 ("🔈 소리 켜기" 버튼이 다시 보임)
if (soundGuessed) {
  let strikes = 0;
  const check = setInterval(() => {
    if (activated()) { clearInterval(check); return; } // 이미 눌렀으면 브라우저가 허락한다
    strikes = isMusicStuck() ? strikes + 1 : 0;
    if (strikes < 2) return;
    clearInterval(check);
    soundUnlocked = false;
    setMusic(musicOn);
    setAmbient(ambientOn);
  }, 4000);
}

// 음악이 들리기 시작하면 환경음을 잠시 끄고, 음악이 멈추면 (자동으로 껐던 경우에만) 다시 켠다.
// 음악과 함께 듣겠다고 직접 켠 경우(ambientWithMusic)는 끄지 않는다
// (예전에는 직접 켜도 다음 곡이 시작되면 또 꺼져서 "환경음 조절이 안 된다"는 의견이 왔다)
onMusicAudible(audible => {
  musicAudible = audible;
  if (audible && ambientOn && !ambientWithMusic) {
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

$('back').onclick = () => go('/');
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
/** 사용자가 직접 환경음을 켜고 끈다. 음악이 나오는 중에 켜면 "음악과 함께 듣기"로 기억한다 */
function chooseAmbient(on) {
  setAmbient(on);
  store.set('ambientOn', on);
  if (!on || musicAudible) {
    ambientWithMusic = on;
    store.set('ambientWithMusic', on);
  }
}
$('ambient-sound').onclick = () => {
  if (consumeUnlockClick($('ambient-sound'))) return;
  // 음악 때문에 쉬는 중에 누르면 "다시 켜기"로 본다 (직접 켠 것은 음악이 나와도 유지)
  chooseAmbient(!ambientOn);
};
// 꺼져 있거나 음악 때문에 쉬는 중에 슬라이더를 움직이면 들어 보려는 것이므로 켠다
onMixerInput(() => {
  if (!ambientOn) chooseAmbient(true);
});
$('fx').onclick = e => {
  scene.fxOn = !scene.fxOn;
  e.target.textContent = scene.fxOn ? '효과 끄기' : '효과 켜기';
};
scene.canvas.onclick = e => current?.effect.click(...scene.toUV(e.clientX, e.clientY));
addEventListener('popstate', route); // 뒤로·앞으로 가기

initMusic();
initChat();
initSupport();
initFeedback();
setMusic(musicOn);   // 아직 누르지 않았으면 음소거로 시작하고, 누르는 순간 소리를 낸다
setAmbient(ambientOn);
$('room-settings').onclick = () => current && openRoomSettings(current.room);
// 새 버전이 배포됐으면 로비에서 조용히 새로고침한다. 방에서 음악을 듣는 중에는 끊지 않고, 로비로 나오면 바꾼다
// (로비는 10초마다 방 목록을 부르므로 그때마다 다시 확인). 열린 창이 있거나 쓰던 의견이 있으면 기다린다
whenOutdated(() => {
  const inLobby = !current && location.pathname === '/';
  const busy = document.querySelector('.dialog-backdrop:not([hidden])') || $('feedback-text')?.value.trim();
  if (inLobby && !busy) location.reload();
});
await syncVisitor(); // 쿠키의 방문자 id 와 맞춘 뒤 들어간다 (내보내기 금지를 저장소만 지워 피하지 못하게)
route();

(function loop() {
  t++;
  if (current) {
    scene.drawBackground();
    if (scene.fxOn) current.effect.draw(t);
  }
  requestAnimationFrame(loop);
})();
