// 함께 듣는 음악: 서버가 보내준 상태에 맞춰 YouTube 플레이어를 동기화하고, 대기열·투표 화면을 그린다.
import { api, store } from './api.js';
import { track } from './analytics.js';

const $ = id => document.getElementById(id);
const DRIFT_LIMIT = 3; // 다른 사람과 이만큼(초) 어긋나면 위치를 다시 맞춘다
// 위치를 옮기면(seekTo) YouTube 가 제목·일시정지·공유 버튼을 몇 초 띄운다. 옮긴 곳을 불러오느라 다시 밀리면
// 5초마다 또 옮기게 되어 버튼이 사라지지 않으므로, 옮기는 횟수를 아낀다
const SEEK_LEAD = 1;           // 불러오는 시간만큼 조금 앞으로 옮긴다(초)
const SEEK_COOLDOWN = 20000;   // 한 번 옮기면 이만큼(ms)은 다시 옮기지 않는다
const SEEK_MAX_STRIKES = 3;    // 연속으로 이만큼 옮겨도 계속 어긋나면
const SEEK_REST = 120000;      // 이만큼(ms) 맞추기를 쉰다 (조금 어긋나는 게 버튼이 계속 뜨는 것보다 낫다)
let lastSeekAt = 0;
let seekStrikes = 0;
let seekRestUntil = 0;

let player = null;
let playerReady = null;   // 플레이어 준비 Promise
let roomId = null;
let state = null;
let clockOffset = 0;      // 서버 시각 - 내 시각 (ms)
let loadedItemId = null;
let durationSent = false;
let muted = true;
let syncTimer = null;
let tab = 'queue';        // 'queue' 다음 곡 / 'history' 지난 신청곡 / 'recommend' 추천 / 'search' 검색
let errorVotedItemId = null;
let volume = store.get('musicVolume', 70);

// ---------------------------------------------------------------- "음악이 들리는 중" 알림
// 음악이 소리를 내며 재생되는 동안에는 환경음을 끄기 위해, 들리기 시작/멈춤을 밖(app.js)에 알린다.
// 곡과 곡 사이에 잠깐 멈추는 것으로 환경음이 깜빡이지 않도록, 멈춤은 조금 기다렸다가 알린다
const QUIET_DELAY = 4000;
let audible = false;
let audibleListener = null;
let quietTimer = null;

function setAudible(value, { immediate = false } = {}) {
  clearTimeout(quietTimer);
  if (value) {
    if (!audible) { audible = true; audibleListener?.(true); }
    return;
  }
  const quiet = () => { if (audible) { audible = false; audibleListener?.(false); } };
  if (immediate) quiet(); else quietTimer = setTimeout(quiet, QUIET_DELAY);
}

/** 음악이 들리기 시작하면 fn(true), 멈추면 fn(false) */
export function onMusicAudible(fn) {
  audibleListener = fn;
}

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
      // 브라우저 자동재생 정책 때문에 음소거로 시작하고, "음악 켜기"를 누르면 소리를 켠다
      // controls·disablekb·fs: 일시정지, 탐색, 키보드 조작을 없애 모두 같은 위치로 듣게 한다 (볼륨은 따로 슬라이더)
      // cc_load_policy 0: 자막을 켜지 않는다 (영상 주인이 자동으로 켜 둔 자막은 재생될 때 hideCaptions 로 끈다)
      playerVars: { autoplay: 1, mute: 1, playsinline: 1, rel: 0, controls: 0, disablekb: 1, fs: 0, iv_load_policy: 3, cc_load_policy: 0 },
      events: {
        onReady: () => { player.setVolume(volume); resolve(player); },
        onStateChange: onPlayerStateChange,
        onError: onPlayerError,
      },
    });
  }));
  return playerReady;
}

// 자막 끄기: 영상에 따라 자막이 기본으로 켜져 나오므로, 재생이 시작될 때마다 자막 모듈을 내린다
function hideCaptions() {
  try {
    player.setOption?.('captions', 'track', {});
    player.unloadModule?.('captions');
    player.unloadModule?.('cc'); // 예전 이름
  } catch { /* 자막이 없는 영상이면 무시 */ }
}

