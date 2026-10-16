// 함께 있는 사람 목록: 방 이름 아래 "👥 함께 있는 사람" 버튼을 누르면 펼쳐진다. IP 는 서버가 앞부분만 보내준다
// 방장에게는 다른 사람 옆에 "내보내기" 버튼이 보인다 (사람은 방문자 id 가 아닌 가린 key 로 가리킨다)
import { api } from './api.js';

const $ = id => document.getElementById(id);

function since(ms) {
  const minutes = Math.max(0, Math.round((Date.now() - ms) / 60000));
  if (minutes < 1) return '방금';
  if (minutes < 60) return `${minutes}분`;
  return `${Math.floor(minutes / 60)}시간`;
}

function toggle(open = $('people').hidden) {
  $('people').hidden = !open;
  $('presence-toggle').setAttribute('aria-expanded', String(open));
}

$('presence-toggle').addEventListener('click', () => toggle());

async function kick(roomId, person, button) {
  if (!confirm(`'${person.nickname}'님을 이 방에서 내보낼까요?
10분 동안 이 방에 다시 들어올 수 없어요.`)) return;
  button.disabled = true;
  try {
    await api('POST', `/api/rooms/${encodeURIComponent(roomId)}/kick`, { key: person.key });
  } catch (e) {
    alert(e.message);
    button.disabled = false;
  }
}

/** 서버가 보낸 접속자 목록 (type=presence) */
export function renderPeople({ count, people }, roomId) {
  const iAmHost = people.some(p => p.me && p.host);
  $('people-count').textContent = count;
  $('people-chip-count').textContent = count;
  $('people-list').replaceChildren(...people.map(person => {
    const li = document.createElement('li');
    const name = Object.assign(document.createElement('span'), {
      className: 'name',
      textContent: `${person.host ? '👑 ' : ''}${person.nickname}${person.me ? ' (나)' : ''}`,
      title: person.host ? '방장' : '',
    });
    const ip = Object.assign(document.createElement('span'), { className: 'ip', textContent: person.ip });
    const time = Object.assign(document.createElement('span'), { className: 'since', textContent: since(person.joinedAt) });
    li.append(name, ip, time);
    if (iAmHost && !person.me) {
      const button = Object.assign(document.createElement('button'), { className: 'kick', type: 'button', textContent: '내보내기' });
      button.onclick = () => kick(roomId, person, button);
      li.append(button);
    }
    return li;
  }));
}

/** 방을 옮길 때 목록을 비우고 접는다 */
export function resetPeople() {
  $('people-list').replaceChildren();
  $('people-count').textContent = '0';
  $('people-chip-count').textContent = '0';
  toggle(false);
}
