// 방 채팅: 지난 대화 불러오기, 보내기, 실시간으로 받은 메시지 표시, 닉네임(브라우저에 저장),
// 이미지(붙여넣기·끌어다 놓기 → 줄여서 data URL 로 보냄, 누르면 크게 보기)
import { api, store } from './api.js';
import { track } from './analytics.js';

const $ = id => document.getElementById(id);
const list = $('chat-list');

const ADJECTIVES = ['졸린', '따뜻한', '느긋한', '포근한', '조용한', '반짝이는', '말랑한', '나른한', '설레는', '잔잔한'];
const NOUNS = ['고양이', '장작', '파도', '별똥별', '수달', '달빛', '모닥불', '조개', '구름', '부엉이'];
const pick = items => items[Math.floor(Math.random() * items.length)];

let roomId = null;
let seen = new Set();      // 같은 메시지가 두 번 그려지지 않게 (보낸 응답과 실시간 알림이 겹칠 수 있음)
let unread = 0;
let pendingImage = null;   // 보낼 이미지 (data URL)
let nickname = store.get('chatNickname', null) ?? `${pick(ADJECTIVES)} ${pick(NOUNS)}`;
store.set('chatNickname', nickname);

// ---------------------------------------------------------------- 그리기

// 닉네임마다 같은 색이 나오도록 (따뜻한 색 계열 안에서)
function colorFor(name) {
  let hash = 0;
  for (const ch of name) hash = (hash * 31 + ch.codePointAt(0)) >>> 0;
  return `hsl(${(hash % 360)}, 65%, 72%)`;
}

const timeText = ms => new Date(ms).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });
const nearBottom = () => list.scrollHeight - list.scrollTop - list.clientHeight < 40;
const collapsed = () => $('chat').classList.contains('collapsed');

function renderEmpty() {
  if (!list.children.length) list.append(Object.assign(document.createElement('li'), { className: 'empty', textContent: '첫 인사를 남겨 보세요' }));
}

function append(message, { scroll = false } = {}) {
  if (seen.has(message.id)) return;
  seen.add(message.id);
  list.querySelector('.empty')?.remove();
  const stick = scroll || nearBottom();
  const li = document.createElement('li');
  li.className = `chat-line${message.mine ? ' mine' : ''}`;
  li.dataset.id = message.id;
  const who = Object.assign(document.createElement('span'), { className: 'who', textContent: message.nickname });
  who.style.color = colorFor(message.nickname);
  const text = Object.assign(document.createElement('span'), { textContent: message.text }); // textContent: HTML 로 해석하지 않음
  const when = Object.assign(document.createElement('span'), { className: 'when', textContent: timeText(message.sentAt) });
  // 방장에게만 보이는 지우기 버튼 (CSS: .chat-list.host .chat-del)
  const del = Object.assign(document.createElement('button'), { className: 'chat-del', type: 'button', textContent: '🗑', title: '이 메시지 지우기 (방장)' });
  del.addEventListener('click', () => deleteMessage(message.id));
  li.append(who, text, when, del);
  // 이미지: 서버가 확인한 data URL(JPEG·PNG·WebP)만 그린다
  if (typeof message.image === 'string' && /^data:image\/(jpeg|png|webp);base64,/.test(message.image)) {
    const img = Object.assign(document.createElement('img'), {
      className: 'chat-image', src: message.image, alt: `${message.nickname}님이 보낸 이미지`, loading: 'lazy',
    });
    img.addEventListener('click', () => openViewer(message.image));
    img.addEventListener('load', () => { if (stick) list.scrollTop = list.scrollHeight; }, { once: true });
    li.append(img);
  }
  list.append(li);
  if (stick) list.scrollTop = list.scrollHeight;
}

function setUnread(count) {
  unread = count;
  $('chat-unread').textContent = count > 99 ? '99+' : count;
  $('chat-unread').hidden = count === 0;
}

function message(text, isError = false) {
  $('chat-msg').textContent = text;
  $('chat-msg').classList.toggle('error', isError);
}

function renderNickname() {
  $('chat-nick').textContent = `${nickname} ✎`;
}

function setCollapsed(value) {
  $('chat').classList.toggle('collapsed', value);
  $('chat-toggle').textContent = value ? '▔' : '▁';
  $('chat-toggle').title = value ? '펼치기' : '접기';
  store.set('chatCollapsed', value);
  if (!value) { setUnread(0); list.scrollTop = list.scrollHeight; }
}

