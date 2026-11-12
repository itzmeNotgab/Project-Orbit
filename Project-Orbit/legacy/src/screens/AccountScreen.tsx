import React, { useState } from 'react';
import { Alert, Image, Text, TextInput, View } from 'react-native';
import { Button, Card, H, P, Row, Screen } from '../components/ui';
import { useAuth } from '../state/AuthContext';
import { useLibrary } from '../state/LibraryContext';
import { useSettings } from '../settings/SettingsContext';
import { api } from '../lib/api';
import { COSMETICS } from '../catalog';
import { pickFile } from '../lib/pick';
import { bytesToDataUri } from '../lib/base64';
import { shortId } from '../lib/ids';
import { colors, radius, spacing } from '../theme/theme';

const input = {
  color: colors.text,
  backgroundColor: colors.surfaceAlt,
  borderRadius: radius.md,
  borderWidth: 1,
  borderColor: colors.border,
  paddingHorizontal: spacing.md,
  paddingVertical: 10,
} as const;

export function AccountScreen({ go }: { go: (id: string) => void }) {
  const { settings } = useSettings();
  const { user, token, busy, error, signIn, signUp, signOut, setAvatar } = useAuth();
  const { loadout, updateLoadout, capes, setCapes } = useLibrary();
  const [name, setName] = useState('');
  const [pw, setPw] = useState('');
  const [syncBusy, setSyncBusy] = useState(false);

  if (!settings.serverUrl) {
    return (
      <Screen>
        <Card>
          <H>Account</H>
          <P dim>ORBIT accounts need an ORBIT server (the code is in the backend/ folder of the repo). Set its https address in Settings to sign in.</P>
          <Button label="Open Settings" onPress={() => go('settings')} />
        </Card>
      </Screen>
    );
  }

  if (!user || !token) {
    return (
      <Screen>
        <Card>
          <H>Sign in to ORBIT</H>
          <TextInput value={name} onChangeText={setName} placeholder="Username" placeholderTextColor={colors.textDim} autoCapitalize="none" autoCorrect={false} style={input} maxLength={20} />
          <TextInput value={pw} onChangeText={setPw} placeholder="Password (8+ characters)" placeholderTextColor={colors.textDim} secureTextEntry autoCapitalize="none" style={input} maxLength={100} />
          {error ? <P style={{ color: colors.danger }}>{error}</P> : null}
          <Row>
            <Button label="Sign in" busy={busy} onPress={() => signIn(name.trim(), pw)} />
            <Button label="Create account" variant="outline" busy={busy} onPress={() => signUp(name.trim(), pw)} />
          </Row>
          <P dim>Usernames are 3-20 letters, numbers, or underscores. Passwords are hashed on the server and never stored in plain text.</P>
        </Card>
      </Screen>
    );
  }

  const pickAvatar = async () => {
    try {
      const f = await pickFile(['image/png', 'image/jpeg'], 150 * 1024);
      if (!f) return;
      const mime = /\.png$/i.test(f.name) ? 'image/png' : 'image/jpeg';
      await setAvatar(bytesToDataUri(f.bytes, mime));
    } catch (e: any) {
      Alert.alert('Could not set picture', String(e?.message ?? e));
    }
  };

  const upload = async () => {
    setSyncBusy(true);
    try {
      const custom = capes.find((c) => c.id === loadout.capeId);
      await api.putLoadout(settings.serverUrl, token, {
        cosmetics: loadout.cosmetics,
        capeId: custom ? 'custom' : loadout.capeId,
        capeData: custom ? custom.dataUri : null,
        petId: loadout.petId,
      });
      Alert.alert('Synced', 'Your loadout was uploaded.');
    } catch (e: any) {
      Alert.alert('Upload failed', String(e?.message ?? e));
    } finally {
      setSyncBusy(false);
    }
  };

  const download = async () => {
    setSyncBusy(true);
    try {
      const res = await api.getLoadout(settings.serverUrl, token);
      const l = res.loadout;
      if (!l) {
        Alert.alert('Nothing to download', 'No loadout has been uploaded for this account yet.');
        return;
      }
      const known = new Set(COSMETICS.map((c) => c.id));
      let capeId = l.capeId;
      if (l.capeId === 'custom' && l.capeData) {
        capeId = 'cape_' + shortId();
        setCapes((p) => [...p, { id: capeId, name: 'Synced cape', dataUri: l.capeData as string, addedAt: Date.now() }]);
      } else if (l.capeId === 'custom') capeId = 'none';
      updateLoadout({ cosmetics: l.cosmetics.filter((id) => known.has(id)), capeId, petId: l.petId });
      Alert.alert('Synced', 'Loadout downloaded.');
    } catch (e: any) {
      Alert.alert('Download failed', String(e?.message ?? e));
    } finally {
      setSyncBusy(false);
    }
  };

  const allOwned = user.owned.includes('*');
  return (
    <Screen>
      <Card>
        <Row>
          {user.avatar ? (
            <Image source={{ uri: user.avatar }} style={{ width: 56, height: 56, borderRadius: 28 }} />
          ) : (
            <View style={{ width: 56, height: 56, borderRadius: 28, backgroundColor: settings.accent, alignItems: 'center', justifyContent: 'center' }}>
              <Text style={{ color: '#0A0712', fontWeight: '800', fontSize: 22 }}>{user.username[0].toUpperCase()}</Text>
            </View>
          )}
          <View style={{ flex: 1 }}>
            <H>{user.username}</H>
            <P dim>{allOwned ? 'All current cosmetics unlocked' : user.owned.length + ' cosmetics owned'}</P>
          </View>
        </Row>
        {error ? <P style={{ color: colors.danger }}>{error}</P> : null}
        <Row wrap>
          <Button small variant="outline" label="Change picture" busy={busy} onPress={pickAvatar} />
          {user.avatar ? <Button small variant="outline" label="Remove picture" busy={busy} onPress={() => setAvatar(null)} /> : null}
        </Row>
      </Card>

      <Card>
        <H>Cosmetic sync</H>
        <P dim>Upload your equipped cosmetics, cape and pet so ORBIT-compatible clients can read them from your account, or restore them on another device.</P>
        <Row wrap>
          <Button label="Upload loadout" busy={syncBusy} onPress={upload} />
          <Button label="Download loadout" variant="outline" busy={syncBusy} onPress={download} />
        </Row>
      </Card>

      <Button label="Sign out" variant="danger" onPress={() => signOut()} />
    </Screen>
  );
}
