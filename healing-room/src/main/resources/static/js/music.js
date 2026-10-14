// 함께 듣는 음악: 서버가 보내준 상태에 맞춰 YouTube 플레이어를 동기화하고, 대기열·투표 화면을 그린다.
import { api, store } from './api.js';

const $ = id => document.getElementById(id);
const DRIFT_LIMIT = 3; // 다른 사람과 이만큼(초) 어긋나면 위치를 다시 맞춘다

let player = null;
let playerReady = null;   // 플레이어 준비 Promise
let roomId = null;
let state = null;
let clockOffset = 0;      // 서버 시각 - 내 시각 (ms)
let loadedItemId = null;
let durationSent = false;
let muted = true;
let syncTimer = null;
let tab = 'queue';        // 'queue' 다음 곡 / 'history' 지난 신청곡
let errorVotedItemId = null;

// ---------------------------------------------------------------- YouTube 플레이어

function loadYouTubeApi() {
  if (window.YT?.Player) return Promise.resolve();
  return new Promise(resolve => {
    const previous = window.onYouTubeIframeAPIReady;
    window.onYouTubeIframeAPIReady = () => { previous?.(); resolve(); };
    const script = document.createElement('script');
    script.src = 'https://www.youtube.com/iframe_api';
    document.head.append(script);
  });
}

function ensurePlayer() {
  playerReady ??= loadYouTubeApi().then(() => new Promise(resolve => {
    player = new YT.Player('yt-player', {
      width: '356', height: '200',
      // 브라우저 자동재생 정책 때문에 음소거로 시작하고, "소리 켜기"를 누르면 소리를 켠다
      playerVars: { autoplay: 1, mute: 1, playsinline: 1, rel: 0, controls: 1 },
      events: {
        onReady: () => resolve(player),
        onStateChange: onPlayerStateChange,
        onError: onPlayerError,
      },
    });
  }));
  return playerReady;
}

function onPlayerStateChange(e) {
  if (e.data === YT.PlayerState.PLAYING) {
    if (!muted) player.unMute();
    reportDuration();
  }
}

// 이 브라우저에서 재생할 수 없는 영상(삭제·외부 재생 금지 등)이면 자동으로 넘기기에 투표한다.
// 모두에게 안 나오면 투표가 과반을 넘어 넘어가고, 일부에게만 안 나오면 그 사람들의 한 표로만 남는다
// YouTube 오류 코드: 100 없는 영상 / 101·150 외부 재생 금지. (2 잘못된 요청, 5 일시적 플레이어 오류는 제외)
const UNPLAYABLE = new Set([100, 101, 150]);

function onPlayerError(e) {
  if (!UNPLAYABLE.has(e.data)) return;
  const np = state?.nowPlaying;
  if (!np || np.id !== loadedItemId || errorVotedItemId === np.id) return;
  errorVotedItemId = np.id;
  message('이 영상은 여기서 재생할 수 없어서 자동으로 넘기기에 투표했어요', true);
  if (np.mine || !np.votedByMe) vote(np, { silent: true });
}

// 영상 길이를 서버에 알려준다. 서버는 이 값으로 곡이 끝나는 시점을 판단한다 (라이브는 0이라 보내지 않음)
function reportDuration() {
  const np = state?.nowPlaying;
  if (!np || durationSent || np.id !== loadedItemId) return;
  const seconds = Math.round(player.getDuration());
  if (seconds > 0) {
    durationSent = true;
    api('PUT', `/api/rooms/${roomId}/queue/${np.id}/duration`, { seconds }).catch(() => {});
  }
}

const serverNow = () => Date.now() + clockOffset;
const expectedPosition = np => Math.max(0, (serverNow() - np.startedAt) / 1000);