// ---------------------------------------------------------------- 동작

async function send(e) {
  e.preventDefault();
  const input = $('chat-input');
  const text = input.value.trim();
  if ((!text && !pendingImage) || !roomId) return;
  const button = e.submitter;
  button.disabled = true;
  try {
    const sent = await api('POST', `/api/rooms/${roomId}/chat`, { nickname, text, image: pendingImage });
    track('chat_send', { image: pendingImage ? 1 : 0 });
    input.value = '';
    setPendingImage(null);
    message('');
    append(sent, { scroll: true });
  } catch (err) {
    message(err.message, true);
  } finally {
    button.disabled = false;
    input.focus();
  }
}

// ---------------------------------------------------------------- 이미지 (파일로 저장하지 않고 data URL 로 채팅 기록에 넣는다)

const MAX_SIDE = 800;          // 긴 쪽을 이만큼(px)까지 줄인다
const MAX_LENGTH = 150000;     // data URL 길이 상한 (서버 ChatImage.MAX_LENGTH 160,000 보다 조금 작게)

/** 이미지 파일을 줄이고 압축해 data URL 로. WebP 로 못 만드는 브라우저(사파리 일부)는 JPEG */
async function compress(file) {
  const url = URL.createObjectURL(file);
  try {
    const img = await new Promise((resolve, reject) => {
      const el = new Image();
      el.onload = () => resolve(el);
      el.onerror = () => reject(new Error('이미지를 읽지 못했어요'));
      el.src = url;
    });
    let scale = Math.min(1, MAX_SIDE / Math.max(img.naturalWidth, img.naturalHeight));
    const canvas = document.createElement('canvas');
    for (let attempt = 0; attempt < 5; attempt++, scale *= 0.75) {
      canvas.width = Math.max(1, Math.round(img.naturalWidth * scale));
      canvas.height = Math.max(1, Math.round(img.naturalHeight * scale));
      const ctx = canvas.getContext('2d');
      ctx.fillStyle = '#fff'; // 투명한 PNG 를 JPEG 로 바꿀 때 검게 되지 않게
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
      for (const quality of [0.82, 0.7, 0.55]) {
        let data = canvas.toDataURL('image/webp', quality);
        if (!data.startsWith('data:image/webp')) data = canvas.toDataURL('image/jpeg', quality);
        if (data.length <= MAX_LENGTH) return data;
      }
    }
    throw new Error('이미지가 너무 커요');
  } finally {
    URL.revokeObjectURL(url);
  }
}

function setPendingImage(data) {
  pendingImage = data;
  $('chat-attach').hidden = !data;
  if (data) $('chat-attach-img').src = data; else $('chat-attach-img').removeAttribute('src');
}

async function attach(file) {
  if (!file || !file.type.startsWith('image/')) return;
  if (file.type === 'image/svg+xml') { message('SVG 이미지는 보낼 수 없어요', true); return; }
  try {
    message('이미지를 줄이는 중…');
    setPendingImage(await compress(file));
    message('');
    $('chat-input').focus();
  } catch (err) {
    message(err.message, true);
  }
}

const imageFrom = items => [...(items ?? [])].find(item => item.kind === 'file' && item.type.startsWith('image/'))?.getAsFile();

function openViewer(src) {
  $('image-viewer-img').src = src;
  $('image-viewer').hidden = false;
}

function closeViewer() {
  $('image-viewer').hidden = true;
  $('image-viewer-img').removeAttribute('src');
}

// ---------------------------------------------------------------- 메시지 하나 지우기 (방장)

async function deleteMessage(id) {
  if (!roomId || !confirm('이 메시지를 지울까요? 방에 있는 모두의 화면에서 사라져요.')) return;
  try {
    await api('DELETE', `/api/rooms/${roomId}/chat/${encodeURIComponent(id)}`);
    removeChat(id); // 알림(chat-delete)도 오지만 바로 지운다
  } catch (err) {
    message(err.message, true);
  }
}

function startNicknameEdit() {
  $('chat-nick').hidden = true;
  $('chat-nick-form').hidden = false;
  $('chat-nick-input').value = nickname;
  $('chat-nick-input').select();
}

