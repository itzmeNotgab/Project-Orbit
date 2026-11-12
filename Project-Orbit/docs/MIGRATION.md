# ORBIT migration report (Phase 1)

## Existing project (Expo/React Native v0.2.0)
Works: 3D viewer (WebView software renderer), skin/cape/pack/world libraries with validation,
ORBIT accounts + sync backend (Express), Script API add-on generator, Minecraft detect/launch.
Missing: any Bedrock networking (RakNet, codecs, relay, forwarding).

## Plan
| Existing | Action |
|---|---|
| src/viewer, viewer-src (3D viewer) | Keep. Phase 7: load in Compose via WebView or port to native. |
| src/lib (png, skinpack, packs, ingame) | Port to Kotlin in Phase 7 (logic is self-contained). |
| backend/ | Keep as-is, reuse from the Kotlin client. |
| OrbitNativeModule.kt | Superseded by app/MinecraftInfo.kt (done). |
| Expo/RN shell, screens | Replace. Keep in `legacy/` until Phase 7 parity. |

## Done in Phase 1
Kotlin + Compose + Material 3 app, Gradle Kotlin DSL, `core` JVM module with tests, intro with skip,
animated dots, Minecraft detection, diagnostics, CI workflow. ARM64 only.

## Not verified
Not compiled in the authoring environment (no network, no Android SDK). First CI run is the real test.
Protocol 1.26.60.4 compatibility is unverified.
