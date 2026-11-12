# Testing checklist (do these on the phone)

Nothing in this repo has been run on a device yet. Work through this list and report failures with the CI log or a screenshot.

1. **Build**: Actions run is green and the `orbit-apk` artifact installs.
2. **Launch**: intro plays (toggle it off in Settings, restart, it is skipped). Accent color persists.
3. **Home**: shows Minecraft version (or "not found"); Launch Minecraft opens the game.
4. **3D viewer** (Cosmetics): character appears; drag rotates; pinch zooms; Animation and Camera chips work. Equip each category; try Walk/Run with a cape and a pet.
5. **Capes**: import a 64x32 PNG; physics on/off; stiffness/sway steppers.
6. **Skins**: import a 64x64 PNG, toggle slim arms, "Make .mcpack" opens Minecraft's import.
7. **Addons / Textures**: import a .mcpack and a .mcaddon; check version, compatibility line, Install in Minecraft.
8. **Worlds**: import a .mcworld; Backup; Open in Minecraft.
9. **In-Game**: create the add-on, import, enable both packs on a test world, use the ORBIT item.
10. **Account**: deploy `backend/` (see its README), set the URL in Settings, sign up, upload/download loadout.

Known untested areas: texture orientation in the 3D viewer, the Kotlin native module build, expo-file-system API calls in `src/lib/storage.ts`, Minecraft accepting the VIEW intent, Script API version numbers.
