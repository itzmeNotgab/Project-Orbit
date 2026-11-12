import { strToU8, zipSync } from 'fflate';
import { makeOrbitIcon } from './png';
import { uuidv4 } from './ids';

/**
 * Generates the ORBIT in-game add-on (behavior pack + resource pack in one .mcaddon).
 *
 * This uses ONLY the supported Bedrock Script API (@minecraft/server and
 * @minecraft/server-ui). It cannot add a draggable overlay, FPS counter, or custom
 * crosshair: those are not exposed by stable APIs. See docs/LIMITATIONS.md.
 */

export const SERVER_MODULE_VERSION = '2.1.0';
export const SERVER_UI_MODULE_VERSION = '2.0.0';
export const MIN_ENGINE = [1, 21, 0];

export type IngameOptions = {
  /** Minecraft formatting code, e.g. '§d'. */
  colorCode: string;
  coords: boolean;
  direction: boolean;
  time: boolean;
  speed: boolean;
  tps: boolean;
  day: boolean;
  dimension: boolean;
};

export const COLOR_CODES = [
  { name: 'Nebula', code: '§d' },
  { name: 'Ion', code: '§b' },
  { name: 'Aurora', code: '§a' },
  { name: 'Ember', code: '§6' },
  { name: 'Snow', code: '§f' },
  { name: 'Sun', code: '§e' },
];

export function buildMainScript(o: IngameOptions): string {
  const defaults = JSON.stringify({
    color: o.colorCode,
    coords: o.coords,
    direction: o.direction,
    time: o.time,
    speed: o.speed,
    tps: o.tps,
    day: o.day,
    dimension: o.dimension,
  });
  return `import { world, system, ItemStack } from "@minecraft/server";
import { ActionFormData } from "@minecraft/server-ui";

const DEFAULTS = ${defaults};
const PROP = "orbit:cfg";
const COLORS = [["Nebula", "\\u00a7d"], ["Ion", "\\u00a7b"], ["Aurora", "\\u00a7a"], ["Ember", "\\u00a76"], ["Snow", "\\u00a7f"], ["Sun", "\\u00a7e"]];
const VISUAL = [["coords", "Coordinates"], ["direction", "Facing direction"], ["time", "Time of day"]];
const UTILITY = [["speed", "Speed (blocks/s)"], ["tps", "Server TPS"], ["day", "Day count"], ["dimension", "Dimension"]];

function loadCfg(player) {
  try {
    const raw = player.getDynamicProperty(PROP);
    if (typeof raw === "string") return Object.assign({}, DEFAULTS, JSON.parse(raw));
  } catch (e) {}
  return Object.assign({}, DEFAULTS);
}
function saveCfg(player, cfg) {
  try { player.setDynamicProperty(PROP, JSON.stringify(cfg)); } catch (e) {}
}

let tps = 20;
let lastTick = system.currentTick;
let lastMs = Date.now();

function facing(yaw) {
  if (yaw > -45 && yaw <= 45) return "S";
  if (yaw > 45 && yaw <= 135) return "W";
  if (yaw > -135 && yaw <= -45) return "E";
  return "N";
}
function clock() {
  const t = (world.getTimeOfDay() + 6000) % 24000;
  const h = Math.floor(t / 1000);
  const m = Math.floor(((t % 1000) / 1000) * 60);
  return (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
}
function dimName(player) {
  const id = player.dimension.id.replace("minecraft:", "");
  return id === "the_end" ? "End" : id === "nether" ? "Nether" : "Overworld";
}

system.runInterval(() => {
  const nowTick = system.currentTick;
  if (nowTick - lastTick >= 20) {
    const ms = Date.now();
    tps = Math.min(20, ((nowTick - lastTick) * 1000) / Math.max(1, ms - lastMs));
    lastTick = nowTick;
    lastMs = ms;
  }
  for (const player of world.getAllPlayers()) {
    const cfg = loadCfg(player);
    const c = cfg.color;
    const parts = [];
    if (cfg.coords) {
      const l = player.location;
      parts.push(c + "XYZ \\u00a7f" + Math.floor(l.x) + " " + Math.floor(l.y) + " " + Math.floor(l.z));
    }
    if (cfg.direction) parts.push(c + "Dir \\u00a7f" + facing(player.getRotation().y));
    if (cfg.time) parts.push(c + "Time \\u00a7f" + clock());
    if (cfg.speed) {
      const v = player.getVelocity();
      parts.push(c + "Spd \\u00a7f" + (Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z) * 20).toFixed(1));
    }
    if (cfg.tps) parts.push(c + "TPS \\u00a7f" + tps.toFixed(1));
    if (cfg.day) parts.push(c + "Day \\u00a7f" + (Math.floor(world.getAbsoluteTime() / 24000) + 1));
    if (cfg.dimension) parts.push(c + "Dim \\u00a7f" + dimName(player));
    if (parts.length) player.onScreenDisplay.setActionBar(parts.join("  \\u00a77|  "));
  }
}, 5);

function onOff(v) { return v ? "\\u00a7aON" : "\\u00a7cOFF"; }

function openMain(player) {
  const f = new ActionFormData().title("ORBIT").body("Choose a category")
    .button("Visual")
    .button("Utility")
    .button("Color")
    .button("Reset to defaults");
  f.show(player).then((r) => {
    if (r.canceled) return;
    if (r.selection === 0) openCategory(player, "Visual", VISUAL);
    else if (r.selection === 1) openCategory(player, "Utility", UTILITY);
    else if (r.selection === 2) openColor(player);
    else { saveCfg(player, Object.assign({}, DEFAULTS)); openMain(player); }
  });
}
function openCategory(player, title, mods) {
  const cfg = loadCfg(player);
  const f = new ActionFormData().title("ORBIT / " + title);
  for (const m of mods) f.button(m[1] + "\\n" + onOff(cfg[m[0]]));
  f.button("Back");
  f.show(player).then((r) => {
    if (r.canceled) return;
    if (r.selection >= mods.length) { openMain(player); return; }
    const key = mods[r.selection][0];
    cfg[key] = !cfg[key];
    saveCfg(player, cfg);
    openCategory(player, title, mods);
  });
}
function openColor(player) {
  const f = new ActionFormData().title("ORBIT / Color");
  for (const c of COLORS) f.button(c[1] + c[0]);
  f.button("Back");
  f.show(player).then((r) => {
    if (r.canceled) return;
    if (r.selection < COLORS.length) {
      const cfg = loadCfg(player);
      cfg.color = COLORS[r.selection][1];
      saveCfg(player, cfg);
    }
    openMain(player);
  });
}

world.afterEvents.itemUse.subscribe((ev) => {
  if (ev.itemStack && ev.itemStack.typeId === "orbit:menu") system.run(() => openMain(ev.source));
});

world.afterEvents.playerSpawn.subscribe((ev) => {
  if (!ev.initialSpawn) return;
  const inv = ev.player.getComponent("minecraft:inventory");
  if (!inv || !inv.container) return;
  const c = inv.container;
  for (let i = 0; i < c.size; i++) {
    const it = c.getItem(i);
    if (it && it.typeId === "orbit:menu") return;
  }
  try { c.addItem(new ItemStack("orbit:menu", 1)); } catch (e) {}
});
`;
}

