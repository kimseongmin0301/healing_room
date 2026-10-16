// 방 WebSocket 연결. 연결돼 있는 동안 "방에 있는 사람"으로 세어지고,
// 서버가 음악 상태(state), 채팅(chat), 접속자 목록(presence), 내보내짐(kicked), 방 닫힘(room-closed),
// 방 꽉 참(room-full), 방 설정 바뀜(room-updated)을 보내준다. 닉네임을 바꾸면 서버에 알린다.
import { visitorId } from './api.js';

// 서버가 일부러 끊은 연결: 4001 내보내짐, 4002 방이 닫힘, 4003 방이 꽉 참 (다시 연결하지 않는다)
const FINAL_CLOSE = new Set([4001, 4002, 4003]);

export function connectRoom(roomId, { nickname, onState, onChat, onChatClear, onPresence, onStatus, onKicked, onRoomGone, onRoomUpdated }) {
  let ws = null;
  let closed = false;
  let retry = 0;
  let pingTimer = null;

  function open() {
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
    const name = encodeURIComponent(nickname?.() ?? '');
    ws = new WebSocket(`${scheme}://${location.host}/ws/rooms/${encodeURIComponent(roomId)}?visitorId=${visitorId}&nickname=${name}`);
    ws.onopen = () => {
      retry = 0;
      onStatus?.('open');
      // 중간 장비가 조용한 연결을 끊지 않도록 가끔 신호를 보낸다
      pingTimer = setInterval(() => ws.readyState === WebSocket.OPEN && ws.send('ping'), 25000);
    };
    ws.onmessage = e => {
      const message = JSON.parse(e.data);
      if (message.type === 'state') onState(message);
      else if (message.type === 'chat') onChat?.(message.message);
      else if (message.type === 'presence') onPresence?.(message);
      else if (message.type === 'chat-clear') onChatClear?.();
      else if (message.type === 'kicked') { closed = true; onKicked?.(message); }
      else if (message.type === 'room-closed' || message.type === 'room-full') { closed = true; onRoomGone?.(message); }
      else if (message.type === 'room-updated') onRoomUpdated?.();
    };
    ws.onclose = e => {
      clearInterval(pingTimer);
      if (closed || FINAL_CLOSE.has(e.code)) return;
      onStatus?.('reconnecting');
      setTimeout(open, Math.min(10000, 1000 * 2 ** retry++));
    };
  }

  open();
  return {
    close() { closed = true; clearInterval(pingTimer); ws?.close(); },
    /** 닉네임이 바뀌면 접속자 목록에 반영되도록 서버에 알린다 */
    sendNickname(name) {
      if (ws?.readyState === WebSocket.OPEN) ws.send(JSON.stringify({ type: 'nickname', nickname: name }));
    },
  };
}
