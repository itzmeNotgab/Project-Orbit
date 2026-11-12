import React, { useState } from 'react';
import { Alert, Switch, View } from 'react-native';
import { CharacterViewer } from '../components/CharacterViewer';
import { Button, Card, Chip, Empty, H, P, Row, Screen, Stepper } from '../components/ui';
import { ViewerControls } from './CosmeticsScreen';
import { BUILTIN_CAPES } from '../catalog';
import { useLibrary } from '../state/LibraryContext';
import { useSettings } from '../settings/SettingsContext';
import { pickFile } from '../lib/pick';
import { readPngInfo } from '../lib/png';
import { bytesToDataUri } from '../lib/base64';
import { shortId } from '../lib/ids';
import { colors } from '../theme/theme';

const MAX_CAPES = 20;
const MAX_BYTES = 300 * 1024;

export function CapesScreen() {
  const { viewerState, loadout, updateLoadout, capes, setCapes } = useLibrary();
  const { settings } = useSettings();
  const [busy, setBusy] = useState(false);

  const importCape = async () => {
    setBusy(true);
    try {
      if (capes.length >= MAX_CAPES) throw new Error('Cape library is full (' + MAX_CAPES + ').');
      const f = await pickFile(['image/png'], MAX_BYTES);
      if (!f) return;
      const info = readPngInfo(f.bytes);
      if (!info) throw new Error('That file is not a valid PNG.');
      const frameH = info.width / 2;
      const okWidth = info.width >= 64 && info.width % 64 === 0 && info.width <= 512;
      const frames = info.height / frameH;
      if (!okWidth || !Number.isInteger(frames) || frames < 1 || frames > 32) {
        throw new Error(
          'Capes must be 64x32 (or a multiple, e.g. 128x64). Animated capes stack 64x32 frames vertically. Got ' + info.width + 'x' + info.height + '.',
        );
      }
      const id = 'cape_' + shortId();
      setCapes((p) => [...p, { id, name: f.name.replace(/\.png$/i, ''), dataUri: bytesToDataUri(f.bytes, 'image/png'), addedAt: Date.now() }]);
      updateLoadout({ capeId: id });
      if (frames > 1) Alert.alert('Animated cape', frames + ' frames detected. It will animate in the preview.');
    } catch (e: any) {
      Alert.alert('Import failed', String(e?.message ?? e));
    } finally {
      setBusy(false);
    }
  };

  const remove = (id: string) => {
    setCapes((p) => p.filter((c) => c.id !== id));
    if (loadout.capeId === id) updateLoadout({ capeId: 'none' });
  };

  return (
    <Screen>
      <CharacterViewer state={viewerState} height={340} />
      <ViewerControls />

      <Card>
        <H>Cape</H>
        <Row wrap>
          <Chip label="None" active={loadout.capeId === 'none'} onPress={() => updateLoadout({ capeId: 'none' })} />
          {BUILTIN_CAPES.map((c) => (
            <Chip key={c.id} label={c.name} active={loadout.capeId === c.id} onPress={() => updateLoadout({ capeId: c.id })} />
          ))}
        </Row>
        <P dim>Custom capes</P>
        {capes.length === 0 ? (
          <Empty title="No custom capes" text="Import a 64x32 PNG (or an animated frame strip)." />
        ) : (
          capes.map((c) => (
            <Row key={c.id}>
              <P style={{ flex: 1 }}>{c.name}</P>
              <Button small variant={loadout.capeId === c.id ? 'outline' : 'solid'} label={loadout.capeId === c.id ? 'Equipped' : 'Equip'} onPress={() => updateLoadout({ capeId: c.id })} />
              <Button small variant="danger" label="Delete" onPress={() => remove(c.id)} />
            </Row>
          ))
        )}
        <Button label="Import cape PNG" onPress={importCape} busy={busy} />
      </Card>

      <Card>
        <H>Physics</H>
        <Row>
          <P style={{ flex: 1 }}>Cape physics</P>
          <Switch
            value={loadout.capePhysics}
            onValueChange={(v) => updateLoadout({ capePhysics: v })}
            trackColor={{ true: settings.accent, false: colors.border }}
            thumbColor={colors.text}
          />
        </Row>
        <Stepper label="Stiffness" value={loadout.capeStiffness} min={0} max={1} step={0.1} onChange={(v) => updateLoadout({ capeStiffness: v })} format={(v) => v.toFixed(1)} />
        <Stepper label="Sway" value={loadout.capeSway} min={0} max={1.5} step={0.1} onChange={(v) => updateLoadout({ capeSway: v })} format={(v) => v.toFixed(1)} />
        <P dim>Pick Walk or Run to see the cape react. Other players only see ORBIT capes through an ORBIT-compatible client.</P>
      </Card>
    </Screen>
  );
}
void View;