async function sync() {
  const np = state?.nowPlaying;
  const p = await ensurePlayer();
  if (!np) {
    loadedItemId = null;
    p.stopVideo();
    return;
  }
  if (np.id !== loadedItemId) {
    loadedItemId = np.id;
    durationSent = false;
    // 라이브 방송에 시작 위치를 주면 "녹화를 볼 수 없음" 오류가 나므로, 길이를 아는(라이브가 아닌) 영상만 위치를 지정한다.
    // 길이를 아직 모르면 0초부터 틀고, 다른 사람이 길이를 알려주면 다음 동기화 때 위치를 맞춘다
    p.loadVideoById(np.duration
      ? { videoId: np.youtubeId, startSeconds: expectedPosition(np) }
      : { videoId: np.youtubeId });
    if (muted) p.mute(); else p.unMute();
    return;
  }
  // 라이브(길이 없음)가 아니고 재생 중일 때만 위치를 맞춘다. 직접 멈춘 경우는 존중한다
  if (np.duration && p.getPlayerState() === YT.PlayerState.PLAYING) {
    const expected = expectedPosition(np);
    if (Math.abs(p.getCurrentTime() - expected) > DRIFT_LIMIT) p.seekTo(expected, true);
  }
}

// ---------------------------------------------------------------- 화면

function voteButton(button, track, required) {
  button.hidden = false;
  button.classList.toggle('voted', track.votedByMe);
  const verb = track.id === state.nowPlaying?.id ? '넘기기' : '빼기';
  if (track.mine) {
    button.textContent = verb;
    button.title = '내가 신청한 곡은 바로 넘기거나 뺄 수 있어요';
  } else {
    button.textContent = `${track.votedByMe ? '투표 취소' : `${verb} 투표`} ${track.votes}/${required}`;
    button.title = `방 인원의 절반을 넘게(${required}명 이상) 투표하면 ${verb === '넘기기' ? '넘어가요' : '빠져요'}`;
  }
  button.onclick = () => vote(track);
}

function tagFor(track) {
  if (track.isDefault) return ['기본 음악', ''];
  if (track.mine) return ['내 신청곡', 'mine'];
  return ['신청곡', ''];
}

function emptyRow(text) {
  const li = document.createElement('li');
  li.className = 'empty';
  li.textContent = text;
  return li;
}

function render() {
  const np = state.nowPlaying;
  $('mini-title').textContent = np ? `♪ ${np.title}` : '';
  const [label, cls] = np ? tagFor(np) : ['', ''];
  $('now-tag').textContent = label;
  $('now-tag').className = `tag ${cls}`;
  $('now-tag').hidden = !np;
  $('now-title').textContent = np ? np.title : '재생할 음악이 없어요. 링크를 신청해 보세요';
  if (np) voteButton($('now-vote'), np, state.required); else $('now-vote').hidden = true;

  $('queue-count').textContent = state.queue.length;
  const list = $('queue-list');
  list.replaceChildren();
  if (!state.queue.length) {
    list.append(emptyRow(np?.isDefault === false ? '다음 곡이 없어요. 끝나면 기본 음악이 나와요' : '신청한 곡이 여기에 쌓여요'));
  }
  state.queue.forEach((track, i) => {
    const li = document.createElement('li');
    const order = Object.assign(document.createElement('span'), { className: 'n', textContent: i + 1 });
    const [tagText, tagClass] = tagFor(track);
    const tag = Object.assign(document.createElement('span'), { className: `tag ${tagClass}`, textContent: tagText });
    const title = Object.assign(document.createElement('span'), { className: 't', textContent: track.title, title: track.title });
    const button = Object.assign(document.createElement('button'), { className: 'btn' });
    voteButton(button, track, state.required);
    li.append(order, tag, title, button);
    list.append(li);
  });
  if (tab === 'history' && np?.id !== lastHistoryFor) loadHistory();
}

// ---------------------------------------------------------------- 지난 신청곡

const HISTORY_LABEL = { played: ['다 들음', 'played'], skipped: ['넘김', 'skipped'], removed: ['빠짐', 'removed'] };
let lastHistoryFor = undefined;

function timeAgo(ms) {
  const minutes = Math.round((Date.now() + clockOffset - ms) / 60000);
  if (minutes < 1) return '방금';
  if (minutes < 60) return `${minutes}분 전`;
  const hours = Math.round(minutes / 60);
  return hours < 24 ? `${hours}시간 전` : `${Math.round(hours / 24)}일 전`;
}

