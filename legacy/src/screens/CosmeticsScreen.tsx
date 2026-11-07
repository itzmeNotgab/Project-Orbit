import React, { useState } from 'react';
import { Switch, View } from 'react-native';
import { CharacterViewer } from '../components/CharacterViewer';
import { Button, Card, Chip, H, P, Row, Screen, Stepper } from '../components/ui';
import { ANIMS, CAMERAS, CATEGORIES, CosmeticCategory, COSMETICS, PETS } from '../catalog';
import { useLibrary } from '../state/LibraryContext';
import { useAuth } from '../state/AuthContext';
import { useSettings } from '../settings/SettingsContext';
import { colors } from '../theme/theme';

export function ViewerControls() {
  const { loadout, updateLoadout } = useLibrary();
  return (
    <Card>
      <H>Animation</H>
      <Row wrap>
        {ANIMS.map((a) => (
          <Chip key={a.id} label={a.label} active={loadout.anim === a.id} onPress={() => updateLoadout({ anim: a.id })} />
        ))}
      </Row>
      <H>Camera</H>
      <Row wrap>
        {CAMERAS.map((c) => (
          <Chip key={c.id} label={c.label} active={loadout.camera === c.id} onPress={() => updateLoadout({ camera: c.id })} />
        ))}
      </Row>
      <P dim>Drag to rotate, pinch to zoom.</P>
    </Card>
  );
}

export function CosmeticsScreen() {
  const { viewerState, loadout, updateLoadout } = useLibrary();
  const { user } = useAuth();
  const { settings } = useSettings();
  const [mode, setMode] = useState<'cosmetics' | 'pets'>('cosmetics');
  const [cat, setCat] = useState<CosmeticCategory>('hats');

  const locked = (id: string) => !!user && !user.owned.includes('*') && !user.owned.includes(id);

  const toggle = (id: string, category: CosmeticCategory) => {
    if (loadout.cosmetics.includes(id)) {
      updateLoadout({ cosmetics: loadout.cosmetics.filter((x) => x !== id) });
      return;
    }
    const same = category === 'other' ? [] : COSMETICS.filter((c) => c.category === category).map((c) => c.id);
    updateLoadout({ cosmetics: [...loadout.cosmetics.filter((x) => !same.includes(x)), id] });
  };

  return (
    <Screen>
      <CharacterViewer state={viewerState} height={340} />
      <ViewerControls />

      <Row>
        <Chip label="Cosmetics" active={mode === 'cosmetics'} onPress={() => setMode('cosmetics')} />
        <Chip label="Pets" active={mode === 'pets'} onPress={() => setMode('pets')} />
      </Row>

      {mode === 'cosmetics' ? (
        <Card>
          <Row wrap>
            {CATEGORIES.map((c) => (
              <Chip key={c.id} label={c.label} active={cat === c.id} onPress={() => setCat(c.id)} />
            ))}
          </Row>
          {COSMETICS.filter((c) => c.category === cat).map((c) => {
            const on = loadout.cosmetics.includes(c.id);
            const lk = locked(c.id);
            return (
              <Row key={c.id}>
                <View style={{ flex: 1 }}>
                  <P>{c.name}</P>
                  {lk ? <P dim>Not owned by your account</P> : null}
                </View>
                <Button small variant={on ? 'outline' : 'solid'} label={on ? 'Unequip' : 'Equip'} disabled={lk} onPress={() => toggle(c.id, c.category)} />
              </Row>
            );
          })}
          <Button
            small
            variant="outline"
            label="Unequip all"
            disabled={loadout.cosmetics.length === 0}
            onPress={() => updateLoadout({ cosmetics: [] })}
          />
          <P dim>Previews render inside ORBIT. Other players only see them through an ORBIT-compatible client.</P>
        </Card>
      ) : (
        <Card>
          {PETS.map((p) => {
            const on = loadout.petId === p.id;
            return (
              <Row key={p.id}>
                <P style={{ flex: 1 }}>{p.name}</P>
                <Button small variant={on ? 'outline' : 'solid'} label={on ? 'Unequip' : 'Equip'} onPress={() => updateLoadout({ petId: on ? null : p.id })} />
              </Row>
            );
          })}
          <Stepper label="Distance" value={loadout.petDistance} min={12} max={40} step={2} onChange={(v) => updateLoadout({ petDistance: v })} />
          <Row>
            <P style={{ flex: 1 }}>Pet visible</P>
            <Switch
              value={loadout.petVisible}
              onValueChange={(v) => updateLoadout({ petVisible: v })}
              trackColor={{ true: settings.accent, false: colors.border }}
              thumbColor={colors.text}
            />
          </Row>
          <P dim>Pets are cosmetic previews. Pick Walk or Run above to see the pet walk beside the player.</P>
        </Card>
      )}
    </Screen>
  );
}