function onPlayerStateChange(e) {
  if (e.data === YT.PlayerState.PLAYING) {
    hideCaptions();
    if (!muted) player.unMute();
    reportDuration();
    setAudible(!muted);
  } else if (e.data === YT.PlayerState.PAUSED || e.data === YT.PlayerState.ENDED) {
    setAudible(false);
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
  if (seconds > 0 && !player.getVideoData?.().isLive) {
    durationSent = true;
    api('PUT', `/api/rooms/${roomId}/queue/${np.id}/duration`, { seconds }).catch(() => {});
  }
}

// 라이브가 아닌, 길이를 아는 영상인지. 서버가 아직 길이를 몰라도(아무도 보고하지 않았거나 늦을 때) 내 플레이어가 알면 위치를 맞춘다.
// 라이브에 위치를 주면 "녹화를 볼 수 없음" 오류가 나므로 라이브는 맞추지 않는다
function hasLength(np, p) {
  if (np.duration) return true;
  if (np.id !== loadedItemId) return false;
  try {
    return p.getDuration() > 0 && !p.getVideoData?.().isLive;
  } catch {
    return false;
  }
}

const serverNow = () => Date.now() + clockOffset;
// 지금 들려야 하는 위치(초). 방장이 멈췄으면 멈춘 시각 기준
const expectedPosition = np => Math.max(0, ((np.pausedAt ?? serverNow()) - np.startedAt) / 1000);

async function sync() {
  const np = state?.nowPlaying;
  const p = await ensurePlayer();
  if (!np) {
    loadedItemId = null;
    p.stopVideo();
    setAudible(false, { immediate: true });
    return;
  }
  if (np.id !== loadedItemId) {
    loadedItemId = np.id;
    durationSent = false;
    resetCatchUp();
    // 라이브 방송에 시작 위치를 주면 "녹화를 볼 수 없음" 오류가 나므로, 길이를 아는(라이브가 아닌) 영상만 위치를 지정한다.
    // 서버가 길이를 아직 모르면 0초부터 틀고, 재생이 시작되면 내 플레이어가 안 길이로 다음 동기화(최대 5초 뒤) 때 위치를 맞춘다
    const video = np.duration ? { videoId: np.youtubeId, startSeconds: expectedPosition(np) } : { videoId: np.youtubeId };
    if (np.pausedAt) p.cueVideoById(video); else p.loadVideoById(video); // 방장이 멈춘 곡이면 불러오기만
    if (muted) p.mute(); else p.unMute();
    return;
  }
  const playerState = p.getPlayerState();
  // 방장이 멈춘 상태: 모두 멈추고 위치만 맞춘다
  if (np.pausedAt) {
    if (playerState === YT.PlayerState.PLAYING || playerState === YT.PlayerState.BUFFERING) p.pauseVideo();
    if (hasLength(np, p) && Math.abs(p.getCurrentTime() - expectedPosition(np)) > 1.5) p.seekTo(expectedPosition(np), true);
    return;
  }
  // 직접 조작은 막혀 있으니, 멈춰 있으면(자동재생 실패, 방장이 다시 재생 등) 다시 재생한다
  if (playerState === YT.PlayerState.PAUSED || playerState === YT.PlayerState.CUED) {
    p.playVideo();
    return;
  }
  // 라이브(길이 없음)가 아닐 때만 위치를 맞춘다
  if (playerState === YT.PlayerState.PLAYING && hasLength(np, p)) catchUp(p, expectedPosition(np));
}

/** 어긋난 만큼 위치를 옮긴다. 너무 자주 옮기지 않는다 (SEEK_* 참고) */
function catchUp(p, expected) {
  const now = Date.now();
  if (Math.abs(p.getCurrentTime() - expected) <= DRIFT_LIMIT) {
    seekStrikes = 0;
    return;
  }
  if (now < seekRestUntil || now - lastSeekAt < SEEK_COOLDOWN) return;
  if (++seekStrikes > SEEK_MAX_STRIKES) {
    seekStrikes = 0;
    seekRestUntil = now + SEEK_REST;
    return;
  }
  lastSeekAt = now;
  p.seekTo(expected + SEEK_LEAD, true);
}

/** 새 곡이 시작되면 맞추기 기록을 처음부터 */
function resetCatchUp() {
  lastSeekAt = 0;
  seekStrikes = 0;
  seekRestUntil = 0;
}

// ---------------------------------------------------------------- 화면

function voteButton(button, track, required) {
  button.hidden = false;
  button.classList.toggle('voted', track.votedByMe);
  const verb = track.id === state.nowPlaying?.id ? '넘기기' : '빼기';
  if (track.mine || track.isDefault || state.hostIsMe) {
    button.textContent = verb;
    button.title = track.mine ? '내가 신청한 곡은 바로 넘기거나 뺄 수 있어요'
      : track.isDefault ? '기본 음악·자동 추천은 누구나 바로 넘길 수 있어요' : '방장은 바로 넘기거나 뺄 수 있어요';
  } else {
    button.textContent = `${track.votedByMe ? '투표 취소' : `${verb} 투표`} ${track.votes}/${required}`;
    button.title = `방 인원의 절반을 넘게(${required}명 이상) 투표하면 ${verb === '넘기기' ? '넘어가요' : '빠져요'}`;
  }
  button.onclick = () => vote(track);
}

function tagFor(track) {
  if (track.source === 'autoplay') return ['자동 추천', ''];
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
    list.append(emptyRow(np?.isDefault === false ? '다음 곡이 없어요. 끝나면 비슷한 곡이 자동으로 이어져요' : '신청한 곡이 여기에 쌓여요'));
  }
  state.queue.forEach((track, i) => {
    const li = document.createElement('li');
    const order = Object.assign(document.createElement('span'), { className: 'n', textContent: i + 1 });
    const [tagText, tagClass] = tagFor(track);
    const tag = Object.assign(document.createElement('span'), { className: `tag ${tagClass}`, textContent: tagText });
    const title = Object.assign(document.createElement('span'), { className: 't', textContent: track.title, title: track.title });
    const button = Object.assign(document.createElement('button'), { className: 'btn' });
    voteButton(button, track, state.required);
    li.append(order, tag, title);
    if (state.hostIsMe) li.append(moveButton(track, 'up', i === 0), moveButton(track, 'down', i === state.queue.length - 1));
    li.append(button);
    list.append(li);
  });
  updateProgress();
  if (np?.id !== lastListsFor) {
    if (tab === 'history') loadHistory();
    if (tab === 'recommend') loadRecommend();
  }
}

