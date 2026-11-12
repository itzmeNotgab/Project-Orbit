import React, { Component, ReactNode } from 'react';
import { Text, View } from 'react-native';
import { colors } from '../theme/theme';

export class ErrorBoundary extends Component<{ children: ReactNode; label?: string }, { error: Error | null }> {
  state = { error: null as Error | null };
  static getDerivedStateFromError(error: Error) {
    return { error };
  }
  render() {
    if (this.state.error) {
      return (
        <View style={{ padding: 20, gap: 8 }}>
          <Text style={{ color: colors.danger, fontWeight: '700' }}>{this.props.label ?? 'This screen'} crashed</Text>
          <Text style={{ color: colors.textDim, fontSize: 12 }}>{String(this.state.error.message)}</Text>
        </View>
      );
    }
    return this.props.children;
  }
}
