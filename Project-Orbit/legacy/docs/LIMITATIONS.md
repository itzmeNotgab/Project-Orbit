# ORBIT — what is and is not possible

ORBIT is a normal Android app. Android does not let one app inject code into another, and Minecraft Bedrock has no public client-mod API. Everything below follows from that.

## Works inside ORBIT
- Real-time 3D character, cosmetic, cape and pet previews (own software renderer in a WebView, no third-party assets).
- Skin, cape, pack and world libraries with file validation (PNG size/shape, zip + manifest checks, level.dat).
- ORBIT accounts and cosmetic sync (needs the server in `backend/`).
- Detecting the installed Minecraft version and launching it (native Kotlin module).

## Works through Minecraft's own import path
ORBIT hands .mcpack / .mcaddon / .mcworld files to Minecraft with Android's file-open intent. Minecraft imports them itself. Skins are delivered as standard skin packs.

## Not possible (and not faked)
- Listing worlds already inside Minecraft (Android 11+ scoped storage blocks Android/data).
- Enabling/disabling packs or changing pack priority from outside the game (done per world in Minecraft).
- Changing the equipped skin from outside the game.
- Showing ORBIT cosmetics, capes or pets inside vanilla Minecraft, or to other players. Needs an ORBIT-compatible client.
- A draggable in-game overlay, in-menu search, FPS counter, or custom crosshair. Stable Script API does not expose them.
- Pets walking beside the player in the game (previews only).

## In-game add-on (Script API)
Generated .mcaddon provides an action-bar HUD (coordinates, direction, time, speed, TPS, day, dimension), an ORBIT menu item (form menu with categories, toggles, color) and per-player saved settings. Targets @minecraft/server 2.1.0 and @minecraft/server-ui 2.0.0 (see `src/lib/ingame.ts` to change).

## Rules followed
No cheats or server-rule bypasses. No Flarial or Atlas code or assets. Passwords are stored only as scrypt hashes.
