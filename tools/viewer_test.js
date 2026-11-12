// Runs app/src/main/assets/viewer.html's renderer script under Node with a mock DOM/canvas.
// This is an AUTOMATED test of the JavaScript logic only. It is NOT a WebView or device test.
const fs = require('fs'), vm = require('vm'), path = require('path');
const html = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/viewer.html'), 'utf8');
const script = html.match(/<script>([\s\S]*)<\/script>/)[1];

function make(opts = {}) {
  const stats = { draw: 0, fill: 0, transforms: [] };
  const messages = [], errors = [];
  const listeners = {}, winListeners = {};
  let rafCb = null;
  function mkCtx() {
    const target = {
      getImageData: (x, y, w, h) => ({ data: new Uint8ClampedArray(Math.max(1, w * h * 4)).fill(200), width: w, height: h }),
      putImageData() {}, createLinearGradient: () => ({ addColorStop() {} }), createRadialGradient: () => ({ addColorStop() {} }),
      drawImage() { stats.draw++; }, fill() { stats.fill++; }, fillRect() { stats.fill++; },
      setTransform(...a) { stats.transforms.push(a.join(',')); },
      measureText: () => ({ width: 10 }),
    };
    return new Proxy(target, { get: (t, k) => (k in t ? t[k] : () => {}), set: (t, k, v) => { t[k] = v; return true; } });
  }
  function mkCanvas() {
    const c = { width: 0, height: 0, style: {}, _l: {}, getContext: () => mkCtx(),
      addEventListener(n, f) { (c._l[n] = c._l[n] || []).push(f); }, toDataURL: () => 'data:image/png;base64,AAAA' };
    return c;
  }
  const canvas = mkCanvas();
  class Image { set src(v) { this._s = v; setTimeout(() => { if (v === 'bad') this.onerror && this.onerror(); else { this.width = 64; this.height = 64; this.onload && this.onload(); } }, 0); } get src() { return this._s; } }
  const win = {
    innerWidth: opts.w ?? 360, innerHeight: opts.h ?? 420, devicePixelRatio: 2.75,
    addEventListener(n, f) { (winListeners[n] = winListeners[n] || []).push(f); },
    requestAnimationFrame(cb) { rafCb = cb; }, onerror: null, setTimeout, Math, JSON, Object, Array, Image, console,
  };
  if (!opts.noBridge) win.OrbitBridge = { post(s) { const m = JSON.parse(s); messages.push(m); if (m.type === 'error') errors.push(m.message); } };
  win.window = win;
  const documentMock = { getElementById: () => canvas, createElement: () => mkCanvas() };
  const ctx = vm.createContext({ window: win, document: documentMock, Image, setTimeout, Math, JSON, Object, Array, console, Uint8ClampedArray, Float32Array, Number, String, Date, isFinite, parseInt, parseFloat, performance: { now: () => 0 } });
  vm.runInContext(script, ctx);
  let now = 0;
  const api = {
    stats, messages, errors, canvas, win,
    set: (s) => win.orbitSet(s),
    async settle() { await new Promise((r) => setTimeout(r, 15)); },
    frames(n = 6) {
      for (let i = 0; i < n; i++) {
        now += 100;
        const cb = rafCb; if (!cb) throw new Error('no rAF scheduled');
        try { cb(now); } catch (e) { errors.push('frame threw: ' + (e && e.message)); if (win.onerror) win.onerror(String(e)); }
      }
    },
    resizeTo(w, h) { win.innerWidth = w; win.innerHeight = h; (winListeners.resize || []).forEach((f) => f()); },
    touch(name, pts) { (canvas._l[name] || []).forEach((f) => f({ preventDefault() {}, touches: pts })); },
  };
  return api;
}

let failed = 0, checks = 0;
function check(c, msg) { checks++; if (!c) { failed++; console.log('FAIL: ' + msg); } }

// What the Android app (ViewerState.toJson) sends for a fresh install.
const KOTLIN_DEFAULT = { skin: null, slim: false, anim: 'idle', camera: 'free', cosmetics: [],
  cape: { enabled: true, id: 'orbit_nebula', tex: null, physics: true, stiffness: 0.5, sway: 0.5 },
  pet: { id: null, distance: 22, visible: true }, accent: '#a855f7', spin: false };
const IDS = ['hat_tophat','hat_crown','hat_wizard','halo_ring','halo_neon','head_headphones','head_antennae','head_visor','wings_angel','wings_bat','wings_neon','tail_fox','tail_dragon','tail_cat','back_backpack','back_jetpack','back_sword','shoulder_pads','shoulder_spikes','shoulder_parrot','other_scarf','other_orbit_ring','other_belt'];

