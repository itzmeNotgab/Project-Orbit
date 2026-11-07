import React, { useState } from 'react';
import { Alert, Switch, Text, View } from 'react-native';
import { CharacterViewer } from '../components/CharacterViewer';
import { Button, Card, Empty, H, P, Row, Screen } from '../components/ui';
import { ViewerControls } from './CosmeticsScreen';
import { useLibrary } from '../state/LibraryContext';
import { useSettings } from '../settings/SettingsContext';
import { pickFile } from '../lib/pick';
import { readPngInfo } from '../lib/png';
import { bytesToDataUri, dataUriToBytes } from '../lib/base64';
import { shortId } from '../lib/ids';
import { buildSkinPack, readSkinPack } from '../lib/skinpack';
import { openInMinecraft, shareFile, writeBytes } from '../lib/storage';
import { colors } from '../theme/theme';

const MAX_SKINS = 30;
const MAX_BYTES = 300 * 1024;

export function SkinsScreen() {
  const { viewerState, loadout, updateLoadout, skins, setSkins } = useLibrary();
  const { settings } = useSettings();
  const [busy, setBusy] = useState<string | null>(null);

  const guard = async (key: string, fn: () => Promise<void>) => {
    setBusy(key);
    try {
      await fn();
    } catch (e: any) {
      Alert.alert('Something went wrong', String(e?.message ?? e));
    } finally {
      setBusy(null);
    }
  };

  const importPng = () =>
    guard('png', async () => {
      if (skins.length >= MAX_SKINS) throw new Error('Skin library is full (' + MAX_SKINS + ').');
      const f = await pickFile(['image/png'], MAX_BYTES);
      if (!f) return;
      const info = readPngInfo(f.bytes);
      if (!info) throw new Error('That file is not a valid PNG.');
      const ok = info.width >= 64 && info.width % 64 === 0 && (info.height === info.width || info.height * 2 === info.width);
      if (!ok) throw new Error('Skins must be 64x64 or 64x32 (or the same ratio at 2x/4x). Got ' + info.width + 'x' + info.height + '.');
      const id = 'skin_' + shortId();
      setSkins((p) => [...p, { id, name: f.name.replace(/\.png$/i, ''), dataUri: bytesToDataUri(f.bytes, 'image/png'), slim: false, addedAt: Date.now() }]);
      updateLoadout({ skinId: id });
    });

  const importPack = () =>
    guard('pack', async () => {
      const f = await pickFile(['*/*'], 30 * 1024 * 1024);
      if (!f) return;
      const { skins: found, skipped } = readSkinPack(f.bytes);
      if (found.length === 0) throw new Error('No usable skins found in this pack.');
      const room = MAX_SKINS - skins.length;
      const take = found.slice(0, Math.max(0, room));
      setSkins((p) => [...p, ...take.map((s) => ({ id: 'skin_' + shortId(), name: s.name, dataUri: s.dataUri, slim: s.slim, addedAt: Date.now() }))]);
      Alert.alert('Skin pack imported', take.length + ' skin(s) added' + (skipped ? ', ' + skipped + ' skipped (invalid or too large)' : '') + (take.length < found.length ? ', library full for the rest' : '') + '.');
    });

  const exportPng = (id: string) =>
    guard('png' + id, async () => {
      const s = skins.find((x) => x.id === id);
      if (!s) return;
      const uri = writeBytes('exports', s.name + '.png', dataUriToBytes(s.dataUri));
      await shareFile(uri, 'image/png');
    });

  const makePack = (id: string) =>
    guard('mk' + id, async () => {
      const s = skins.find((x) => x.id === id);
      if (!s) return;
      const bytes = buildSkinPack(s.name, [{ name: s.name, dataUri: s.dataUri, slim: s.slim }]);
      const uri = writeBytes('exports', 'ORBIT_' + s.name + '.mcpack', bytes);
      const how = await openInMinecraft(uri);
      if (how === 'shared') Alert.alert('Skin pack created', 'Choose Minecraft in the share sheet, or save the file and open it from your file manager.');
    });

  const setSlim = (id: string, v: boolean) => setSkins((p) => p.map((s) => (s.id === id ? { ...s, slim: v } : s)));
  const remove = (id: string) => {
    setSkins((p) => p.filter((s) => s.id !== id));
    if (loadout.skinId === id) updateLoadout({ skinId: null });
  };

  return (
    <Screen>
      <CharacterViewer state={viewerState} height={340} />
      <ViewerControls />

      <Card>
        <H>Skin library</H>
        <Row>
          <Button label="Use default skin" variant={loadout.skinId === null ? 'outline' : 'solid'} small onPress={() => updateLoadout({ skinId: null })} />
        </Row>
        {skins.length === 0 ? (
          <Empty title="No skins yet" text="Import a PNG skin or a skin pack (.mcpack)." />
        ) : (
          skins.map((s) => {
            const active = loadout.skinId === s.id;
            return (
              <View key={s.id} style={{ gap: 6, paddingVertical: 6, borderTopWidth: 1, borderTopColor: colors.border }}>
                <Row>
                  <Text style={{ color: colors.text, flex: 1, fontWeight: '700' }}>{s.name}</Text>
                  <Button small variant={active ? 'outline' : 'solid'} label={active ? 'In use' : 'Use'} onPress={() => updateLoadout({ skinId: s.id })} />
                </Row>
                <Row>
                  <P style={{ flex: 1 }}>Slim arms</P>
                  <Switch value={s.slim} onValueChange={(v) => setSlim(s.id, v)} trackColor={{ true: settings.accent, false: colors.border }} thumbColor={colors.text} />
                </Row>
                <Row wrap>
                  <Button small variant="outline" label="Export PNG" busy={busy === 'png' + s.id} onPress={() => exportPng(s.id)} />
                  <Button small variant="outline" label="Make .mcpack" busy={busy === 'mk' + s.id} onPress={() => makePack(s.id)} />
                  <Button small variant="danger" label="Delete" onPress={() => remove(s.id)} />
                </Row>
              </View>
            );
          })
        )}
        <Row wrap>
          <Button label="Import PNG" busy={busy === 'png'} onPress={importPng} />
          <Button label="Import skin pack" variant="outline" busy={busy === 'pack'} onPress={importPack} />
        </Row>
      </Card>

      <Card>
        <H>Using a skin in Minecraft</H>
        <P dim>
          Bedrock manages skins itself. "Make .mcpack" builds a standard skin pack and hands it to Minecraft, which imports it as a normal skin pack. Alternatively export the PNG and import it from Minecraft's character creator. ORBIT cannot change your equipped skin from outside the game.
        </P>
      </Card>
    </Screen>
  );
}
