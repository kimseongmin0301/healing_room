// 로비: 열려 있는 방 목록(방마다 지금 있는 사람 수)과 방 만들기
import { api } from './api.js';

const $ = id => document.getElementById(id);
const grid = $('room-grid');
let timer = null;
let enterRoom = null;   // app.js 가 넘겨준 "방에 들어가기" 함수
let themes = [];
let pickedTheme = null;

// ---------------------------------------------------------------- 방 목록

function card(room) {
  const button = document.createElement('button');
  button.className = 'room-card';
  button.style.backgroundImage = `url("${room.theme.background}")`;
  button.innerHTML = `
    <span class="badges"></span>
    <span class="info"><span class="theme-name"></span><h2></h2><p></p></span>`;
  const badges = button.querySelector('.badges');
  const count = Object.assign(document.createElement('span'), {
    className: 'tag', textContent: room.listeners > 0 ? `${room.listeners}명이 쉬는 중` : '비어 있어요',
  });
  badges.append(count);
  if (room.isDefault) badges.append(Object.assign(document.createElement('span'), { className: 'tag', textContent: '기본 방' }));
  button.querySelector('.theme-name').textContent = room.theme.name;
  button.querySelector('h2').textContent = room.name;
  button.querySelector('p').textContent = room.theme.subtitle;
  button.onclick = () => enterRoom(room.id);
  return button;
}

async function refresh() {
  try {
    const rooms = await api('GET', '/api/rooms');
    grid.replaceChildren(...rooms.map(card));
  } catch {
    grid.textContent = '방 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.';
  }
}

// ---------------------------------------------------------------- 방 만들기

function renderThemes() {
  const picker = $('theme-picker');
  picker.replaceChildren(...themes.map(theme => {
    const option = document.createElement('button');
    option.type = 'button';
    option.className = `theme-option${theme.id === pickedTheme ? ' on' : ''}`;
    option.setAttribute('role', 'radio');
    option.setAttribute('aria-checked', String(theme.id === pickedTheme));
    option.style.backgroundImage = `url("${theme.background}")`;
    option.append(Object.assign(document.createElement('span'), { textContent: theme.title }));
    option.onclick = () => { pickedTheme = theme.id; renderThemes(); };
    return option;
  }));
}

async function openCreate() {
  $('create-msg').textContent = '';
  $('create-name').value = '';
  $('create-dialog').hidden = false;
  if (!themes.length) {
    try { themes = await api('GET', '/api/themes'); } catch { themes = []; }
  }
  pickedTheme ??= themes[0]?.id;
  renderThemes();
  $('create-name').focus();
}

function closeCreate() {
  $('create-dialog').hidden = true;
}

async function submitCreate(e) {
  e.preventDefault();
  const name = $('create-name').value.trim();
  if (!name) { $('create-msg').textContent = '방 이름을 지어 주세요.'; $('create-name').focus(); return; }
  if (!pickedTheme) { $('create-msg').textContent = '테마를 골라 주세요.'; return; }
  const button = e.submitter;
  button.disabled = true;
  try {
    const room = await api('POST', '/api/rooms', { name, themeId: pickedTheme });
    closeCreate();
    enterRoom(room.id);
  } catch (err) {
    $('create-msg').textContent = err.message;
  } finally {
    button.disabled = false;
  }
}

$('create-open').onclick = openCreate;
$('create-cancel').onclick = closeCreate;
$('create-form').addEventListener('submit', submitCreate);
$('create-name').addEventListener('input', () => { $('create-msg').textContent = ''; });
$('create-dialog').addEventListener('click', e => { if (e.target === $('create-dialog')) closeCreate(); });
addEventListener('keydown', e => { if (e.key === 'Escape' && !$('create-dialog').hidden) closeCreate(); });

// ---------------------------------------------------------------- 외부에서 쓰는 함수

/** @param onEnter 방 id 를 받아 들어가는 함수 (카드 클릭·방 만들기 직후 호출) */
export function showLobby(onEnter, message = '') {
  enterRoom = onEnter;
  $('lobby-msg').textContent = message;
  refresh();
  clearInterval(timer);
  timer = setInterval(refresh, 10000);
}

export function hideLobby() {
  clearInterval(timer);
  closeCreate();
}
