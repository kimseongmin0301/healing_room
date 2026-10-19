// 환경음 믹서: 방의 환경음 목록(DB)으로 슬라이더를 만들고, 소리를 켜고 끈다. 볼륨은 브라우저에 저장한다.
import { store } from '../api.js';
import { SOUNDS } from './sounds.js';

const list = document.getElementById('mixer-list');
let ctx = null;
let master = null;
let roomId = null;
let channels = [];   // { sound, gain, voice }
let enabled = false;
let onTouch = null;   // 슬라이더를 움직였을 때 (app.js: 꺼져 있거나 쉬는 중이면 켠다)

/** 사용자가 슬라이더를 움직이면 부를 함수 */
export function onMixerInput(fn) {
  onTouch = fn;
}

const key = sound => `volume:${roomId}:${sound}`;
// 사람 귀는 소리 크기를 로그처럼 느끼므로 제곱해서 슬라이더 체감을 고르게 한다
const level = percent => (percent / 100) ** 2;

function ensureContext() {
  if (ctx) return ctx;
  ctx = new (window.AudioContext || window.webkitAudioContext)();
  master = ctx.createGain();
  master.gain.value = 0.9;
  master.connect(ctx.destination);
  return ctx;
}

function startVoices() {
  for (const ch of channels) {
    if (!ch.voice && SOUNDS[ch.sound]) ch.voice = SOUNDS[ch.sound](ctx, ch.gain);
  }
}

function stopVoices() {
  for (const ch of channels) { ch.voice?.stop(); ch.voice = null; }
}

/** 방에 들어갈 때: 슬라이더를 만든다 (소리는 setAmbientEnabled(true) 이후에 난다) */
export function enterMixer(id, ambients) {
  leaveMixer();
  roomId = id;
  list.replaceChildren();
  if (enabled) ensureContext();

  for (const { sound, label, defaultVolume } of ambients) {
    const value = store.get(key(sound), defaultVolume);
    const row = document.createElement('label');
    row.className = 'mix-row';
    const name = Object.assign(document.createElement('span'), { textContent: label });
    const out = Object.assign(document.createElement('output'), { textContent: value });
    const input = Object.assign(document.createElement('input'), { type: 'range', min: 0, max: 100, step: 1, value });
    row.append(name, out, input);
    list.append(row);

    const ch = { sound, value, gain: null, voice: null };
    channels.push(ch);
    input.addEventListener('input', () => {
      ch.value = Number(input.value);
      out.textContent = ch.value;
      store.set(key(sound), ch.value);
      if (ch.gain) ch.gain.gain.setTargetAtTime(level(ch.value), ctx.currentTime, 0.05);
      onTouch?.();
    });
  }
  if (enabled) connectChannels();
}

function connectChannels() {
  for (const ch of channels) {
    if (!ch.gain) {
      ch.gain = ctx.createGain();
      ch.gain.gain.value = level(ch.value);
      ch.gain.connect(master);
    }
  }
  startVoices();
}

/** 방을 나갈 때 */
export function leaveMixer() {
  stopVoices();
  for (const ch of channels) ch.gain?.disconnect();
  channels = [];
}

/** "소리 켜기/끄기". 브라우저 정책상 사용자가 클릭한 뒤에만 소리를 낼 수 있다 */
export async function setAmbientEnabled(value) {
  enabled = value;
  if (value) {
    ensureContext();
    await ctx.resume();
    connectChannels();
  } else if (ctx) {
    stopVoices();
  }
}
