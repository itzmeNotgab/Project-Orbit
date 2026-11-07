# Test checklist (do in this order)

1. GitHub Actions: `Android build` is green (core, network, library tests + debug APK).
2. Install APK on the TECNO POVA 4. Intro shows and can be skipped.
3. Home/Diagnostics: Minecraft detected, version shown.
4. Customize: 3D character appears; toggle cosmetics, cape, pet, animations, camera; import a custom cape.
5. Library > Skins: import a PNG, preview; build .mcpack, Minecraft opens and offers to import it.
6. Library > Packs: import an .mcpack/.mcaddon, validation text is sensible; Open hands it to Minecraft.
7. Library > Worlds: import .mcworld, backup, export, open in Minecraft.
8. Relay: Ping a real server (note its protocol number). Start relay, add 127.0.0.1:19133 in Minecraft,
   confirm packet counters rise. Note whether the service survives with Minecraft in front (HiOS battery settings).
9. Modules: toggle modules, save/load a profile, favorites; restart the app, state persists.
10. HUD: drag/resize/recolor; grant overlay permission; enable overlay; start Minecraft; check it draws.
11. Account (needs your backend over https): sign up, upload, download loadout.
Report any failure with the screen and what you saw.
