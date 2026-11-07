# Phase 2 report

## Implemented
- `network` module (pure Kotlin/JVM, no third-party deps):
  - `RakNet`: unconnected ping/pong encode/parse with magic validation.
  - `ServerProbe` + `ServerInfo`: pings a real server and reads its advertised protocol number and version.
  - `UdpRelay`: local UDP relay, one upstream socket per client, unchanged forwarding both ways,
    session limit, idle expiry, per-direction stats, clean failure on bad target.
- App: `RelayService` (foreground service), `RelayController`, Relay tab (ping, start/stop, live stats).
- Tests: `RakNetTest` (4), `UdpRelayTest` (7). CI runs `:core:test :network:test`.

## Not implemented (deliberately)
- RakNet connection (reliability, frame sets) and Bedrock codecs. The relay does not read game packets.
  Plan for Phase 3: use Cloudburst `netty-transport-raknet` + `bedrock-codec`
  (Apache-2.0, versions to be verified against the 1.26.60.4 protocol before adding).
- Authentication and encryption. Not started.
- Protocol compatibility with 1.26.60.4: UNVERIFIED. The Ping button reports a server's real protocol
  number; compare it with what Minecraft reports before assuming anything.

## Unverified
- Not compiled or run in the authoring environment (no Android SDK / Kotlin compiler).
- Whether Minecraft on your device accepts 127.0.0.1 as a server is untested.
- Android may restrict the foreground service if battery optimisation is aggressive on TECNO/HiOS.
