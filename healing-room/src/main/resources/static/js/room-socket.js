// 방 WebSocket 연결. 연결돼 있는 동안 "방에 있는 사람"으로 세어지고, 서버가 음악 상태를 보내준다.
import { visitorId } from './api.js';

export function connectRoom(roomId, { onState, onStatus }) {
  let ws = null;
  let closed = false;
  let retry = 0;
  let pingTimer = null;

  function open() {
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
    ws = new WebSocket(`${scheme}://${location.host}/ws/rooms/${encodeURIComponent(roomId)}?visitorId=${visitorId}`);
    ws.onopen = () => {
      retry = 0;
      onStatus?.('open');
      // 중간 장비가 조용한 연결을 끊지 않도록 가끔 신호를 보낸다
      pingTimer = setInterval(() => ws.readyState === WebSocket.OPEN && ws.send('ping'), 25000);
    };
    ws.onmessage = e => {
      const message = JSON.parse(e.data);
      if (message.type === 'state') onState(message);
    };
    ws.onclose = () => {
      clearInterval(pingTimer);
      if (closed) return;
      onStatus?.('reconnecting');
      setTimeout(open, Math.min(10000, 1000 * 2 ** retry++));
    };
  }

  open();
  return {
    close() { closed = true; clearInterval(pingTimer); ws?.close(); },
  };
}
