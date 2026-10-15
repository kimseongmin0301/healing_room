// 함께 있는 사람 목록: 방 이름 아래 "👥 함께 있는 사람" 버튼을 누르면 펼쳐진다. IP 는 서버가 앞부분만 보내준다
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

/** 서버가 보낸 접속자 목록 (type=presence) */
export function renderPeople({ count, people }) {
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
