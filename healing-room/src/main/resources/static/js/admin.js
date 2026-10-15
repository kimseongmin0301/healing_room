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

function render({ totalPeople, rooms }) {
  $('status').textContent = `총 ${totalPeople}명 · ${new Date().toLocaleTimeString('ko-KR')} 기준`;
  $('rooms').replaceChildren(...rooms.map(room => {
    const section = document.createElement('section');
    const h2 = document.createElement('h2');
    h2.textContent = room.name;
    h2.append(Object.assign(document.createElement('small'), { textContent: `#${room.id} · ${room.count}명` }));
    const table = document.createElement('table');
    table.innerHTML = '<thead><tr><th>닉네임</th><th>IP</th><th>방문자</th><th>들어온 시각</th><th>탭</th></tr></thead>';
    const body = document.createElement('tbody');
    for (const p of room.people) {
      const tr = document.createElement('tr');
      tr.append(cell(`${p.host ? '👑 ' : ''}${p.nickname}`), cell(p.ip ?? '-', 'mono'), cell(p.visitor, 'mono'),
        cell(timeText(p.joinedAt)), cell(String(p.tabs)));
      body.append(tr);
    }
    table.append(body);
    section.append(h2, table);
    return section;
  }));
  if (!rooms.length) $('rooms').textContent = '지금 접속해 있는 사람이 없어요.';
}

async function load() {
  const token = getToken();
  if (!token) return;
  try {
    const res = await fetch('/api/admin/presence', { headers: { 'X-Admin-Token': token } });
    const data = await res.json().catch(() => null);
    if (!res.ok) throw new Error(data?.message ?? `불러오지 못했어요 (${res.status})`);
    render(data);
  } catch (e) {
    $('status').textContent = e.message;
    $('rooms').replaceChildren();
    clearInterval(timer);
  }
}

function start() {
  clearInterval(timer);
  load();
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
  $('status').textContent = '키를 지웠어요.';
});

if (getToken()) start();
