# Test checklist (do in this order)

1. GitHub Actions: repo check, `:library:test`, debug APK and the native job are green.
2. Install on the phone. Intro shows and can be skipped; four tabs only; profile button top right.
3. Home: Minecraft detected with version; Launch shows the ORBIT splash then starts Minecraft.
4. Customize: open it first. A character must appear with NO cosmetics selected within a few seconds. If not, an on-screen
   message appears; also check Settings > 3D viewer (page loaded / bridge ready / first frame) and `adb logcat -s OrbitViewer chromium`.
   Then rotate by dragging, pinch to zoom, rotate the phone. 3D character renders. Head/Back/Cape/Pets galleries: tap equips and shows EQUIPPED, same-slot replaces,
   search and filters work, save/apply/delete a loadout, import a cape PNG, cape physics sliders move the cape.
5. Library > Skins: import PNG, preview, build .mcpack, Minecraft opens it.
6. Library > Addons / Textures: import files, check messages (wrong tab, engine version warning), Open in Minecraft, Remove.
   Create the ORBIT branding pack, enable it in Minecraft, see whether the title logo changes (unverified).
7. Library > Worlds: import .mcworld, backup, export, open in Minecraft.
8. Settings: change theme, glass Off/On, animations, storage numbers, target version field.
9. Account (needs your backend over https): sign up, upload, download.
Report anything that fails with the screen and what you saw.