function moveButton(track, direction, disabled) {
  const button = Object.assign(document.createElement('button'), {
    className: 'move', type: 'button', textContent: direction === 'up' ? '▲' : '▼',
    title: direction === 'up' ? '앞으로' : '뒤로', disabled,
  });
  if (disabled) button.style.visibility = 'hidden';
  button.onclick = () => api('PUT', `/api/rooms/${roomId}/queue/${track.id}/move`, { direction })
    .catch(e => message(e.message, true));
  return button;
}

// ---------------------------------------------------------------- 재생 위치 (모두 보기, 방장만 조작)

let seeking = false;

function formatTime(seconds) {
  const s = Math.max(0, Math.floor(seconds));
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = String(s % 60).padStart(2, '0');
  return h ? `${h}:${String(m).padStart(2, '0')}:${sec}` : `${m}:${sec}`;
}

function updateProgress() {
  const np = state?.nowPlaying;
  const host = !!state?.hostIsMe;
  const seek = $('seek');
  $('host-toggle').hidden = !host || !np;
  if (np) {
    $('host-toggle').textContent = np.pausedAt ? '▶' : '❚❚';
    $('host-toggle').title = np.pausedAt ? '모두 이어서 재생' : '모두 멈춤';
  }
  const position = np ? expectedPosition(np) : 0;
  const duration = np?.duration ?? 0;
  seek.disabled = !host || !duration;
  seek.title = host ? (duration ? '옮기면 모두의 재생 위치가 바뀌어요' : '길이를 알 수 없는 영상(라이브)은 옮길 수 없어요')
    : '재생 위치는 방장만 옮길 수 있어요';
  if (!seeking) {
    seek.max = Math.max(1, duration);
    seek.value = Math.min(position, duration || position);
    $('time-now').textContent = formatTime(position);
  }
  $('time-total').textContent = !np ? '0:00' : duration ? formatTime(duration) : 'LIVE';
}

