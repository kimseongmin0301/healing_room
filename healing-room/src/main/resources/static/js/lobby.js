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

// 한 쪽에 보여줄 카드 수: 넓으면 6(3×2), 중간 4(2×2), 휴대폰 2(1×2). CSS 의 .room-page 열 수와 맞춘다
const pageSize = () => innerWidth > 960 ? 6 : innerWidth > 600 ? 4 : 2;
let lastRooms = [];

function currentPage() {
  return grid.clientWidth ? Math.round(grid.scrollLeft / grid.clientWidth) : 0;
}

function renderRooms(rooms) {
  const page = currentPage();
  const size = pageSize();
  const pages = [];
  for (let i = 0; i < rooms.length; i += size) {
    const div = Object.assign(document.createElement('div'), { className: 'room-page' });
    div.append(...rooms.slice(i, i + size).map(card));
    pages.push(div);
  }
  grid.replaceChildren(...pages);
  // 목록을 새로 그려도 보던 쪽에 머문다
  grid.style.scrollBehavior = 'auto';
  grid.scrollLeft = Math.min(page, pages.length - 1) * grid.clientWidth;
  grid.style.scrollBehavior = '';
  updateSlider();
}

function updateSlider() {
  const count = grid.querySelectorAll('.room-page').length;
  const page = currentPage();
  $('rooms-prev').hidden = page <= 0;
  $('rooms-next').hidden = page >= count - 1;
  $('room-dots').replaceChildren(...(count > 1 ? Array.from({ length: count }, (_, i) =>
    Object.assign(document.createElement('span'), { className: i === page ? 'on' : '' })) : []));
}

let targetPage = null; // 넘기는 중인 쪽 (빠르게 여러 번 눌러도 한 쪽씩 정확히 넘어가게)

function slide(step) {
  const count = grid.querySelectorAll('.room-page').length;
  targetPage = Math.max(0, Math.min(count - 1, (targetPage ?? currentPage()) + step));
  grid.scrollTo({ left: targetPage * grid.clientWidth, behavior: 'smooth' });
}

async function refresh() {
  try {
    lastRooms = await api('GET', '/api/rooms');
    renderRooms(lastRooms);
  } catch {
    grid.textContent = '방 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.';
    updateSlider();
  }
}

$('rooms-prev').addEventListener('click', () => slide(-1));
$('rooms-next').addEventListener('click', () => slide(1));
grid.addEventListener('scroll', () => requestAnimationFrame(() => {
  if (targetPage !== null && Math.abs(grid.scrollLeft - targetPage * grid.clientWidth) < 2) targetPage = null;
  updateSlider();
}), { passive: true });
grid.addEventListener('pointerdown', () => { targetPage = null; }); // 손으로 넘기기 시작하면 맡긴다
let lastSize = pageSize();
addEventListener('resize', () => {
  if (pageSize() !== lastSize) { lastSize = pageSize(); renderRooms(lastRooms); } else updateSlider();
});

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
    // 넓은 화면은 "카페멍 · 비 오는 창가", 휴대폰은 "카페멍" (CSS 로 둘 중 하나만 보임)
    const label = document.createElement('span');
    label.append(Object.assign(document.createElement('b'), { className: 'long', textContent: theme.title }),
      Object.assign(document.createElement('b'), { className: 'short', textContent: theme.name }));
    label.title = theme.title;
    option.append(label);
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
