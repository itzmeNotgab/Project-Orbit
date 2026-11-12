import React, { useState } from 'react';
import { Alert, Switch, Text, View } from 'react-native';
import { Button, Card, Chip, H, P, Row, Screen } from '../components/ui';
import { useSettings } from '../settings/SettingsContext';
import { buildInGameAddon, COLOR_CODES, IngameOptions } from '../lib/ingame';
import { openInMinecraft, shareFile, writeBytes } from '../lib/storage';
import { detectMinecraft } from '../lib/minecraft';
import { colors } from '../theme/theme';

const MODULES: { key: keyof IngameOptions; label: string; group: string }[] = [
  { key: 'coords', label: 'Coordinates', group: 'Visual' },
  { key: 'direction', label: 'Facing direction', group: 'Visual' },
  { key: 'time', label: 'Time of day', group: 'Visual' },
  { key: 'speed', label: 'Speed', group: 'Utility' },
  { key: 'tps', label: 'Server TPS', group: 'Utility' },
  { key: 'day', label: 'Day count', group: 'Utility' },
  { key: 'dimension', label: 'Dimension', group: 'Utility' },
];

export function InGameScreen() {
  const { settings } = useSettings();
  const [opts, setOpts] = useState<IngameOptions>({
    colorCode: '§d',
    coords: true,
    direction: true,
    time: false,
    speed: false,
    tps: false,
    day: false,
    dimension: false,
  });
  const [busy, setBusy] = useState(false);
  const mc = detectMinecraft();

  const build = async (send: boolean) => {
    setBusy(true);
    try {
      const uri = writeBytes('exports', 'ORBIT-InGame.mcaddon', buildInGameAddon(opts));
      if (send) {
        const how = await openInMinecraft(uri);
        if (how === 'shared') Alert.alert('Almost there', 'Pick Minecraft in the share sheet, or save the file and open it from your file manager.');
      } else await shareFile(uri);
    } catch (e: any) {
      Alert.alert('Could not create the add-on', String(e?.message ?? e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen>
      <Card>
        <H>ORBIT in-game add-on</H>
        <P dim>
          Minecraft Bedrock has no supported way for a launcher to inject code or draw a menu over the game. What it does support is add-ons with the Script API, so ORBIT generates one. It shows a HUD line in the action bar and gives you an ORBIT menu item that opens a settings menu.
        </P>
        {mc.installed ? <P dim>Detected Minecraft {mc.versionName}. The add-on needs a version with Script API 2.x.</P> : null}
      </Card>

      <Card>
        <H>HUD modules</H>
        {['Visual', 'Utility'].map((g) => (
          <View key={g} style={{ gap: 4 }}>
            <Text style={{ color: settings.accent, fontWeight: '700' }}>{g}</Text>
            {MODULES.filter((m) => m.group === g).map((m) => (
              <Row key={m.key}>
                <P style={{ flex: 1 }}>{m.label}</P>
                <Switch
                  value={opts[m.key] as boolean}
                  onValueChange={(v) => setOpts({ ...opts, [m.key]: v })}
                  trackColor={{ true: settings.accent, false: colors.border }}
                  thumbColor={colors.text}
                />
              </Row>
            ))}
          </View>
        ))}
        <Text style={{ color: settings.accent, fontWeight: '700' }}>HUD color</Text>
        <Row wrap>
          {COLOR_CODES.map((c) => (
            <Chip key={c.code} label={c.name} active={opts.colorCode === c.code} onPress={() => setOpts({ ...opts, colorCode: c.code })} />
          ))}
        </Row>
        <P dim>These are the defaults. In the game you can toggle each one and change the color from the ORBIT menu, and it is remembered per player.</P>
      </Card>

      <Card>
        <H>Install</H>
        <Row wrap>
          <Button label="Create & open in Minecraft" busy={busy} onPress={() => build(true)} />
          <Button label="Share file" variant="outline" busy={busy} onPress={() => build(false)} />
        </Row>
        <P dim>
          After importing, edit your world → Add-ons, and turn on both "ORBIT In-Game (Behavior)" and "ORBIT In-Game (Resources)". You receive an ORBIT menu item when you join (or use /give @s orbit:menu). Use it to open the menu.
        </P>
      </Card>

      <Card>
        <H>Not possible with stable Bedrock APIs</H>
        <P dim>
          A draggable overlay, in-menu search, an FPS counter, and a custom crosshair. ORBIT does not fake these. Gameplay-changing cheats are not included.
        </P>
      </Card>
    </Screen>
  );
}
