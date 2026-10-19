// Google Analytics(GA4) 이벤트. 태그(gtag.js)는 index.html 에서 불러오고, 페이지 조회는 GA 의 "향상된 측정"이
// 주소 바뀜(history API)까지 알아서 센다. 여기서는 그 밖에 보고 싶은 행동만 보낸다.
// 방문자 id·닉네임·채팅 내용·곡 제목처럼 사람을 알아볼 수 있거나 사용자가 쓴 글은 보내지 않는다.

/** 이벤트 보내기. 광고 차단 등으로 GA 가 없으면 아무것도 안 한다 */
export function track(name, params = {}) {
  try {
    window.gtag?.('event', name, params);
  } catch {
    // 분석이 안 돼도 서비스는 그대로
  }
}
