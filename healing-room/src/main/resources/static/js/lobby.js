// 로비: 방 카드 목록과 방마다 지금 있는 사람 수
import { api } from './api.js';

const grid = document.getElementById('room-grid');
let timer = null;

function card(room, onEnter) {
  const button = document.createElement('button');
  button.className = 'room-card';
  button.style.backgroundImage = `url("${room.background}")`;
  button.innerHTML = `
    <span class="count tag"></span>
    <span class="info"><h2></h2><p></p></span>`;
  button.querySelector('h2').textContent = room.title;
  button.querySelector('p').textContent = room.subtitle;
  button.querySelector('.count').textContent =
    room.listeners > 0 ? `${room.listeners}명이 쉬는 중` : '비어 있어요';
  button.onclick = () => onEnter(room.id);
  return button;
}

async function refresh(onEnter) {
  try {
    const rooms = await api('GET', '/api/rooms');
    grid.replaceChildren(...rooms.map(room => card(room, onEnter)));
  } catch (e) {
    grid.textContent = '방 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.';
  }
}

export function showLobby(onEnter) {
  refresh(onEnter);
  clearInterval(timer);
  timer = setInterval(() => refresh(onEnter), 10000);
}

export function hideLobby() {
  clearInterval(timer);
}
