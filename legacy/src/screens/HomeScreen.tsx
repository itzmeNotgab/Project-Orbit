import React, { useCallback, useEffect, useState } from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';
import { OrbitLogo } from '../components/OrbitLogo';
import { Button, Card, Chip, H, P, Row, Screen } from '../components/ui';
import { useSettings } from '../settings/SettingsContext';
import { useLibrary } from '../state/LibraryContext';
import { useAuth } from '../state/AuthContext';
import { APP_NAME, APP_VERSION, TAGLINE, UPDATES } from '../config';
import { COSMETICS } from '../catalog';
import { detectMinecraft, launchMinecraft, McStatus } from '../lib/minecraft';
import { colors, spacing } from '../theme/theme';

export function HomeScreen({ go }: { go: (id: string) => void }) {
  const { settings } = useSettings();
  const { packs, loadout, updateLoadout } = useLibrary();
  const { user } = useAuth();
  const [mc, setMc] = useState<McStatus | null>(null);
  const [msg, setMsg] = useState<string | null>(null);

  const refresh = useCallback(() => setMc(detectMinecraft()), []);
  useEffect(refresh, [refresh]);

  const launch = () => {
    setMsg(launchMinecraft() ? null : 'Could not launch Minecraft. Is it installed?');
  };

  const featured = COSMETICS.filter((c) => ['hat_wizard', 'wings_angel', 'other_orbit_ring'].includes(c.id));
  const recent = [...packs].sort((a, b) => b.addedAt - a.addedAt).slice(0, 3);

  return (
    <Screen>
      <Card style={{ alignItems: 'center', borderColor: settings.accent }}>
        <OrbitLogo size={64} accent={settings.accent} />
        <Text style={styles.title}>{APP_NAME}</Text>
        <Text style={[styles.sub, { color: settings.accent }]}>{TAGLINE}</Text>
      </Card>

      <Card>
        <H>Player</H>
        <Row>
          {user?.avatar ? (
            <Image source={{ uri: user.avatar }} style={styles.avatar} />
          ) : (
            <View style={[styles.avatar, { backgroundColor: settings.accent, alignItems: 'center', justifyContent: 'center' }]}>
              <Text style={{ color: '#0A0712', fontWeight: '800', fontSize: 18 }}>{(user?.username ?? 'G')[0].toUpperCase()}</Text>
            </View>
          )}
          <View style={{ flex: 1 }}>
            <P>{user ? user.username : 'Guest'}</P>
            <P dim>{user ? 'ORBIT account' : 'Not signed in'}</P>
          </View>
          {!user ? <Button small variant="outline" label="Sign in" onPress={() => go('account')} /> : null}
        </Row>
      </Card>

      <Card>
        <H>Minecraft</H>
        {!mc ? null : !mc.nativeAvailable ? (
          <P dim>Version detection needs the ORBIT native module, which is missing in this build (for example Expo Go).</P>
        ) : mc.installed ? (
          <P>Detected version {mc.versionName} (build {mc.versionCode})</P>
        ) : (
          <P dim>Minecraft Bedrock (com.mojang.minecraftpe) was not found on this device.</P>
        )}
        {msg ? <P dim>{msg}</P> : null}
        <Row>
          <Button label="Launch Minecraft" onPress={launch} disabled={!mc?.installed} />
          <Button variant="outline" label="Re-check" onPress={refresh} />
        </Row>
      </Card>

      <Card>
        <H>Recently installed</H>
        {recent.length === 0 ? (
          <P dim>No packs imported yet. Use Addons or Textures to import one.</P>
        ) : (
          recent.map((p) => (
            <P key={p.id}>
              {p.name} <Text style={{ color: colors.textDim }}>v{p.version}</Text>
            </P>
          ))
        )}
      </Card>

      <Card>
        <H>Featured cosmetics</H>
        <Row wrap>
          {featured.map((c) => (
            <Chip
              key={c.id}
              label={c.name}
              active={loadout.cosmetics.includes(c.id)}
              onPress={() => {
                updateLoadout({ cosmetics: loadout.cosmetics.includes(c.id) ? loadout.cosmetics : [...loadout.cosmetics, c.id] });
                go('cosmetics');
              }}
            />
          ))}
        </Row>
      </Card>

      <Card>
        <H>Latest ORBIT updates</H>
        {UPDATES.map((u) => (
          <P key={u.version} dim>
            v{u.version}: {u.notes}
          </P>
        ))}
        <P dim>You are on v{APP_VERSION}.</P>
        <Button small variant="outline" label="Settings" onPress={() => go('settings')} />
      </Card>
    </Screen>
  );
}

const styles = StyleSheet.create({
  title: { color: colors.text, fontSize: 26, fontWeight: '800', letterSpacing: 6 },
  sub: { fontSize: 13, letterSpacing: 2 },
  avatar: { width: 44, height: 44, borderRadius: 22 },
});
void spacing;
