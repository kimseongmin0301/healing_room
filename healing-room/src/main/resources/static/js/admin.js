// 관리자 화면: 키(ADMIN_TOKEN)로 /api/admin/presence 를 불러와 5초마다 갱신한다.
// 키는 이 탭이 열려 있는 동안만 sessionStorage 에 둔다 (탭을 닫으면 사라짐)
const $ = id => document.getElementById(id);
const KEY = 'adminToken';
let timer = null;

const getToken = () => { try { return sessionStorage.getItem(KEY) ?? ''; } catch { return ''; } };
const setToken = value => { try { value ? sessionStorage.setItem(KEY, value) : sessionStorage.removeItem(KEY); } catch { /* 무시 */ } };

const timeText = ms => new Date(ms).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });

function cell(text, className = '') {
  return Object.assign(document.createElement('td'), { textContent: text, className });
}

async function reset(roomId) {
  const what = roomId ? `'${roomId}' 방의` : '모든 방의';
  if (!confirm(`${what} 지난 신청곡, 투표, 채팅을 모두 지울까요? 되돌릴 수 없어요.`)) return;
  const url = '/api/admin/reset' + (roomId ? `?roomId=${encodeURIComponent(roomId)}` : '');
  const res = await fetch(url, { method: 'POST', headers: { 'X-Admin-Token': getToken() } });
  const data = await res.json().catch(() => null);
  $('status').textContent = res.ok
    ? `초기화했어요: 곡 기록 ${data.deletedSongs}개${data.deletedChatRooms !== undefined ? `, 채팅 ${data.deletedChatRooms}개 방` : ''}`
    : (data?.message ?? `초기화하지 못했어요 (${res.status})`);
  load();
}

async function kick(roomId, person) {
  if (!confirm(`'${person.nickname}'님을 '${roomId}' 방에서 내보낼까요? 10분 동안 다시 못 들어와요.`)) return;
  const url = `/api/admin/kick?roomId=${encodeURIComponent(roomId)}&key=${encodeURIComponent(person.key)}`;
  const res = await fetch(url, { method: 'POST', headers: { 'X-Admin-Token': getToken() } });
  $('status').textContent = res.ok ? `'${person.nickname}'님을 내보냈어요.`
    : ((await res.json().catch(() => null))?.message ?? `내보내지 못했어요 (${res.status})`);
  load();
}

function render({ totalPeople, rooms }) {
  $('status').textContent = `총 ${totalPeople}명 · ${new Date().toLocaleTimeString('ko-KR')} 기준`;
  $('rooms').replaceChildren(...rooms.map(room => {
    const section = document.createElement('section');
    const h2 = document.createElement('h2');
    h2.textContent = room.name;
    h2.append(Object.assign(document.createElement('small'), { textContent: `#${room.id} · ${room.count}명` }));
    const resetButton = Object.assign(document.createElement('button'), { className: 'btn', type: 'button', textContent: '이 방 초기화' });
    resetButton.style.marginLeft = '10px';
    resetButton.onclick = () => reset(room.id);
    h2.append(resetButton);
    const table = document.createElement('table');
    table.innerHTML = '<thead><tr><th>닉네임</th><th>IP</th><th>방문자</th><th>들어온 시각</th><th>탭</th><th></th></tr></thead>';
    const body = document.createElement('tbody');
    for (const p of room.people) {
      const tr = document.createElement('tr');
      // 내보내기: 10분 동안 이 방에 다시 못 들어온다
      const kickButton = Object.assign(document.createElement('button'), { className: 'btn', type: 'button', textContent: '내보내기' });
      kickButton.onclick = () => kick(room.id, p);
      const action = document.createElement('td');
      action.append(kickButton);
      tr.append(cell(`${p.host ? '👑 ' : ''}${p.nickname}`), cell(p.ip ?? '-', 'mono'), cell(p.visitor, 'mono'),
        cell(timeText(p.joinedAt)), cell(String(p.tabs)), action);
      body.append(tr);
    }
    table.append(body);
    section.append(h2, table);
    return section;
  }));
  if (!rooms.length) $('rooms').textContent = '지금 접속해 있는 사람이 없어요.';
}

