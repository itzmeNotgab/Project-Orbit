export type CosmeticCategory = 'hats' | 'halos' | 'head' | 'wings' | 'tails' | 'back' | 'shoulder' | 'other';

export const CATEGORIES: { id: CosmeticCategory; label: string }[] = [
  { id: 'hats', label: 'Hats' },
  { id: 'halos', label: 'Halos' },
  { id: 'head', label: 'Head' },
  { id: 'wings', label: 'Wings' },
  { id: 'tails', label: 'Tails' },
  { id: 'back', label: 'Back' },
  { id: 'shoulder', label: 'Shoulder' },
  { id: 'other', label: 'Other' },
];

export type Cosmetic = { id: string; name: string; category: CosmeticCategory };

/** IDs must match the builders in viewer-src/viewer.js. */
export const COSMETICS: Cosmetic[] = [
  { id: 'hat_tophat', name: 'Top Hat', category: 'hats' },
  { id: 'hat_crown', name: 'Gold Crown', category: 'hats' },
  { id: 'hat_wizard', name: 'Wizard Hat', category: 'hats' },
  { id: 'halo_ring', name: 'Gold Halo', category: 'halos' },
  { id: 'halo_neon', name: 'Neon Halo', category: 'halos' },
  { id: 'head_headphones', name: 'Headphones', category: 'head' },
  { id: 'head_antennae', name: 'Antennae', category: 'head' },
  { id: 'head_visor', name: 'Visor', category: 'head' },
  { id: 'wings_angel', name: 'Angel Wings', category: 'wings' },
  { id: 'wings_bat', name: 'Bat Wings', category: 'wings' },
  { id: 'wings_neon', name: 'Neon Wings', category: 'wings' },
  { id: 'tail_fox', name: 'Fox Tail', category: 'tails' },
  { id: 'tail_dragon', name: 'Dragon Tail', category: 'tails' },
  { id: 'tail_cat', name: 'Cat Tail', category: 'tails' },
  { id: 'back_backpack', name: 'Backpack', category: 'back' },
  { id: 'back_jetpack', name: 'Jetpack', category: 'back' },
  { id: 'back_sword', name: 'Sheathed Sword', category: 'back' },
  { id: 'shoulder_pads', name: 'Shoulder Pads', category: 'shoulder' },
  { id: 'shoulder_spikes', name: 'Spiked Pauldrons', category: 'shoulder' },
  { id: 'shoulder_parrot', name: 'Parrot', category: 'shoulder' },
  { id: 'other_scarf', name: 'Scarf', category: 'other' },
  { id: 'other_orbit_ring', name: 'Orbiting Planets', category: 'other' },
  { id: 'other_belt', name: 'Belt', category: 'other' },
];

export const PETS: { id: string; name: string }[] = [
  { id: 'pet_cat', name: 'Cube Cat' },
  { id: 'pet_slime', name: 'Jelly Slime' },
  { id: 'pet_robot', name: 'Hover Bot' },
  { id: 'pet_dragonling', name: 'Dragonling' },
];

export const BUILTIN_CAPES: { id: string; name: string }[] = [
  { id: 'orbit_nebula', name: 'Nebula' },
  { id: 'orbit_ion', name: 'Ion' },
  { id: 'orbit_ember', name: 'Ember' },
  { id: 'orbit_aurora', name: 'Aurora' },
];

export type Anim = 'idle' | 'walk' | 'run' | 'wave';
export const ANIMS: { id: Anim; label: string }[] = [
  { id: 'idle', label: 'Idle' },
  { id: 'walk', label: 'Walk' },
  { id: 'run', label: 'Run' },
  { id: 'wave', label: 'Wave' },
];

export type Cam = 'free' | 'front' | 'back' | 'left' | 'right' | 'top' | 'close';
export const CAMERAS: { id: Cam; label: string }[] = [
  { id: 'free', label: 'Free' },
  { id: 'front', label: 'Front' },
  { id: 'back', label: 'Back' },
  { id: 'left', label: 'Left' },
  { id: 'right', label: 'Right' },
  { id: 'top', label: 'Top' },
  { id: 'close', label: 'Close-up' },
];
