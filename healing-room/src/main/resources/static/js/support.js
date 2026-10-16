// 서버비 후원: 위쪽 ☕ 버튼을 누르면 카카오페이 QR 창을 연다 (바깥이나 Esc 로 닫기)
const $ = id => document.getElementById(id);

// 휴대폰·태블릿이면 true (카카오페이 송금 링크는 휴대폰 앱에서만 열린다)
const isMobile = () => /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent)
  || (navigator.maxTouchPoints > 1 && /Macintosh/.test(navigator.userAgent)); // 데스크톱 모드 iPad

export function initSupport() {
  const dialog = $('support-dialog');
  if (isMobile()) {
    $('support-link').hidden = false;
    $('support-how').textContent = '아래 버튼을 누르면 카카오페이가 열려요';
  }
  const close = () => { dialog.hidden = true; };
  $('support-open').addEventListener('click', () => { dialog.hidden = false; });
  $('support-close').addEventListener('click', close);
  dialog.addEventListener('click', e => { if (e.target === dialog) close(); });
  addEventListener('keydown', e => { if (e.key === 'Escape' && !dialog.hidden) close(); });
}
