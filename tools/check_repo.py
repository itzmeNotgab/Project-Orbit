#!/usr/bin/env python3
"""Fast repo sanity checks (no Android SDK needed). Exit 1 on problems.
1. stray copies like 'File (2).kt'  2. duplicate top-level Kotlin declarations in one package
3. common Compose symbols used without an import (the class of error that broke earlier builds)."""
import glob, re, sys, collections, os

problems = []
files = [f for f in glob.glob('**/*', recursive=True) if os.path.isfile(f) and not f.startswith(('legacy/', 'native/build/', '.git/')) and '/build/' not in f]

for f in files:
    if re.search(r' \(\d+\)|\(copy\)| copy\.', os.path.basename(f)):
        problems.append(f'stray copy: {f}')

kt = [f for f in files if f.endswith('.kt')]
decl = collections.defaultdict(list)
for f in kt:
    s = open(f, encoding='utf-8').read()
    pk = re.search(r'^package\s+([\w.]+)', s, re.M)
    pk = pk.group(1) if pk else ''
    for m in re.finditer(r'^(public |internal )?(?:data |sealed |enum |abstract |open |inline )*(?:class|object|interface|fun)\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(?P<n>\w+)', s, re.M):
        if not s[m.start():].startswith('private'):
            decl[(pk, m.group('n'), f.split('/src/')[0])].append(f)
for k, v in decl.items():
    if len(v) > 1 and not all(n.endswith('Test.kt') for n in v):
        problems.append(f'duplicate declaration {k[1]} in package {k[0]}: {sorted(set(v))}')

