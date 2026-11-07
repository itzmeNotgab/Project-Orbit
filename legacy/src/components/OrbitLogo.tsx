import React from 'react';
import Svg, { Circle, Defs, Ellipse, LinearGradient, Stop } from 'react-native-svg';

export function OrbitLogo({ size = 64, accent }: { size?: number; accent: string }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 100 100">
      <Defs>
        <LinearGradient id="planet" x1="0" y1="0" x2="1" y2="1">
          <Stop offset="0" stopColor={accent} />
          <Stop offset="1" stopColor="#2E1065" />
        </LinearGradient>
      </Defs>
      <Circle cx={50} cy={50} r={26} fill="url(#planet)" />
      <Ellipse cx={50} cy={50} rx={44} ry={14} fill="none" stroke={accent} strokeWidth={3} opacity={0.9} transform="rotate(-25 50 50)" />
    </Svg>
  );
}
