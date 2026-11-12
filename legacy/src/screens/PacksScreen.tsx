import React, { useMemo, useState } from 'react';
import { Alert, Image, Text, View } from 'react-native';
import { Button, Card, Chip, Empty, H, P, Row, Screen } from '../components/ui';
import { PackEntry, useLibrary } from '../state/LibraryContext';
import { inspectPackArchive, PackKind } from '../lib/packs';
import { pickFile } from '../lib/pick';
import { copyIntoLibrary, fileExists, openInMinecraft, removeFile, shareFile } from '../lib/storage';
import { compareVersions, detectMinecraft, parseVersion } from '../lib/minecraft';
import { shortId } from '../lib/ids';
import { colors } from '../theme/theme';

const KIND_LABEL: Record<PackKind, string> = { addon: 'Add-on', behavior: 'Behavior pack', resource: 'Texture pack', skin: 'Skin pack' };

function compat(entry: PackEntry): { text: string; ok: boolean | null } {
  if (!entry.minEngine) return { text: 'No minimum version declared', ok: null };
  const mc = detectMinecraft();
  if (!mc.installed || !mc.versionName) return { text: 'Needs Minecraft ' + entry.minEngine + '+ (installed version unknown)', ok: null };
  const ok = compareVersions(parseVersion(mc.versionName), parseVersion(entry.minEngine)) >= 0;
  return ok ? { text: 'Compatible (needs ' + entry.minEngine + '+, you have ' + mc.versionName + ')', ok: true } : { text: 'Needs Minecraft ' + entry.minEngine + '+, you have ' + mc.versionName, ok: false };
}

function fmtSize(n: number): string {
  return n > 1048576 ? (n / 1048576).toFixed(1) + ' MB' : Math.max(1, Math.round(n / 1024)) + ' KB';
}

