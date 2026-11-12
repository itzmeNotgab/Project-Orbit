# Version compatibility

A compatibility profile is flat JSON for ONE exact game version:

    {"version":"1.26.60.4","hook.render.frame":"FD 7B ?? A9|0","hook.player.tick":"...|4"}

`PATTERN|offset`: hex bytes, `??` is a wildcard, offset is added to the match address.
Rules enforced by the runtime: wrong version => every hook fails ("profile is for X, game is Y"); no match => "signature not found";
more than one match => "ambiguous"; malformed => "malformed signature". Failed hooks make dependent modules unsupported.

**Status for 1.26.60.4: no profile exists.** Signatures must be derived from that exact game library (or taken from a
project that already supports it, respecting its license). Until then every module reports unsupported. This is intentional.

Hooks the current modules need: `render.frame`, `player.tick`, `world.time`, `input.key`, `net.ping`.
