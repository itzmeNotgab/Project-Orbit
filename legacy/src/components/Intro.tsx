import React, { useEffect, useRef } from 'react';
import { Animated, Easing, StyleSheet, Text, View } from 'react-native';
import { OrbitLogo } from './OrbitLogo';
import { useSettings } from '../settings/SettingsContext';
import { APP_NAME, TAGLINE } from '../config';
import { colors } from '../theme/theme';

export function Intro({ onDone }: { onDone: () => void }) {
  const { settings } = useSettings();
  const opacity = useRef(new Animated.Value(0)).current;
  const scale = useRef(new Animated.Value(0.7)).current;
  const spin = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    const loop = Animated.loop(
      Animated.timing(spin, { toValue: 1, duration: 2400, easing: Easing.linear, useNativeDriver: true }),
    );
    loop.start();
    const seq = Animated.sequence([
      Animated.parallel([
        Animated.timing(opacity, { toValue: 1, duration: 600, useNativeDriver: true }),
        Animated.spring(scale, { toValue: 1, useNativeDriver: true }),
      ]),
      Animated.delay(900),
      Animated.timing(opacity, { toValue: 0, duration: 350, useNativeDriver: true }),
    ]);
    seq.start(({ finished }) => {
      if (finished) onDone();
    });
    return () => {
      loop.stop();
      seq.stop();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const rotate = spin.interpolate({ inputRange: [0, 1], outputRange: ['0deg', '360deg'] });

  return (
    <View style={styles.root}>
      <Animated.View style={[styles.center, { opacity, transform: [{ scale }] }]}>
        <Animated.View style={[styles.ring, { borderColor: settings.accent, transform: [{ rotate }] }]} />
        <OrbitLogo size={96} accent={settings.accent} />
        <Text style={styles.name}>{APP_NAME}</Text>
        <Text style={[styles.tag, { color: settings.accent }]}>{TAGLINE}</Text>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg, alignItems: 'center', justifyContent: 'center' },
  center: { alignItems: 'center', justifyContent: 'center' },
  ring: { position: 'absolute', top: -24, width: 144, height: 144, borderRadius: 72, borderWidth: 2, borderStyle: 'dashed', opacity: 0.4 },
  name: { color: colors.text, fontSize: 34, fontWeight: '800', letterSpacing: 8, marginTop: 24 },
  tag: { fontSize: 14, marginTop: 6, letterSpacing: 2 },
});