const dateText = value => new Date(value).toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' });

// 받은 의견: 글은 textContent 로만 넣는다 (HTML 로 해석하지 않음)
function renderFeedback(list) {
  $('feedback-total').textContent = `${list.length}개`;
  if (!list.length) { $('feedback').textContent = '아직 받은 의견이 없어요.'; return; }
  const table = document.createElement('table');
  table.innerHTML = '<thead><tr><th>받은 시각</th><th>내용</th><th>보낸 사람</th><th></th></tr></thead>';
  const body = document.createElement('tbody');
  for (const f of list) {
    const tr = document.createElement('tr');
    const remove = Object.assign(document.createElement('button'), { className: 'btn', type: 'button', textContent: '지우기' });
    remove.onclick = async () => {
      if (!confirm('이 의견을 지울까요?')) return;
      const res = await fetch(`/api/admin/feedback/${f.id}`, { method: 'DELETE', headers: { 'X-Admin-Token': getToken() } });
      if (!res.ok) $('status').textContent = (await res.json().catch(() => null))?.message ?? `지우지 못했어요 (${res.status})`;
      load();
    };
    const action = document.createElement('td');
    action.append(remove);
    tr.append(cell(dateText(f.createdAt)), cell(f.message, 'text'), cell(f.visitor, 'mono'), action);
    body.append(tr);
  }
  table.append(body);
  $('feedback').replaceChildren(table);
}

async function load() {
  const token = getToken();
  if (!token) return;
  try {
    const headers = { 'X-Admin-Token': token };
    const [res, feedbackRes] = await Promise.all([
      fetch('/api/admin/presence', { headers }), fetch('/api/admin/feedback', { headers })]);
    const data = await res.json().catch(() => null);
    if (res.status === 401 && data?.message?.includes('로그인')) { location.reload(); return; } // 로그인이 풀리면 로그인 화면으로
    if (!res.ok) throw new Error(data?.message ?? `불러오지 못했어요 (${res.status})`);
    render(data);
    if (feedbackRes.ok) renderFeedback(await feedbackRes.json());
  } catch (e) {
    $('status').textContent = e.message;
    $('rooms').replaceChildren();
    clearInterval(timer);
  }
}

// ---------------------------------------------------------------- 방 관리
// 5초 갱신에 넣지 않는다 (고치고 있던 칸이 갱신으로 지워지지 않게). 저장·없애기 뒤와 "새로고침"에서만 다시 불러온다

const MAX_CHOICES = [2, 3, 4, 5, 6, 8, 10, 15, 20, 30, 50]; // 방 만들기 창과 같은 선택지
let themes = [];

function button(text, onClick) {
  const b = Object.assign(document.createElement('button'), { className: 'btn', type: 'button', textContent: text });
  b.onclick = onClick;
  return b;
}