(async () => {
  // 1. page load posts ready through OrbitBridge
  let v = make();
  check(v.messages.some((m) => m.type === 'ready'), 'ready message reaches OrbitBridge on load');
  // 2. nothing is drawn until the host sends a state (this is why a missed ready/initial-state handshake shows a blank view)
  v.frames(3); check(v.stats.draw + v.stats.fill === 0, 'nothing drawn before first state');
  // 3. fresh-install state draws a character with no cosmetics selected
  v.set(KOTLIN_DEFAULT); await v.settle(); v.frames(4);
  check(v.errors.length === 0, 'no errors with default state: ' + v.errors.join('|'));
  const base = v.stats.draw + v.stats.fill; check(base > 50, 'character draws many faces with default state (got ' + base + ')');
  // 4. empty state object also initializes
  v = make(); v.set({}); await v.settle(); v.frames(4);
  check(v.errors.length === 0 && v.stats.draw + v.stats.fill > 50, 'empty state {} initializes and draws');
  // 5. each cosmetic, each pet, each cape, each animation, each camera
  v = make(); v.set(KOTLIN_DEFAULT); await v.settle(); v.frames(2);
  for (const id of IDS) { const s = JSON.parse(JSON.stringify(KOTLIN_DEFAULT)); s.cosmetics = [id]; const b = v.stats.draw + v.stats.fill; v.set(s); await v.settle(); v.frames(3); check(v.stats.draw + v.stats.fill > b, 'draws after cosmetic ' + id); }
  { const s = JSON.parse(JSON.stringify(KOTLIN_DEFAULT)); s.cosmetics = IDS.slice(); v.set(s); await v.settle(); v.frames(3); }
  for (const p of ['pet_cat','pet_slime','pet_robot','pet_dragonling']) { const s = JSON.parse(JSON.stringify(KOTLIN_DEFAULT)); s.pet.id = p; v.set(s); await v.settle(); v.frames(3); }
  for (const c of ['orbit_nebula','orbit_ion','orbit_ember','orbit_aurora']) { const s = JSON.parse(JSON.stringify(KOTLIN_DEFAULT)); s.cape.id = c; v.set(s); await v.settle(); v.frames(3); }
  for (const a of ['idle','walk','run','wave']) { const s = JSON.parse(JSON.stringify(KOTLIN_DEFAULT)); s.anim = a; v.set(s); await v.settle(); v.frames(5); }
  for (const c of ['free','front','back','left','right','top','close']) { const s = JSON.parse(JSON.stringify(KOTLIN_DEFAULT)); s.camera = c; v.set(s); await v.settle(); v.frames(8); }
  { const s = JSON.parse(JSON.stringify(KOTLIN_DEFAULT)); s.cape.enabled = false; s.slim = true; v.set(s); await v.settle(); v.frames(3); }
  check(v.errors.length === 0, 'no errors across all cosmetics/pets/capes/anims/cameras: ' + v.errors.slice(0, 3).join('|'));
  // 6. skin as data URI and a bad skin falls back to the default skin
  { const s = JSON.parse(JSON.stringify(KOTLIN_DEFAULT)); s.skin = 'data:image/png;base64,AAAA'; v.set(s); await v.settle(); v.frames(3); }
  { const s = JSON.parse(JSON.stringify(KOTLIN_DEFAULT)); s.skin = 'bad'; const b = v.stats.draw + v.stats.fill; v.set(s); await v.settle(); v.frames(3); check(v.stats.draw + v.stats.fill > b, 'bad skin falls back and still draws'); }
  check(v.errors.length === 0, 'no errors with skins: ' + v.errors.join('|'));
  // 7. WebView that starts with 0x0 size, then gets resized (and rotated)
  v = make({ w: 0, h: 0 }); v.set(KOTLIN_DEFAULT); await v.settle(); v.frames(3);
  check(v.canvas.width > 0 && v.canvas.height > 0, 'zero-size start falls back to a usable canvas');
  v.resizeTo(360, 420); check(v.canvas.width === Math.round(360 * 2) || v.canvas.width > 0, 'resize updates canvas'); v.frames(2);
  v.resizeTo(800, 360); v.frames(2); check(v.errors.length === 0, 'resize/rotation without errors');
  // 8. touch rotation and pinch zoom change what is drawn
  v = make(); v.set(KOTLIN_DEFAULT); await v.settle(); v.frames(3);
  const before = v.stats.transforms.slice(-40).join(';');
  v.touch('touchstart', [{ clientX: 100, clientY: 100 }]); v.touch('touchmove', [{ clientX: 220, clientY: 130 }]); v.touch('touchend', []); v.frames(3);
  check(v.stats.transforms.slice(-40).join(';') !== before, 'touch drag rotates the view');
  const b2 = v.stats.transforms.slice(-40).join(';');
  v.touch('touchstart', [{ clientX: 100, clientY: 100 }, { clientX: 200, clientY: 100 }]); v.touch('touchmove', [{ clientX: 60, clientY: 100 }, { clientX: 240, clientY: 100 }]); v.touch('touchend', []); v.frames(3);
  check(v.stats.transforms.slice(-40).join(';') !== b2, 'pinch changes zoom');
  // 9. errors in the page are forwarded to the host
  v = make(); v.win.onerror('boom'); check(v.errors.includes('boom'), 'window.onerror forwards to OrbitBridge');
  // 10. host is told when the first frame was actually drawn (so the UI can hide its loading overlay)
  v = make(); v.frames(2); check(!v.messages.some((m) => m.type === 'drawn'), 'no drawn message before a state');
  v.set(KOTLIN_DEFAULT); await v.settle(); v.frames(3);
  const drawn = v.messages.filter((m) => m.type === 'drawn'); check(drawn.length === 1 && drawn[0].width > 0 && drawn[0].height > 0, 'exactly one drawn message with the canvas size');
  // 11. size changes are picked up even if the WebView never fires a resize event
  v = make({ w: 0, h: 0 }); v.set(KOTLIN_DEFAULT); await v.settle(); v.frames(2);
  v.win.innerWidth = 400; v.win.innerHeight = 500; v.frames(2);
  check(v.canvas.width === Math.round(400 * 2) && v.canvas.height === Math.round(500 * 2), 'canvas follows size changes without a resize event (got ' + v.canvas.width + 'x' + v.canvas.height + ')');
  // 12. missing bridge never throws; state still renders
  v = make({ noBridge: true }); v.set(KOTLIN_DEFAULT); await v.settle(); v.frames(3);
  check(v.stats.draw + v.stats.fill > 50, 'renders even if no bridge object exists');
  console.log(checks + ' checks, ' + failed + ' failures'); process.exit(failed ? 1 : 0);
})();