// ---------------------------------------------------------------- 지난 신청곡

const HISTORY_LABEL = { played: ['다 들음', 'played'], skipped: ['넘김', 'skipped'], removed: ['빠짐', 'removed'] };
let lastListsFor = undefined;   // 지난 신청곡/추천을 마지막으로 불러온 시점의 재생 곡 id

function timeAgo(ms) {
  const minutes = Math.round((Date.now() + clockOffset - ms) / 60000);
  if (minutes < 1) return '방금';
  if (minutes < 60) return `${minutes}분 전`;
  const hours = Math.round(minutes / 60);
  return hours < 24 ? `${hours}시간 전` : `${Math.round(hours / 24)}일 전`;
}

async function loadHistory() {
  if (!roomId) return;
  lastListsFor = state?.nowPlaying?.id;
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

// ---------------------------------------------------------------- 추천 + 내 신청 기록

const MY_REQUESTS = 'myRequests';

/** 신청에 성공한 곡을 브라우저에 기록한다 (자주 신청한 곡 추천용, 최대 100곡) */
function recordRequest({ youtubeId, title }) {
  const list = store.get(MY_REQUESTS, []);
  const found = list.find(item => item.youtubeId === youtubeId);
  if (found) { found.count += 1; found.lastAt = Date.now(); found.title = title; }
  else list.push({ youtubeId, title, count: 1, lastAt: Date.now() });
  list.sort((a, b) => b.lastAt - a.lastAt);
  store.set(MY_REQUESTS, list.slice(0, 100));
}

function myFavorites() {
  return store.get(MY_REQUESTS, [])
    .sort((a, b) => b.count - a.count || b.lastAt - a.lastAt)
    .slice(0, 10);
}

function suggestionRow({ youtubeId, title, count }, countLabel) {
  const li = document.createElement('li');
  li.append(Object.assign(document.createElement('span'), { className: 't', textContent: title, title }));
  if (count && countLabel) li.append(Object.assign(document.createElement('span'), { className: 'count', textContent: countLabel(count) }));
  const button = Object.assign(document.createElement('button'), { className: 'btn', type: 'button', textContent: '신청' });
  button.onclick = () => request(youtubeId, button);
  li.append(button);
  return li;
}

function section(list, heading, items, countLabel, emptyText) {
  list.append(Object.assign(document.createElement('div'), { className: 'rec-section', textContent: heading }));
  if (!items.length) {
    list.append(Object.assign(document.createElement('div'), { className: 'rec-note', textContent: emptyText }));
    return;
  }
  const ol = Object.assign(document.createElement('ol'), { className: 'rec-items' });
  items.forEach(item => ol.append(suggestionRow(item, countLabel)));
  list.append(ol);
}

async function loadRecommend() {
  if (!roomId) return;
  lastListsFor = state?.nowPlaying?.id;
  const list = $('recommend-list');
  list.replaceChildren(Object.assign(document.createElement('div'), { className: 'rec-note', textContent: '추천을 불러오는 중…' }));
  let data = null;
  try {
    data = await api('GET', `/api/rooms/${roomId}/recommendations`);
  } catch {
    // 서버 추천을 못 받아도 내 기록은 보여준다
  }
  list.replaceChildren();
  section(list, '내가 자주 신청한 곡', myFavorites(), n => `${n}번`, '곡을 신청하면 이 브라우저에 기록돼요');
  if (!data) {
    list.append(Object.assign(document.createElement('div'), { className: 'rec-note', textContent: '추천을 불러오지 못했어요' }));
    return;
  }
  const base = data.basedOn ? (data.basedOn.length > 24 ? data.basedOn.slice(0, 24) + '…' : data.basedOn) : '지금 곡';
  section(list, `비슷한 영상 · ${base}`, data.similar, null,
    data.similarAvailable ? '비슷한 영상을 찾지 못했어요' : 'YouTube API 키를 설정하면 비슷한 영상도 추천돼요');
  section(list, '이 곡과 함께 신청된 곡', data.together, n => `${n}개 방`, '아직 함께 신청된 곡이 없어요');
  section(list, '이 테마 인기곡 (30일)', data.popular, n => `${n}번`, '아직 신청 기록이 없어요');
}

// ---------------------------------------------------------------- 직접 검색

// YouTube 링크나 영상 id(11자)로 보이면 true → 검색하지 않고 바로 신청
function looksLikeLink(text) {
  const t = text.trim();
  if (/^https?:\/\//i.test(t) || /youtu\.?be/i.test(t)) return true;
  // 11자 영상 id: 숫자·-·_ 가 섞였거나 대소문자가 섞인 것 (영어 단어 하나는 검색으로)
  return /^[A-Za-z0-9_-]{11}$/.test(t) && /[0-9_-]|[A-Z].*[a-z]|[a-z].*[A-Z]/.test(t.slice(1));
}

function searchRow({ youtubeId, title, channel, thumbnail, seconds }) {
  const li = document.createElement('li');
  li.className = 'search-item';
  if (thumbnail) li.append(Object.assign(document.createElement('img'), { src: thumbnail, alt: '', loading: 'lazy' }));
  const text = Object.assign(document.createElement('span'), { className: 't' });
  text.append(Object.assign(document.createElement('span'), { className: 'title', textContent: title, title }),
    Object.assign(document.createElement('span'), { className: 'meta', textContent: `${channel} · ${formatTime(seconds)}` }));
  const button = Object.assign(document.createElement('button'), { className: 'btn', type: 'button', textContent: '신청' });
  button.onclick = async () => {
    if (await request(youtubeId, button)) { button.textContent = '신청됨'; button.disabled = true; }
  };
  li.append(text, button);
  return li;
}

function searchNote(text) {
  return Object.assign(document.createElement('div'), { className: 'rec-note', textContent: text });
}

async function search(query, button) {
  showTab('search');
  const list = $('search-list');
  list.replaceChildren(searchNote(`"${query}" 찾는 중…`));
  if (button) button.disabled = true;
  try {
    const results = await api('GET', `/api/rooms/${roomId}/search?q=${encodeURIComponent(query)}`);
    if (!results.length) { list.replaceChildren(searchNote('여기서 재생할 수 있는 영상을 찾지 못했어요. 다른 말로 찾아보세요')); return false; }
    const ol = Object.assign(document.createElement('ol'), { className: 'rec-items' });
    results.forEach(r => ol.append(searchRow(r)));
    list.replaceChildren(searchNote(`"${query}" 검색 결과 · 누르면 신청돼요`), ol);
    message('');
    return true;
  } catch (err) {
    list.replaceChildren(searchNote(err.message));
    return false;
  } finally {
    if (button) button.disabled = false;
  }
}

function showTab(name) {
  tab = name;
  document.querySelectorAll('#music .tab').forEach(b => b.classList.toggle('on', b.dataset.tab === name));
  $('queue-list').hidden = name !== 'queue';
  $('history-list').hidden = name !== 'history';
  $('recommend-list').hidden = name !== 'recommend';
  $('search-list').hidden = name !== 'search';
  if (name === 'search' && !$('search-list').childElementCount) {
    $('search-list').append(searchNote('아래 칸에 노래 제목이나 가수를 입력하고 신청을 누르면 찾아드려요'));
  }
  if (name === 'history') loadHistory();
  if (name === 'recommend') loadRecommend();
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
    if (track.votedByMe && !track.mine && !track.isDefault && !state?.hostIsMe) {
      await api('DELETE', url);
    } else {
      const { applied } = await api('PUT', url);
      if (applied && !silent) message(track.mine || track.isDefault || state?.hostIsMe ? '처리했어요' : '투표가 통과됐어요');
    }
  } catch (e) {
    if (!silent) message(e.message, true);
  }
}

/** 곡 신청 (링크 또는 영상 id). 성공하면 내 신청 기록에 남긴다 */
async function request(url, button) {
  if (button) button.disabled = true;
  message('영상을 확인하는 중…');
  try {
    const added = await api('POST', `/api/rooms/${roomId}/queue`, { url });
    recordRequest(added);
    track('song_request');
    message(`신청했어요: ${added.title}`);
    return true;
  } catch (err) {
    message(err.message, true);
    return false;
  } finally {
    if (button) button.disabled = false;
  }
}

async function addTrack(e) {
  e.preventDefault();
  const input = $('add-url');
  const text = input.value.trim();
  if (!text) { message('노래 제목이나 YouTube 링크를 입력해 주세요', true); input.focus(); return; }
  if (looksLikeLink(text)) {
    if (await request(text, e.submitter)) input.value = '';
  } else {
    await search(text, e.submitter);
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

  // 방장: 모두 멈춤/이어서 재생, 위치 옮기기
  $('host-toggle').addEventListener('click', () => {
    const np = state?.nowPlaying;
    if (!np) return;
    api('PUT', `/api/rooms/${roomId}/queue/playback`, { action: np.pausedAt ? 'resume' : 'pause' })
      .catch(e => message(e.message, true));
  });
  const seek = $('seek');
  seek.addEventListener('input', () => { seeking = true; $('time-now').textContent = formatTime(Number(seek.value)); });
  seek.addEventListener('change', () => {
    seeking = false;
    api('PUT', `/api/rooms/${roomId}/queue/playback`, { action: 'seek', seconds: Number(seek.value) })
      .catch(e => message(e.message, true));
  });
  setInterval(() => roomId && state && updateProgress(), 500);

  // 음악 볼륨 (플레이어 조작을 막았으므로 따로 둔다)
  $('music-volume').value = volume;
  $('music-volume-out').textContent = volume;
  $('music-volume').addEventListener('input', e => {
    volume = Number(e.target.value);
    $('music-volume-out').textContent = volume;
    store.set('musicVolume', volume);
    player?.setVolume(volume);
  });
}

/** 방에 들어갈 때 */
export function enterMusic(id) {
  roomId = id;
  state = null;
  loadedItemId = null;
  errorVotedItemId = null;
  lastListsFor = undefined;
  message('');
  $('search-list').replaceChildren(); // 검색 결과는 방마다 새로
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
  setAudible(false, { immediate: true });
}

/** 서버가 보낸 새 상태 */
export function applyState(next) {
  if (!roomId) return;
  clockOffset = next.serverTime - Date.now();
  // 방장이 위치를 옮기거나 다시 재생하면 시작 시각이 바뀐다 → 기다리지 않고 바로 맞춘다
  if (next.nowPlaying?.startedAt !== state?.nowPlaying?.startedAt) resetCatchUp();
  state = next;
  render();
  sync();
}

/**
 * 지금 곡이 있어 들려야 하는데 들리지 않는지 (재생이 안 되거나, 소리를 켰는데 YouTube 가 음소거해 둠).
 * 클릭 없이 소리를 내 봤다가 브라우저가 막았는지 알아보는 데 쓴다 (app.js)
 */
export function isMusicStuck() {
  const np = state?.nowPlaying;
  if (!np || np.pausedAt || muted || !player?.getPlayerState) return false;
  return player.getPlayerState() !== YT.PlayerState.PLAYING || player.isMuted();
}

export function setMusicMuted(value) {
  muted = value;
  if (muted) setAudible(false, { immediate: true });
  if (!player) return;
  if (muted) {
    player.mute();
  } else {
    player.unMute();
    if (!state?.nowPlaying?.pausedAt) player.playVideo(); // 방장이 멈춘 상태면 그대로
    if (player.getPlayerState?.() === YT.PlayerState.PLAYING) setAudible(true);
  }
}
