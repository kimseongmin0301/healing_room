// 현재 방에 "있다"는 신호를 서버로 주기적으로 보내고, 함께 있는 인원을 받아 표시한다.
const HEARTBEAT_MS = 15000;

function loadVisitorId() {
  try {
    let id = localStorage.getItem('visitorId');
    if (!id) { id = crypto.randomUUID(); localStorage.setItem('visitorId', id); }
    return id;
  } catch {
    return crypto.randomUUID(); // 저장소를 못 쓰는 환경이면 이번 접속 동안만 사용
  }
}

const visitorId = loadVisitorId();
let roomId = null;
let timer = null;

const url = id => `/api/rooms/${encodeURIComponent(id)}/presence/${visitorId}`;

async function beat(onCount) {
  if (!roomId) return;
  const current = roomId;
  try {
    const res = await fetch(url(current), { method: 'PUT' });
    if (res.ok && current === roomId) onCount((await res.json()).count);
  } catch {
    // 일시적인 네트워크 오류는 무시하고 다음 신호에서 다시 시도
  }
}

function leave() {
  if (roomId) fetch(url(roomId), { method: 'DELETE', keepalive: true }).catch(() => {});
}

/** 방에 들어갈 때 호출. onCount(인원) 콜백으로 인원 변화를 알려준다. */
export function joinRoom(id, onCount) {
  if (id === roomId) return;
  leave();
  roomId = id;
  clearInterval(timer);
  beat(onCount);
  timer = setInterval(() => beat(onCount), HEARTBEAT_MS);
}

addEventListener('pagehide', leave);
