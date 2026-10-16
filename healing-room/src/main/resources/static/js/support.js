// 서버비 후원: 위쪽 ☕ 버튼을 누르면 카카오페이 QR 창을 연다 (바깥이나 Esc 로 닫기)
const $ = id => document.getElementById(id);

export function initSupport() {
  const dialog = $('support-dialog');
  const close = () => { dialog.hidden = true; };
  $('support-open').addEventListener('click', () => { dialog.hidden = false; });
  $('support-close').addEventListener('click', close);
  dialog.addEventListener('click', e => { if (e.target === dialog) close(); });
  addEventListener('keydown', e => { if (e.key === 'Escape' && !dialog.hidden) close(); });
}
