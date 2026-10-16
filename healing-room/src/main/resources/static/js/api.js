// 서버 API 호출과 방문자 id. 로그인이 없으므로 브라우저마다 UUID 하나를 만들어 계속 쓴다.

/**
 * UUID v4 만들기. crypto.randomUUID() 는 HTTPS 나 localhost 에서만 쓸 수 있어서
 * http://IP주소 로 접속하면 오류가 난다. getRandomValues 는 어디서나 되므로 직접 만든다.
 */
function uuid() {
  if (window.isSecureContext && crypto.randomUUID) return crypto.randomUUID();
  const b = crypto.getRandomValues(new Uint8Array(16));
  b[6] = (b[6] & 0x0f) | 0x40; // 버전 4
  b[8] = (b[8] & 0x3f) | 0x80; // RFC 4122 변형
  const h = [...b].map(x => x.toString(16).padStart(2, '0')).join('');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

function loadVisitorId() {
  try {
    let id = localStorage.getItem('visitorId');
    if (!id) { id = uuid(); localStorage.setItem('visitorId', id); }
    return id;
  } catch {
    return uuid(); // 저장소를 못 쓰는 환경이면 이번 접속 동안만 사용
  }
}

// let 으로 내보내면 다른 모듈도 바뀐 값을 그대로 본다 (syncVisitor 뒤에 쿠키 기준 id 로 바뀔 수 있음)
export let visitorId = loadVisitorId();

/**
 * 시작할 때 한 번: 서버가 쿠키(hr_vid)에 남겨 둔 방문자 id 와 맞춘다.
 * 쿠키가 있으면 그 id 를 쓰고(저장소를 지우거나 바꿔도 같은 사람), 없으면 지금 id 를 쿠키에 남긴다.
 */
export async function syncVisitor() {
  try {
    const res = await fetch('/api/visitor', { headers: { 'X-Visitor-Id': visitorId } });
    const { id } = await res.json();
    if (id && id !== visitorId) {
      visitorId = id;
      try { localStorage.setItem('visitorId', id); } catch { /* 저장소를 못 쓰면 이번 접속 동안만 */ }
    }
  } catch {
    // 서버에 닿지 않으면 저장소의 id 를 그대로 쓴다
  }
}

/** JSON API 호출. 실패하면 서버가 준 message 로 Error 를 던진다. */
export async function api(method, url, body) {
  const res = await fetch(url, {
    method,
    headers: { 'X-Visitor-Id': visitorId, ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) throw new Error(data?.message ?? `요청에 실패했어요 (${res.status})`);
  return data;
}

/** 브라우저 저장소 읽기/쓰기 (시크릿 모드 등에서 실패해도 무시) */
export const store = {
  get(key, fallback) {
    try { const v = localStorage.getItem(key); return v === null ? fallback : JSON.parse(v); } catch { return fallback; }
  },
  set(key, value) {
    try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* 무시 */ }
  },
};