export function buildInGameAddon(o: IngameOptions): Uint8Array {
  const icon = makeOrbitIcon(16);
  const bp: Record<string, Uint8Array> = {
    'ORBIT_BP/manifest.json': strToU8(
      JSON.stringify(
        {
          format_version: 2,
          header: {
            name: 'ORBIT In-Game (Behavior)',
            description: 'ORBIT HUD readouts and menu using the Bedrock Script API.',
            uuid: uuidv4(),
            version: [1, 0, 0],
            min_engine_version: MIN_ENGINE,
          },
          modules: [
            { type: 'script', language: 'javascript', entry: 'scripts/main.js', uuid: uuidv4(), version: [1, 0, 0] },
          ],
          dependencies: [
            { module_name: '@minecraft/server', version: SERVER_MODULE_VERSION },
            { module_name: '@minecraft/server-ui', version: SERVER_UI_MODULE_VERSION },
          ],
        },
        null,
        2,
      ),
    ),
    'ORBIT_BP/scripts/main.js': strToU8(buildMainScript(o)),
    'ORBIT_BP/items/orbit_menu.json': strToU8(
      JSON.stringify(
        {
          format_version: '1.21.60',
          'minecraft:item': {
            description: { identifier: 'orbit:menu', menu_category: { category: 'equipment' } },
            components: { 'minecraft:icon': 'orbit_menu', 'minecraft:max_stack_size': 1 },
          },
        },
        null,
        2,
      ),
    ),
    'ORBIT_BP/pack_icon.png': icon,
    'ORBIT_RP/manifest.json': strToU8(
      JSON.stringify(
        {
          format_version: 2,
          header: {
            name: 'ORBIT In-Game (Resources)',
            description: 'Icon for the ORBIT menu item.',
            uuid: uuidv4(),
            version: [1, 0, 0],
            min_engine_version: MIN_ENGINE,
          },
          modules: [{ type: 'resources', uuid: uuidv4(), version: [1, 0, 0] }],
        },
        null,
        2,
      ),
    ),
    'ORBIT_RP/textures/item_texture.json': strToU8(
      JSON.stringify(
        {
          resource_pack_name: 'orbit',
          texture_name: 'atlas.items',
          texture_data: { orbit_menu: { textures: 'textures/items/orbit_menu' } },
        },
        null,
        2,
      ),
    ),
    'ORBIT_RP/textures/items/orbit_menu.png': icon,
    'ORBIT_RP/pack_icon.png': icon,
  };
  return zipSync(bp);
}
