export const APP_NAME = 'ORBIT';
export const APP_VERSION = '0.2.0';
export const TAGLINE = 'Beyond Vanilla.';

export type Section = { id: string; title: string };

export const SECTIONS: Section[] = [
  { id: 'home', title: 'Home' },
  { id: 'cosmetics', title: 'Cosmetics' },
  { id: 'capes', title: 'Capes' },
  { id: 'skins', title: 'Skins' },
  { id: 'addons', title: 'Addons' },
  { id: 'textures', title: 'Textures' },
  { id: 'worlds', title: 'Worlds' },
  { id: 'ingame', title: 'In-Game' },
  { id: 'account', title: 'Account' },
  { id: 'settings', title: 'Settings' },
];

export const UPDATES: { version: string; notes: string }[] = [
  { version: '0.2.0', notes: 'All launcher sections implemented: 3D cosmetics, capes, pets, skins, packs, worlds, accounts, in-game add-on generator.' },
  { version: '0.1.0', notes: 'Project foundation, branding, settings.' },
];
