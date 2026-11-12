# Phases 3 and 4: why they are not implemented yet

Facts found (web search, dated Nov 2026):
- Minecraft Bedrock 1.26.60 uses network protocol **2225** (Mojang bedrock-protocol-docs, 2026-10-27).
  Your target 1.26.60.4 is a hotfix of that line; its protocol number must be confirmed with the
  Ping button (Relay tab) against a server running your exact version.
- The maintained Cloudburst-derived protocol libraries are published as `org.allaymc.protocol:bedrock-codec`
  and `bedrock-connection` (Apache-2.0, versioned by Minecraft version, e.g. 1.26.30-R2). I could not
  find a 1.26.60 build from that group. A 1.26.60-R1 build exists under `io.github.ryzemcbe:bedrock-codec`
  (Nov 2, 2026), a third-party fork I cannot vouch for. It would sit in the path of your login, so I did
  not wire it in.
- Raw transport: `org.cloudburstmc.netty:netty-transport-raknet` (snapshot builds only).

What is needed to finish (needs a machine that can compile and a verified artifact):
1. Choose and audit a codec artifact that contains protocol 2225 (or build AllayMC/Protocol at the
   1.26.60 tag yourself and publish it to GitHub Packages / mavenLocal).
2. Terminate RakNet on both sides (server channel for Minecraft, client channel upstream), forward
   Bedrock packets through listeners.
3. Xbox/Microsoft device-code login upstream, ECDH session encryption. Needs a registered client ID.
4. Run against a real server and a real 1.26.60.4 client; report the exact failing stage.
Writing this blind would produce code that cannot be checked until you test, so it is deliberately not included.
