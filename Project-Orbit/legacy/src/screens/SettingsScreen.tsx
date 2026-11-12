import React, { useState } from 'react';
import { Alert, Switch, TextInput, View, Pressable, Text } from 'react-native';
import { Button, Card, H, P, Row, Screen } from '../components/ui';
import { useSettings } from '../settings/SettingsContext';
import { useLibrary } from '../state/LibraryContext';
import { api } from '../lib/api';
import { APP_VERSION } from '../config';
import { ACCENTS, colors, radius, spacing } from '../theme/theme';

export function SettingsScreen() {
  const { settings, update } = useSettings();
  const { resetAll } = useLibrary();
  const [url, setUrl] = useState(settings.serverUrl);
  const [testing, setTesting] = useState(false);

  const saveUrl = () => {
    const v = url.trim();
    if (v && !/^https:\/\//i.test(v)) {
      Alert.alert('Invalid URL', 'The server address must start with https://');
      return;
    }
    update({ serverUrl: v });
  };

  const test = async () => {
    setTesting(true);
    try {
      await api.health(url.trim());
      Alert.alert('Server reachable', 'The ORBIT server responded.');
    } catch (e: any) {
      Alert.alert('Cannot reach server', String(e?.message ?? e));
    } finally {
      setTesting(false);
    }
  };

  const reset = () =>
    Alert.alert('Reset library?', 'Removes saved skins, capes, pack and world entries, and your loadout from ORBIT. Minecraft is not touched.', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Reset', style: 'destructive', onPress: () => resetAll() },
    ]);

  return (
    <Screen>
      <Card>
        <Row>
          <View style={{ flex: 1 }}>
            <H>Startup intro</H>
            <P dim>Play the ORBIT animation each time the app opens.</P>
          </View>
          <Switch value={settings.introEnabled} onValueChange={(v) => update({ introEnabled: v })} trackColor={{ true: settings.accent, false: colors.border }} thumbColor={colors.text} />
        </Row>
      </Card>

      <Card>
        <H>Accent color</H>
        <Row>
          {ACCENTS.map((a) => {
            const active = a.value === settings.accent;
            return (
              <Pressable key={a.value} onPress={() => update({ accent: a.value })} style={{ alignItems: 'center', gap: 4, flex: 1 }}>
                <View style={{ width: 40, height: 40, borderRadius: 20, backgroundColor: a.value, borderWidth: 2, borderColor: active ? colors.text : 'transparent' }} />
                <Text style={{ color: active ? colors.text : colors.textDim, fontSize: 11 }}>{a.name}</Text>
              </Pressable>
            );
          })}
        </Row>
      </Card>

      <Card>
        <H>ORBIT server</H>
        <TextInput
          value={url}
          onChangeText={setUrl}
          placeholder="https://your-orbit-server.example.com"
          placeholderTextColor={colors.textDim}
          autoCapitalize="none"
          autoCorrect={false}
          keyboardType="url"
          style={{ color: colors.text, backgroundColor: colors.surfaceAlt, borderRadius: radius.md, borderWidth: 1, borderColor: colors.border, paddingHorizontal: spacing.md, paddingVertical: 10 }}
        />
        <Row>
          <Button small label="Save" onPress={saveUrl} />
          <Button small variant="outline" label="Test" busy={testing} disabled={!url.trim()} onPress={test} />
        </Row>
      </Card>

      <Card>
        <H>Data</H>
        <Button variant="danger" label="Reset library" onPress={reset} />
        <P dim>ORBIT v{APP_VERSION}. Not affiliated with Mojang, Microsoft, Flarial, or Atlas.</P>
      </Card>
    </Screen>
  );
}