export function PacksScreen({ scope }: { scope: 'addons' | 'textures' }) {
  const { packs, setPacks } = useLibrary();
  const [busy, setBusy] = useState(false);
  const [filter, setFilter] = useState<'all' | PackKind>('all');

  const inScope = (k: PackKind) => (scope === 'textures' ? k === 'resource' : k !== 'resource');
  const list = useMemo(
    () => packs.filter((p) => inScope(p.kind) && (filter === 'all' || p.kind === filter)).sort((a, b) => b.addedAt - a.addedAt),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [packs, filter, scope],
  );

  const importPack = async () => {
    setBusy(true);
    try {
      const f = await pickFile(['*/*'], 60 * 1024 * 1024);
      if (!f) return;
      if (!/\.(mcpack|mcaddon|zip)$/i.test(f.name)) throw new Error('Expected a .mcpack, .mcaddon, or .zip file.');
      const res = inspectPackArchive(f.bytes, f.name);
      if (!res.ok) throw new Error(res.error);
      const dup = packs.find((p) => p.packs.some((x) => res.packs.some((y) => y.uuid === x.uuid && y.version === x.version)));
      if (dup) throw new Error('"' + dup.name + '" with the same id and version is already in your library.');
      const uri = copyIntoLibrary(f.uri, 'packs', Date.now() + '_' + f.name);
      const first = res.packs[0];
      const engines = res.packs.map((p) => p.minEngine).filter((x): x is string => !!x);
      const maxEngine = engines.sort((a, b) => compareVersions(parseVersion(b), parseVersion(a)))[0] ?? null;
      const entry: PackEntry = {
        id: 'pack_' + shortId(),
        name: res.kind === 'addon' && res.packs.length > 1 ? f.name.replace(/\.(mcaddon|mcpack|zip)$/i, '') : first.name,
        kind: res.kind,
        fileName: f.name,
        uri,
        sizeBytes: f.size,
        addedAt: Date.now(),
        version: first.version,
        minEngine: maxEngine,
        description: first.description,
        icon: res.packs.find((p) => p.icon)?.icon ?? null,
        packs: res.packs.map((p) => ({ name: p.name, uuid: p.uuid, version: p.version, kind: p.kind })),
        warnings: res.warnings,
        installedHint: false,
      };
      setPacks((p) => [...p, entry]);
      if (!inScope(res.kind)) Alert.alert('Imported', 'This is a ' + KIND_LABEL[res.kind] + ', so it appears under ' + (scope === 'textures' ? 'Addons' : 'Textures') + '.');
    } catch (e: any) {
      Alert.alert('Import failed', String(e?.message ?? e));
    } finally {
      setBusy(false);
    }
  };

  const install = async (p: PackEntry) => {
    try {
      if (!fileExists(p.uri)) throw new Error('The stored file is missing. Remove this entry and import it again.');
      const how = await openInMinecraft(p.uri);
      setPacks((all) => all.map((x) => (x.id === p.id ? { ...x, installedHint: true } : x)));
      if (how === 'shared') Alert.alert('Almost there', 'Pick Minecraft in the share sheet, or save the file and open it from your file manager.');
    } catch (e: any) {
      Alert.alert('Could not open', String(e?.message ?? e));
    }
  };

  const remove = (p: PackEntry) =>
    Alert.alert('Remove from library?', 'This removes ORBIT\'s copy. Anything already imported into Minecraft stays there; delete it from Minecraft\'s storage settings.', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Remove',
        style: 'destructive',
        onPress: () => {
          removeFile(p.uri);
          setPacks((all) => all.filter((x) => x.id !== p.id));
        },
      },
    ]);

  const filters: { id: 'all' | PackKind; label: string }[] =
    scope === 'textures'
      ? [{ id: 'all', label: 'All' }]
      : [
          { id: 'all', label: 'All' },
          { id: 'addon', label: 'Add-ons' },
          { id: 'behavior', label: 'Behavior' },
          { id: 'skin', label: 'Skin packs' },
        ];

  return (
    <Screen>
      <Card>
        <H>{scope === 'textures' ? 'Texture packs' : 'Add-ons'}</H>
        <P dim>
          ORBIT validates and keeps a library of packs, then hands them to Minecraft through Android's file-open path. Enabling a pack and ordering priority happen in Minecraft, per world (Settings → Storage / world settings), because Android does not let ORBIT change that.
        </P>
        {filters.length > 1 ? (
          <Row wrap>
            {filters.map((f) => (
              <Chip key={f.id} label={f.label} active={filter === f.id} onPress={() => setFilter(f.id)} />
            ))}
          </Row>
        ) : null}
        <Button label="Import .mcpack / .mcaddon" onPress={importPack} busy={busy} />
      </Card>

      {list.length === 0 ? (
        <Empty title="Nothing here yet" text="Import a pack to see its details, version, and compatibility." />
      ) : (
        list.map((p) => {
          const c = compat(p);
          return (
            <Card key={p.id}>
              <Row>
                {p.icon ? <Image source={{ uri: p.icon }} style={{ width: 48, height: 48, borderRadius: 8 }} /> : null}
                <View style={{ flex: 1 }}>
                  <Text style={{ color: colors.text, fontWeight: '700', fontSize: 15 }}>{p.name}</Text>
                  <P dim>
                    {KIND_LABEL[p.kind]} · v{p.version} · {fmtSize(p.sizeBytes)}
                  </P>
                </View>
              </Row>
              {p.description ? <P dim>{p.description}</P> : null}
              <P style={{ color: c.ok === false ? colors.danger : c.ok ? '#34D399' : colors.textDim, fontSize: 12 }}>{c.text}</P>
              {p.packs.length > 1 || p.kind === 'addon'
                ? p.packs.map((x) => (
                    <P key={x.uuid + x.version} dim>
                      • {x.name} ({x.kind}) v{x.version}
                    </P>
                  ))
                : null}
              {p.warnings.map((w, i) => (
                <P key={i} dim>
                  ⚠ {w}
                </P>
              ))}
              <P dim>Status: {p.installedHint ? 'Sent to Minecraft (confirm in the game)' : 'In library, not sent to Minecraft yet'}</P>
              <Row wrap>
                <Button small label="Install in Minecraft" onPress={() => install(p)} />
                <Button small variant="outline" label="Share" onPress={() => shareFile(p.uri).catch((e) => Alert.alert('Share failed', String(e?.message ?? e)))} />
                <Button small variant="danger" label="Remove" onPress={() => remove(p)} />
              </Row>
            </Card>
          );
        })
      )}
    </Screen>
  );
}