async function loadHistory() {
  if (!roomId) return;
  lastHistoryFor = state?.nowPlaying?.id;
  const list = $('history-list');
  try {
    const items = await api('GET', `/api/rooms/${roomId}/queue/history`);
    list.replaceChildren();
    if (!items.length) list.append(emptyRow('아직 지난 신청곡이 없어요'));
    for (const item of items) {
      const li = document.createElement('li');
      const [label, cls] = HISTORY_LABEL[item.status] ?? [item.status, ''];
      const tag = Object.assign(document.createElement('span'), { className: `tag ${cls}`, textContent: label });
      const title = Object.assign(document.createElement('span'), {
        className: 't', textContent: (item.mine ? '(내 신청) ' : '') + item.title, title: item.title,
      });
      const when = Object.assign(document.createElement('span'), { className: 'when', textContent: item.endedAt ? timeAgo(item.endedAt) : '' });
      li.append(tag, title, when);
      list.append(li);
    }
  } catch (e) {
    list.replaceChildren(emptyRow('지난 신청곡을 불러오지 못했어요'));
  }
}

function showTab(name) {
  tab = name;
  document.querySelectorAll('#music .tab').forEach(b => b.classList.toggle('on', b.dataset.tab === name));
  $('queue-list').hidden = name !== 'queue';
  $('history-list').hidden = name !== 'history';
  if (name === 'history') loadHistory();
}

// 음악 창 크기: normal 기본 / min 최소화(제목만) / max 최대화 (브라우저에 기억)
function setView(view) {
  const music = $('music');
  music.classList.toggle('min', view === 'min');
  music.classList.toggle('max', view === 'max');
  $('music-min').textContent = view === 'min' ? '▔' : '▁';
  $('music-min').title = view === 'min' ? '복원' : '최소화';
  $('music-max').textContent = view === 'max' ? '❐' : '□';
  $('music-max').title = view === 'max' ? '원래 크기' : '최대화';
  store.set('musicView', view);
}

const currentView = () => $('music').classList.contains('min') ? 'min'
  : $('music').classList.contains('max') ? 'max' : 'normal';

function message(text, isError = false) {
  $('music-msg').textContent = text;
  $('music-msg').classList.toggle('error', isError);
}

async function vote(track, { silent = false } = {}) {
  const url = `/api/rooms/${roomId}/queue/${track.id}/vote`;
  try {
    if (track.votedByMe) {
      await api('DELETE', url);
    } else {
      const { applied } = await api('PUT', url);
      if (applied && !silent) message(track.mine ? '신청곡을 뺐어요' : '투표가 통과됐어요');
    }
  } catch (e) {
    if (!silent) message(e.message, true);
  }
}

async function addTrack(e) {
  e.preventDefault();
  const input = $('add-url');
  const button = e.submitter;
  button.disabled = true;
  message('영상을 확인하는 중…');
  try {
    await api('POST', `/api/rooms/${roomId}/queue`, { url: input.value });
    input.value = '';
    message('신청했어요');
  } catch (err) {
    message(err.message, true);
  } finally {
    button.disabled = false;
  }
}

// ---------------------------------------------------------------- 외부에서 쓰는 함수

export function initMusic() {
  $('add-form').addEventListener('submit', addTrack);
  $('music-min').addEventListener('click', () => setView(currentView() === 'min' ? 'normal' : 'min'));
  $('music-max').addEventListener('click', () => setView(currentView() === 'max' ? 'normal' : 'max'));
  $('mini-title').addEventListener('click', () => currentView() === 'min' && setView('normal'));
  document.querySelectorAll('#music .tab').forEach(b => b.addEventListener('click', () => showTab(b.dataset.tab)));
  setView(store.get('musicView', 'normal'));
}

/** 방에 들어갈 때 */
export function enterMusic(id) {
  roomId = id;
  state = null;
  loadedItemId = null;
  errorVotedItemId = null;
  lastHistoryFor = undefined;
  message('');
  showTab('queue');
  ensurePlayer();
  clearInterval(syncTimer);
  syncTimer = setInterval(() => state && sync(), 5000);
}

/** 방을 나갈 때 */
export function leaveMusic() {
  clearInterval(syncTimer);
  roomId = null;
  state = null;
  loadedItemId = null;
  player?.stopVideo();
}

/** 서버가 보낸 새 상태 */
export function applyState(next) {
  if (!roomId) return;
  clockOffset = next.serverTime - Date.now();
  state = next;
  render();
  sync();
}

export function setMusicMuted(value) {
  muted = value;
  if (!player) return;
  if (muted) player.mute(); else { player.unMute(); player.playVideo(); }
}
