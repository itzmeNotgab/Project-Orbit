import React, { ReactNode } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View, ViewStyle } from 'react-native';
import { useSettings } from '../settings/SettingsContext';
import { colors, radius, spacing } from '../theme/theme';

export function Card({ children, style }: { children: ReactNode; style?: ViewStyle }) {
  return <View style={[styles.card, style]}>{children}</View>;
}

export function H({ children }: { children: ReactNode }) {
  return <Text style={styles.h}>{children}</Text>;
}

export function P({ children, dim, style }: { children: ReactNode; dim?: boolean; style?: object }) {
  return <Text style={[dim ? styles.dim : styles.p, style]}>{children}</Text>;
}

export function Button({
  label,
  onPress,
  variant = 'solid',
  disabled,
  busy,
  small,
}: {
  label: string;
  onPress: () => void;
  variant?: 'solid' | 'outline' | 'danger';
  disabled?: boolean;
  busy?: boolean;
  small?: boolean;
}) {
  const { settings } = useSettings();
  const off = disabled || busy;
  const bg = variant === 'solid' ? settings.accent : 'transparent';
  const border = variant === 'danger' ? colors.danger : settings.accent;
  const fg = variant === 'solid' ? '#0A0712' : variant === 'danger' ? colors.danger : settings.accent;
  return (
    <Pressable
      onPress={onPress}
      disabled={off}
      style={[styles.btn, small && styles.btnSmall, { backgroundColor: bg, borderColor: border, opacity: off ? 0.5 : 1 }]}
    >
      {busy ? <ActivityIndicator size="small" color={fg} /> : <Text style={[styles.btnText, { color: fg }, small && { fontSize: 12 }]}>{label}</Text>}
    </Pressable>
  );
}

export function Chip({ label, active, onPress }: { label: string; active?: boolean; onPress: () => void }) {
  const { settings } = useSettings();
  return (
    <Pressable
      onPress={onPress}
      style={[styles.chip, active && { backgroundColor: settings.accent, borderColor: settings.accent }]}
    >
      <Text style={[styles.chipText, active && { color: '#0A0712', fontWeight: '700' }]}>{label}</Text>
    </Pressable>
  );
}

export function Empty({ title, text }: { title: string; text: string }) {
  return (
    <View style={styles.empty}>
      <Text style={styles.emptyTitle}>{title}</Text>
      <Text style={styles.dim}>{text}</Text>
    </View>
  );
}

export function Loading({ label }: { label?: string }) {
  const { settings } = useSettings();
  return (
    <View style={styles.empty}>
      <ActivityIndicator color={settings.accent} />
      {label ? <Text style={styles.dim}>{label}</Text> : null}
    </View>
  );
}

export function Stepper({
  label,
  value,
  min,
  max,
  step,
  onChange,
  format,
}: {
  label: string;
  value: number;
  min: number;
  max: number;
  step: number;
  onChange: (v: number) => void;
  format?: (v: number) => string;
}) {
  const { settings } = useSettings();
  const round = (v: number) => Math.round(v * 100) / 100;
  return (
    <View style={styles.stepper}>
      <Text style={[styles.p, { flex: 1 }]}>{label}</Text>
      <Pressable style={[styles.stepBtn, { borderColor: settings.accent }]} onPress={() => onChange(round(Math.max(min, value - step)))}>
        <Text style={[styles.btnText, { color: settings.accent }]}>−</Text>
      </Pressable>
      <Text style={[styles.p, { width: 54, textAlign: 'center' }]}>{format ? format(value) : String(value)}</Text>
      <Pressable style={[styles.stepBtn, { borderColor: settings.accent }]} onPress={() => onChange(round(Math.min(max, value + step)))}>
        <Text style={[styles.btnText, { color: settings.accent }]}>+</Text>
      </Pressable>
    </View>
  );
}

export function Row({ children, wrap }: { children: ReactNode; wrap?: boolean }) {
  return <View style={[styles.row, wrap && { flexWrap: 'wrap' }]}>{children}</View>;
}

const styles = StyleSheet.create({
  card: { backgroundColor: colors.surface, borderRadius: radius.lg, borderWidth: 1, borderColor: colors.border, padding: spacing.lg, gap: spacing.sm },
  h: { color: colors.text, fontSize: 16, fontWeight: '700' },
  p: { color: colors.text, fontSize: 14, lineHeight: 20 },
  dim: { color: colors.textDim, fontSize: 12, lineHeight: 18 },
  btn: { borderWidth: 1, borderRadius: radius.pill, paddingHorizontal: spacing.lg, paddingVertical: 10, alignItems: 'center', justifyContent: 'center', minHeight: 40 },
  btnSmall: { paddingHorizontal: spacing.md, paddingVertical: 6, minHeight: 32 },
  btnText: { fontSize: 14, fontWeight: '700' },
  chip: { borderWidth: 1, borderColor: colors.border, borderRadius: radius.pill, paddingHorizontal: spacing.md, paddingVertical: 6, backgroundColor: colors.surfaceAlt },
  chipText: { color: colors.textDim, fontSize: 13 },
  empty: { alignItems: 'center', padding: spacing.xl, gap: spacing.sm },
  emptyTitle: { color: colors.text, fontSize: 15, fontWeight: '700' },
  stepper: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  stepBtn: { width: 34, height: 34, borderRadius: 17, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
});

import { ScrollView } from 'react-native';
export function Screen({ children }: { children: ReactNode }) {
  return (
    <ScrollView contentContainerStyle={{ padding: spacing.lg, gap: spacing.md }} keyboardShouldPersistTaps="handled">
      {children}
    </ScrollView>
  );
}
