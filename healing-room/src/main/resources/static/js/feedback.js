// 로비 의견 보내기: 익명으로 /api/feedback 에 보낸다 (관리자 화면에서만 읽음)
import { api } from './api.js';
import { track } from './analytics.js';

const $ = id => document.getElementById(id);

export function initFeedback() {
  const text = $('feedback-text');
  const count = () => { $('feedback-count').textContent = `${text.value.length} / 1000`; };
  const message = (value, isError = false) => {
    $('feedback-msg').textContent = value;
    $('feedback-msg').classList.toggle('error', isError);
  };
  text.addEventListener('input', () => { count(); message(''); });

  $('feedback-form').addEventListener('submit', async e => {
    e.preventDefault();
    const value = text.value.trim();
    if (!value) { message('의견을 적어 주세요', true); text.focus(); return; }
    e.submitter.disabled = true;
    try {
      await api('POST', '/api/feedback', { message: value });
      track('feedback_send');
      text.value = '';
      count();
      message('보내 주셔서 고마워요! 꼭 읽어 볼게요 🙏');
    } catch (err) {
      message(err.message, true);
    } finally {
      e.submitter.disabled = false;
    }
  });
}