function finishNicknameEdit(e) {
  e?.preventDefault();
  const value = $('chat-nick-input').value.replace(/\s+/g, ' ').trim();
  if (value && [...value].length <= 12) {
    nickname = value;
    store.set('chatNickname', nickname);
    renderNickname();
    dispatchEvent(new CustomEvent('nickname-change', { detail: nickname })); // 접속자 목록에도 반영
    message('');
  } else if (e) {
    message('닉네임은 1~12자로 지어 주세요', true);
    return;
  }
  $('chat-nick-form').hidden = true;
  $('chat-nick').hidden = false;
}

// ---------------------------------------------------------------- 외부에서 쓰는 함수

export const getNickname = () => nickname;

export function initChat() {
  renderNickname();
  $('chat-form').addEventListener('submit', send);
  $('chat-nick').addEventListener('click', startNicknameEdit);
  $('chat-nick-form').addEventListener('submit', finishNicknameEdit);
  $('chat-nick-input').addEventListener('keydown', e => { if (e.key === 'Escape') finishNicknameEdit(); });
  $('chat-toggle').addEventListener('click', () => setCollapsed(!collapsed()));
  // 이미지: Ctrl+V 로 붙여넣기, 채팅 창에 끌어다 놓기
  $('chat').addEventListener('paste', e => {
    const file = imageFrom(e.clipboardData?.items);
    if (file) { e.preventDefault(); attach(file); }
  });
  $('chat').addEventListener('dragover', e => {
    if (![...(e.dataTransfer?.types ?? [])].includes('Files')) return;
    e.preventDefault();
    $('chat').classList.add('dropping');
  });
  $('chat').addEventListener('dragleave', e => { if (!$('chat').contains(e.relatedTarget)) $('chat').classList.remove('dropping'); });
  $('chat').addEventListener('drop', e => {
    $('chat').classList.remove('dropping');
    const file = [...(e.dataTransfer?.files ?? [])].find(f => f.type.startsWith('image/'));
    if (!file) return;
    e.preventDefault();
    if (collapsed()) setCollapsed(false);
    attach(file);
  });
  $('chat-attach-clear').addEventListener('click', () => setPendingImage(null));
  $('image-viewer').addEventListener('click', closeViewer);
  addEventListener('keydown', e => { if (e.key === 'Escape' && !$('image-viewer').hidden) closeViewer(); });
  setCollapsed(store.get('chatCollapsed', matchMedia('(max-width: 720px)').matches));
}

/** 방에 들어갈 때: 지난 대화를 불러온다 */
export async function enterChat(id) {
  roomId = id;
  seen = new Set();
  list.replaceChildren();
  setUnread(0);
  message('');
  try {
    const history = await api('GET', `/api/rooms/${id}/chat`);
    if (roomId !== id) return;
    history.forEach(m => append(m));
    list.scrollTop = list.scrollHeight;
  } catch (e) {
    message(e.message, true);
  }
  renderEmpty();
}

/** 욕설·음란어 닉네임으로 제한됐을 수 있으니 새 무작위 닉네임으로 바꿔 둔다 (그대로면 다시 들어올 때 또 막힌다) */
export function resetNickname() {
  nickname = `${pick(ADJECTIVES)} ${pick(NOUNS)}`;
  store.set('chatNickname', nickname);
}

export function leaveChat() {
  roomId = null;
  setPendingImage(null);
}

/** 기록 초기화로 대화가 지워졌을 때 화면도 비운다 */
export function clearChat() {
  seen = new Set();
  list.replaceChildren();
  setUnread(0);
  renderEmpty();
  message('대화 기록이 초기화됐어요');
}

/** 방장이면 메시지마다 지우기 버튼을 보인다 */
export function setChatHost(isHost) {
  list.classList.toggle('host', !!isHost);
}

/** 방장·관리자가 지운 메시지를 화면에서 뺀다 */
export function removeChat(id) {
  [...list.children].find(li => li.dataset.id === id)?.remove();
  renderEmpty();
}

/** WebSocket 으로 받은 새 메시지 */
export function receiveChat(chatMessage) {
  if (!roomId) return;
  const isNew = !seen.has(chatMessage.id);
  append(chatMessage);
  if (isNew && collapsed() && !chatMessage.mine) setUnread(unread + 1);
}
