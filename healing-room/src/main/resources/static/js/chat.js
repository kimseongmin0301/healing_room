// 방 채팅: 지난 대화 불러오기, 보내기, 실시간으로 받은 메시지 표시, 닉네임(브라우저에 저장)
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
  const who = Object.assign(document.createElement('span'), { className: 'who', textContent: message.nickname });
  who.style.color = colorFor(message.nickname);
  const text = Object.assign(document.createElement('span'), { textContent: message.text }); // textContent: HTML 로 해석하지 않음
  const when = Object.assign(document.createElement('span'), { className: 'when', textContent: timeText(message.sentAt) });
  li.append(who, text, when);
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
  if (!text || !roomId) return;
  const button = e.submitter;
  button.disabled = true;
  try {
    const sent = await api('POST', `/api/rooms/${roomId}/chat`, { nickname, text });
    track('chat_send');
    input.value = '';
    message('');
    append(sent, { scroll: true });
  } catch (err) {
    message(err.message, true);
  } finally {
    button.disabled = false;
    input.focus();
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
}

/** 기록 초기화로 대화가 지워졌을 때 화면도 비운다 */
export function clearChat() {
  seen = new Set();
  list.replaceChildren();
  setUnread(0);
  renderEmpty();
  message('대화 기록이 초기화됐어요');
}

/** WebSocket 으로 받은 새 메시지 */
export function receiveChat(chatMessage) {
  if (!roomId) return;
  const isNew = !seen.has(chatMessage.id);
  append(chatMessage);
  if (isNew && collapsed() && !chatMessage.mine) setUnread(unread + 1);
}
