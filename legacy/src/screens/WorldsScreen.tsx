import React, { useState } from 'react';
import { Alert, Image, Text, TextInput, View } from 'react-native';
import { Button, Card, Empty, H, P, Row, Screen } from '../components/ui';
import { WorldEntry, useLibrary } from '../state/LibraryContext';
import { inspectWorldArchive } from '../lib/packs';
import { pickFile } from '../lib/pick';
import { copyIntoLibrary, fileExists, openInMinecraft, removeFile, shareFile } from '../lib/storage';
import { shortId } from '../lib/ids';
import { colors, radius } from '../theme/theme';

function fmtSize(n: number): string {
  return n > 1048576 ? (n / 1048576).toFixed(1) + ' MB' : Math.max(1, Math.round(n / 1024)) + ' KB';
}

export function WorldsScreen() {
  const { worlds, setWorlds } = useLibrary();
  const [busy, setBusy] = useState<string | null>(null);
  const [editing, setEditing] = useState<{ id: string; text: string } | null>(null);

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

  const importWorld = () =>
    guard('import', async () => {
      const f = await pickFile(['*/*'], 100 * 1024 * 1024);
      if (!f) return;
      if (!/\.(mcworld|zip)$/i.test(f.name)) throw new Error('Expected a .mcworld file.');
      const res = inspectWorldArchive(f.bytes);
      if (!res.ok) throw new Error(res.error);
      const uri = copyIntoLibrary(f.uri, 'worlds', Date.now() + '_' + f.name);
      setWorlds((p) => [
        ...p,
        { id: 'world_' + shortId(), name: res.world.name, fileName: f.name, uri, sizeBytes: f.size, addedAt: Date.now(), icon: res.world.icon, favorite: false, backupOf: null },
      ]);
    });

  const backup = (w: WorldEntry) =>
    guard('bk' + w.id, async () => {
      if (!fileExists(w.uri)) throw new Error('The stored file is missing.');
      const stamp = new Date().toISOString().slice(0, 16).replace('T', ' ');
      const uri = copyIntoLibrary(w.uri, 'backups', Date.now() + '_' + w.fileName);
      setWorlds((p) => [
        ...p,
        { ...w, id: 'world_' + shortId(), name: w.name + ' (backup ' + stamp + ')', uri, addedAt: Date.now(), favorite: false, backupOf: w.id },
      ]);
    });

  const open = (w: WorldEntry) =>
    guard('op' + w.id, async () => {
      if (!fileExists(w.uri)) throw new Error('The stored file is missing. Remove this entry and import it again.');
      const how = await openInMinecraft(w.uri);
      if (how === 'shared') Alert.alert('Almost there', 'Pick Minecraft in the share sheet, or save the file and open it from your file manager.');
    });

  const exportWorld = (w: WorldEntry) => guard('ex' + w.id, () => shareFile(w.uri));

  const remove = (w: WorldEntry) =>
    Alert.alert('Remove from library?', 'This removes ORBIT\'s copy only. Worlds inside Minecraft are not touched.', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Remove',
        style: 'destructive',
        onPress: () => {
          removeFile(w.uri);
          setWorlds((p) => p.filter((x) => x.id !== w.id));
        },
      },
    ]);

  const sorted = [...worlds].sort((a, b) => Number(b.favorite) - Number(a.favorite) || b.addedAt - a.addedAt);

  return (
    <Screen>
      <Card>
        <H>Worlds</H>
        <P dim>
          Android (11 and newer) blocks apps from reading Minecraft's own world folder, so ORBIT cannot list the worlds already inside the game. It keeps a library of .mcworld files you import here: back them up, export them, and send them to Minecraft, which imports each as a new world. Restoring a backup therefore creates a new copy rather than overwriting.
        </P>
        <Button label="Import .mcworld" onPress={importWorld} busy={busy === 'import'} />
      </Card>

      {sorted.length === 0 ? (
        <Empty title="No worlds yet" text="Export a world from Minecraft as a .mcworld file, then import it here." />
      ) : (
        sorted.map((w) => (
          <Card key={w.id}>
            <Row>
              {w.icon ? (
                <Image source={{ uri: w.icon }} style={{ width: 72, height: 40, borderRadius: radius.md }} />
              ) : (
                <View style={{ width: 72, height: 40, borderRadius: radius.md, backgroundColor: colors.surfaceAlt }} />
              )}
              <View style={{ flex: 1 }}>
                {editing?.id === w.id ? (
                  <TextInput
                    value={editing.text}
                    onChangeText={(t) => setEditing({ id: w.id, text: t })}
                    style={{ color: colors.text, borderBottomWidth: 1, borderBottomColor: colors.border, padding: 2 }}
                    autoFocus
                    maxLength={60}
                    onSubmitEditing={() => {
                      const name = editing.text.trim();
                      if (name) setWorlds((p) => p.map((x) => (x.id === w.id ? { ...x, name } : x)));
                      setEditing(null);
                    }}
                  />
                ) : (
                  <Text style={{ color: colors.text, fontWeight: '700' }}>
                    {w.favorite ? '★ ' : ''}
                    {w.name}
                  </Text>
                )}
                <P dim>
                  {w.backupOf ? 'Backup' : 'World'} · {fmtSize(w.sizeBytes)} · {new Date(w.addedAt).toLocaleDateString()}
                </P>
              </View>
            </Row>
            <Row wrap>
              <Button small label={w.backupOf ? 'Restore' : 'Open in Minecraft'} busy={busy === 'op' + w.id} onPress={() => open(w)} />
              {!w.backupOf ? <Button small variant="outline" label="Backup" busy={busy === 'bk' + w.id} onPress={() => backup(w)} /> : null}
              <Button small variant="outline" label="Export" busy={busy === 'ex' + w.id} onPress={() => exportWorld(w)} />
              <Button small variant="outline" label={w.favorite ? 'Unstar' : 'Star'} onPress={() => setWorlds((p) => p.map((x) => (x.id === w.id ? { ...x, favorite: !x.favorite } : x)))} />
              <Button small variant="outline" label="Rename" onPress={() => setEditing({ id: w.id, text: w.name })} />
              <Button small variant="danger" label="Remove" onPress={() => remove(w)} />
            </Row>
          </Card>
        ))
      )}
    </Screen>
  );
}