async function adminRequest(method, url, body) {
  const res = await fetch(url, {
    method,
    headers: { 'X-Admin-Token': getToken(), ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new Error(data?.message ?? `처리하지 못했어요 (${res.status})`);
  return data;
}

async function loadRooms() {
  if (!getToken()) return;
  try {
    if (!themes.length) themes = await fetch('/api/themes').then(r => r.json());
    renderRoomAdmin(await adminRequest('GET', '/api/admin/rooms'));
  } catch (e) {
    $('room-admin').textContent = e.message;
  }
}

async function saveRoom(room, settings) {
  try {
    await adminRequest('PUT', `/api/admin/rooms/${encodeURIComponent(room.id)}/settings`, settings);
    $('status').textContent = `'${settings.name}' 방 설정을 바꿨어요.`;
  } catch (e) {
    $('status').textContent = e.message;
  }
  loadRooms();
}

async function deleteRoom(room) {
  const people = room.listeners ? ` 지금 ${room.listeners}명이 있고, 모두 처음 화면으로 나가요.` : '';
  if (!confirm(`'${room.name}' 방을 없앨까요?${people} 채팅도 지워지고 되돌릴 수 없어요.`)) return;
  try {
    await adminRequest('DELETE', `/api/admin/rooms/${encodeURIComponent(room.id)}`);
    $('status').textContent = `'${room.name}' 방을 없앴어요.`;
  } catch (e) {
    $('status').textContent = e.message;
  }
  loadRooms();
  load();
}

function renderRoomAdmin(rooms) {
  $('room-total').textContent = `${rooms.length}개`;
  const table = document.createElement('table');
  table.className = 'room-table';
  table.innerHTML = '<thead><tr><th>주소</th><th>이름</th><th>테마</th><th>최대 인원</th><th>지금</th><th></th></tr></thead>';
  const body = document.createElement('tbody');
  for (const room of rooms) {
    const link = Object.assign(document.createElement('a'), { href: `/${room.id}`, target: '_blank', textContent: `/${room.id}` });
    const where = document.createElement('td');
    where.className = 'mono';
    where.append(link, room.isDefault ? ' (기본 방)' : '');

    const name = Object.assign(document.createElement('input'), { value: room.name, maxLength: 30 });
    const theme = document.createElement('select');
    for (const t of themes) theme.append(new Option(t.title ?? t.name, t.id, false, t.id === room.theme.id));
    theme.disabled = room.isDefault; // 기본 방의 테마는 고정
    const max = document.createElement('select');
    max.append(new Option('제한 없음', ''));
    const choices = [...new Set([...MAX_CHOICES, ...(room.maxPeople ? [room.maxPeople] : [])])].sort((a, b) => a - b);
    for (const n of choices) max.append(new Option(`${n}명`, String(n), false, n === room.maxPeople));

    const actions = document.createElement('td');
    actions.className = 'actions';
    actions.append(button('저장', () => saveRoom(room, {
      name: name.value.trim(), themeId: theme.value, maxPeople: max.value ? Number(max.value) : null,
    })));
    if (!room.isDefault) actions.append(button('없애기', () => deleteRoom(room)));

    const cellOf = el => { const td = document.createElement('td'); td.append(el); return td; };
    const tr = document.createElement('tr');
    tr.append(where, cellOf(name), cellOf(theme), cellOf(max),
      cell(room.maxPeople ? `${room.listeners}/${room.maxPeople}명` : `${room.listeners}명`), actions);
    body.append(tr);
  }
  table.append(body);
  $('room-admin').replaceChildren(table);
}

$('room-reload').addEventListener('click', loadRooms);

function start() {
  clearInterval(timer);
  load();
  loadRooms();
  timer = setInterval(load, 5000);
}

$('login').addEventListener('submit', e => {
  e.preventDefault();
  setToken($('token').value.trim());
  $('token').value = '';
  start();
});
$('logout').addEventListener('click', () => {
  setToken('');
  clearInterval(timer);
  $('rooms').replaceChildren();
  $('feedback').replaceChildren();
  $('feedback-total').textContent = '';
  $('room-admin').replaceChildren();
  $('room-total').textContent = '';
  $('status').textContent = '키를 지웠어요.';
});

// 로그아웃: 로그인 쿠키를 지우고 키도 지운 뒤 로그인 화면으로
$('signout').addEventListener('click', async () => {
  setToken('');
  await fetch('/api/admin/logout', { method: 'POST' }).catch(() => {});
  location.reload();
});

$('reset-all').addEventListener('click', () => {
  if (!getToken()) { $('status').textContent = '먼저 관리자 키를 넣어 주세요.'; return; }
  reset(null);
});

if (getToken()) start();