NEEDS = {  # symbol -> imports that satisfy it (any one)
    'rememberSaveable': ['androidx.compose.runtime.saveable.rememberSaveable'],
    'horizontalScroll': ['androidx.compose.foundation.horizontalScroll'],
    'verticalScroll': ['androidx.compose.foundation.verticalScroll'],
    'rememberScrollState': ['androidx.compose.foundation.rememberScrollState'],
    'clickable': ['androidx.compose.foundation.clickable'],
    'background': ['androidx.compose.foundation.background'],
    'RoundedCornerShape': ['androidx.compose.foundation.shape.RoundedCornerShape'],
    'LazyColumn': ['androidx.compose.foundation.lazy.LazyColumn'],
    'detectTapGestures': ['androidx.compose.foundation.gestures.detectTapGestures'],
    'detectDragGestures': ['androidx.compose.foundation.gestures.detectDragGestures'],
    'pointerInput': ['androidx.compose.ui.input.pointer.pointerInput'],
    'AndroidView': ['androidx.compose.ui.viewinterop.AndroidView'],
    'LocalContext': ['androidx.compose.ui.platform.LocalContext'],
    'collectAsState': ['androidx.compose.runtime.collectAsState'],
    'mutableIntStateOf': ['androidx.compose.runtime.mutableIntStateOf', 'androidx.compose.runtime.*'],
    'LaunchedEffect': ['androidx.compose.runtime.LaunchedEffect', 'androidx.compose.runtime.*'],
    'remember': ['androidx.compose.runtime.remember', 'androidx.compose.runtime.*'],
    'mutableStateOf': ['androidx.compose.runtime.mutableStateOf', 'androidx.compose.runtime.*'],
    'Switch': ['androidx.compose.material3.Switch'],
    'Slider': ['androidx.compose.material3.Slider'],
    'Text': ['androidx.compose.material3.Text'],
    'OutlinedTextField': ['androidx.compose.material3.OutlinedTextField'],
    'Button': ['androidx.compose.material3.Button'],
    'Column': ['androidx.compose.foundation.layout.Column', 'androidx.compose.foundation.layout.*'],
    'Row': ['androidx.compose.foundation.layout.Row', 'androidx.compose.foundation.layout.*'],
    'Box': ['androidx.compose.foundation.layout.Box', 'androidx.compose.foundation.layout.*'],
    'Modifier': ['androidx.compose.ui.Modifier'],
    'Alignment': ['androidx.compose.ui.Alignment'],
    'dp': ['androidx.compose.ui.unit.dp'],
    'sp': ['androidx.compose.ui.unit.sp'],
    'Color': ['androidx.compose.ui.graphics.Color', 'android.graphics.Color'],
    'Composable': ['androidx.compose.runtime.Composable', 'androidx.compose.runtime.*'],
    'FlowRow': ['androidx.compose.foundation.layout.FlowRow', 'androidx.compose.foundation.layout.*'],
    'Arrangement': ['androidx.compose.foundation.layout.Arrangement', 'androidx.compose.foundation.layout.*'],
    'rememberLauncherForActivityResult': ['androidx.activity.compose.rememberLauncherForActivityResult'],
    'ActivityResultContracts': ['androidx.activity.result.contract.ActivityResultContracts'],
    'delay': ['kotlinx.coroutines.delay'],
    'LazyVerticalGrid': ['androidx.compose.foundation.lazy.grid.LazyVerticalGrid'],
    'GridCells': ['androidx.compose.foundation.lazy.grid.GridCells'],
    'Dialog': ['androidx.compose.ui.window.Dialog'],
    'Canvas': ['androidx.compose.foundation.Canvas', 'android.graphics.Canvas'],
    'animateFloatAsState': ['androidx.compose.animation.core.animateFloatAsState'],
    'Animatable': ['androidx.compose.animation.core.Animatable'],
    'AnimatedContent': ['androidx.compose.animation.AnimatedContent'],
    'graphicsLayer': ['androidx.compose.ui.graphics.graphicsLayer'],
    'drawBehind': ['androidx.compose.ui.draw.drawBehind'],
    'border': ['androidx.compose.foundation.border'],
    'rememberCoroutineScope': ['androidx.compose.runtime.rememberCoroutineScope', 'androidx.compose.runtime.*'],
    'Dispatchers': ['kotlinx.coroutines.Dispatchers'],
    'withContext': ['kotlinx.coroutines.withContext'],
    'BoxWithConstraints': ['androidx.compose.foundation.layout.BoxWithConstraints', 'androidx.compose.foundation.layout.*'],
    'PaddingValues': ['androidx.compose.foundation.layout.PaddingValues', 'androidx.compose.foundation.layout.*'],
    'TextOverflow': ['androidx.compose.ui.text.style.TextOverflow'],
    'TextAlign': ['androidx.compose.ui.text.style.TextAlign'],
    'LocalConfiguration': ['androidx.compose.ui.platform.LocalConfiguration'],
}
for f in kt:
    if not f.startswith('app/src/main/'):
        continue
    s = open(f, encoding='utf-8').read()
    imports = set(re.findall(r'^import\s+([\w.*]+)', s, re.M))
    body = re.sub(r'^import .*$', '', s, flags=re.M)
    body = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', body)
    for sym, ok in NEEDS.items():
        if re.search(r'(?<![\w.])' + re.escape(sym) + r'(?=\s*[(<{.,)]|\s+[a-z]|\s*$)', body) is None:
            continue
        if sym == 'dp' or sym == 'sp':
            if re.search(r'\d(?:\.\d+)?f?\.' + sym + r'\b', body) is None: continue
        if sym in ('Color',) and 'android.graphics.Color' in body and 'Color(' not in body.replace('android.graphics.Color', ''):
            continue
        if not any(i in imports for i in ok):
            # defined locally in the same file/package?
            if re.search(r'(?:fun|class|object)\s+' + sym + r'\b', body):
                continue
            if sym in ('Text', 'Row', 'Column', 'Box', 'Modifier', 'Color', 'Button', 'Slider', 'Switch', 'Alignment', 'Arrangement') and not re.search(r'(?<![\w.])' + sym + r'\s*[({.]', body):
                continue
            problems.append(f'{f}: uses {sym} without import ({ok[0]})')

# internal imports must resolve to something declared in the project; no references to removed modules
decl_names = {k[1] for k in decl}
for f in kt:   # top-level properties/constants (e.g. const val FOO) are valid import targets too
    for m in re.finditer(r'^(?:(?:public|internal) )?(?:const )?(?:val|var)\s+(?:[\w.<>?]+\.)?(\w+)', open(f, encoding='utf-8').read(), re.M):
        decl_names.add(m.group(1))
for f in kt:
    s_ = open(f, encoding='utf-8').read()
    for m in re.finditer(r'^import\s+(app\.orbit\.[\w.]+?)(\.\*)?$', s_, re.M):
        path, star = m.group(1), m.group(2)
        if star:
            if not any(re.search(r'^package\s+' + re.escape(path) + r'\b', open(g, encoding='utf-8').read(), re.M) for g in kt):
                problems.append(f'{f}: import of unknown package {path}.*')
        elif path.split('.')[-1] not in decl_names:
            problems.append(f'{f}: import {path} not declared anywhere in the project')
    for stale in ('RelayController', 'RelayService', 'app.orbit.network', 'app.orbit.core', 'HudOverlayService'):
        if stale in s_ and not f.startswith('legacy/'):
            problems.append(f'{f}: references removed component {stale}')

if problems:
    print('\n'.join(problems)); print(f'{len(problems)} problem(s)'); sys.exit(1)
print(f'repo check OK ({len(kt)} Kotlin files)')
