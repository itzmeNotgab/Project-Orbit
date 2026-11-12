import React, { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { StatusBar } from 'expo-status-bar';
import { SettingsProvider, useSettings } from './src/settings/SettingsContext';
import { LibraryProvider } from './src/state/LibraryContext';
import { AuthProvider } from './src/state/AuthContext';
import { Intro } from './src/components/Intro';
import { OrbitLogo } from './src/components/OrbitLogo';
import { ErrorBoundary } from './src/components/ErrorBoundary';
import { Loading } from './src/components/ui';
import { HomeScreen } from './src/screens/HomeScreen';
import { CosmeticsScreen } from './src/screens/CosmeticsScreen';
import { CapesScreen } from './src/screens/CapesScreen';
import { SkinsScreen } from './src/screens/SkinsScreen';
import { PacksScreen } from './src/screens/PacksScreen';
import { WorldsScreen } from './src/screens/WorldsScreen';
import { InGameScreen } from './src/screens/InGameScreen';
import { AccountScreen } from './src/screens/AccountScreen';
import { SettingsScreen } from './src/screens/SettingsScreen';
import { APP_NAME, SECTIONS } from './src/config';
import { colors, spacing } from './src/theme/theme';

function renderScreen(id: string, go: (id: string) => void) {
  switch (id) {
    case 'cosmetics': return <CosmeticsScreen />;
    case 'capes': return <CapesScreen />;
    case 'skins': return <SkinsScreen />;
    case 'addons': return <PacksScreen scope="addons" />;
    case 'textures': return <PacksScreen scope="textures" />;
    case 'worlds': return <WorldsScreen />;
    case 'ingame': return <InGameScreen />;
    case 'account': return <AccountScreen go={go} />;
    case 'settings': return <SettingsScreen />;
    default: return <HomeScreen go={go} />;
  }
}

function Shell() {
  const { settings, loaded } = useSettings();
  const [introDone, setIntroDone] = useState(false);
  const [tab, setTab] = useState('home');

  if (!loaded) return <View style={styles.root} />;
  if (settings.introEnabled && !introDone) return <Intro onDone={() => setIntroDone(true)} />;

  const section = SECTIONS.find((s) => s.id === tab) ?? SECTIONS[0];
  return (
    <SafeAreaView style={styles.root} edges={['top', 'bottom']}>
      <View style={styles.header}>
        <OrbitLogo size={28} accent={settings.accent} />
        <Text style={styles.brand}>{APP_NAME}</Text>
        <Text style={styles.page}>{section.title}</Text>
      </View>
      <View style={{ flex: 1 }}>
        <ErrorBoundary key={section.id} label={section.title}>
          {renderScreen(section.id, setTab)}
        </ErrorBoundary>
      </View>
      <View style={styles.tabs}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.tabsInner}>
          {SECTIONS.map((s) => {
            const active = s.id === tab;
            return (
              <Pressable key={s.id} onPress={() => setTab(s.id)} style={styles.tab}>
                <Text style={[styles.tabText, active && { color: settings.accent, fontWeight: '700' }]}>{s.title}</Text>
                <View style={[styles.dot, { backgroundColor: active ? settings.accent : 'transparent' }]} />
              </Pressable>
            );
          })}
        </ScrollView>
      </View>
    </SafeAreaView>
  );
}

function Gate() {
  return (
    <LibraryProvider>
      <AuthProvider>
        <Shell />
      </AuthProvider>
    </LibraryProvider>
  );
}

export default function App() {
  return (
    <SafeAreaProvider>
      <SettingsProvider>
        <StatusBar style="light" />
        <Gate />
      </SettingsProvider>
    </SafeAreaProvider>
  );
}
void Loading;

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg },
  header: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, paddingHorizontal: spacing.lg, paddingVertical: spacing.md },
  brand: { color: colors.text, fontSize: 16, fontWeight: '800', letterSpacing: 4 },
  page: { color: colors.textDim, fontSize: 14, marginLeft: 'auto' },
  tabs: { borderTopWidth: 1, borderTopColor: colors.border, backgroundColor: colors.surface },
  tabsInner: { paddingHorizontal: spacing.sm },
  tab: { paddingHorizontal: spacing.md, paddingVertical: spacing.md, alignItems: 'center', gap: spacing.xs },
  tabText: { color: colors.textDim, fontSize: 13 },
  dot: { width: 5, height: 5, borderRadius: 3 },
});
