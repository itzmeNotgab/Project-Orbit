import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { WebView, WebViewMessageEvent } from 'react-native-webview';
import { VIEWER_HTML } from '../viewer/viewerHtml';
import { ViewerState } from '../state/LibraryContext';
import { colors, radius } from '../theme/theme';

export function CharacterViewer({ state, height = 320 }: { state: ViewerState; height?: number }) {
  const ref = useRef<WebView>(null);
  const [ready, setReady] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const js = useMemo(
    () => 'window.orbitSet(' + JSON.stringify(state).replace(/\u2028/g, '\\u2028').replace(/\u2029/g, '\\u2029') + ');true;',
    [state],
  );

  useEffect(() => {
    if (ready) ref.current?.injectJavaScript(js);
  }, [ready, js]);

  const onMessage = useCallback((e: WebViewMessageEvent) => {
    try {
      const m = JSON.parse(e.nativeEvent.data);
      if (m.type === 'ready') setReady(true);
      else if (m.type === 'error') setError(String(m.message));
    } catch {
      // ignore malformed messages
    }
  }, []);

  return (
    <View style={[styles.box, { height }]}>
      <WebView
        ref={ref}
        originWhitelist={['*']}
        source={{ html: VIEWER_HTML }}
        style={styles.web}
        scrollEnabled={false}
        overScrollMode="never"
        javaScriptEnabled
        domStorageEnabled={false}
        allowFileAccess={false}
        setSupportMultipleWindows={false}
        onMessage={onMessage}
        onError={() => setError('The 3D viewer could not start (WebView unavailable).')}
      />
      {error ? (
        <View style={styles.err}>
          <Text style={styles.errText}>3D viewer error: {error}</Text>
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  box: { borderRadius: radius.lg, overflow: 'hidden', backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.border },
  web: { flex: 1, backgroundColor: 'transparent' },
  err: { position: 'absolute', left: 8, right: 8, bottom: 8, backgroundColor: 'rgba(0,0,0,0.7)', padding: 8, borderRadius: 8 },
  errText: { color: colors.danger, fontSize: 12 },
});
