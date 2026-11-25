# Field test · 2026-11-25

Reference bike parked, engine idling (~1,150 rpm, revs to ~2,960). Moto G30, ELM327 v2.3 clone over
RFCOMM. Logs in `field-logs/2026-11-25/` (not committed).

## Protocol

- ISO 15765-4 CAN, 11-bit, 500 kbit/s. `ATSPA6` is accepted at once; no full protocol search.
- The ECU accepts the combined request `010C0D` (RPM and speed in one reply).
- The adapter accepts the single-reply count `010C0D1`, so it answers as soon as the ECU replies
  instead of waiting for other ECUs. The app now probes for it and falls back to `010C0D`, then to
  separate requests.

## Update rate

| Session | Request | Exchange latency (median) | RPM | Speed | Vitals refresh |
| --- | --- | --- | --- | --- | --- |
| 2026-11-24 | `010C` + `010D` | 73 ms | 6.4 Hz | 2.4 Hz | ~32 s |
| 16:49 debug build | `010C0D` | 76 ms | 10.6 Hz | 10.6 Hz | ~5.8 s |
| 17:00 debug build | `010C0D1` | 63 ms | 13.0 Hz | 13.0 Hz | ~5.6 s |
| 17:08 optimized build | `010C0D1` | 55 ms | 15.3 Hz | 15.3 Hz | ~5.5 s |

The app adds only 4–7 ms between a reply and the next request. The rest is the adapter and the
phone's Bluetooth stack; the optimized (non-debuggable) build shortened it by ~8 ms.

## Rendering: the main bottleneck now

`dumpsys gfxinfo`, 15 s on the dashboard while live:

| Build | Frames/s | Frame time p50 / p90 | Janky | GPU p50 | App CPU |
| --- | --- | --- | --- | --- | --- |
| debug | ~15 | 32 / 53 ms | 41 % | 13 ms | 59 % |
| optimized, no R8 | ~16 | 31 / 38 ms | 33 % | 15 ms | 37 % |

The screen runs at 90 Hz (11 ms per frame), so the needle moves in visible steps.

To do:
1. **Read animated values in the draw phase.** `animateReadings` is read in each panel's body,
   so every animation frame recomposes the whole display (texts, gauges, frame). Pass the animated
   tachometer value as a provider read inside the tachometer's `Canvas`, and let the speed text
   change only when the whole number does.
2. **Run the dashboard at 60 Hz.** GPU time (~15 ms) does not fit a 90 Hz frame; 60 Hz gives
   16.6 ms. Set the window's preferred refresh rate while the dashboard is shown.
3. **Stiffen the needle spring.** `ReadingSpring` (critically damped, MediumLow) trails a rising
   RPM by ~100 ms; StiffnessMedium brings that to ~50 ms with readings now every ~65 ms.
4. Re-measure frames per second and jank with `dumpsys gfxinfo` after each change.

## Release build crashes at startup

With the release `optimization` settings, R8 produces an `IllegalAccessError` (a Kotlin collection
class becomes inaccessible) in `ProcessLifecycleInitializer`, so a release build would crash on
launch. The new `profile` build type (release, debug-signed, R8 off) works around it for testing.
Fix the R8 configuration before shipping a release.

## Session keeps polling after the engine stops

When the ignition goes off the ECU stops answering, but the session keeps asking for RPM about 20
times a second and never leaves the live state: the lamp would stay green while the readings show
`--`. Treat a run of replies without data (for example 2 s) as a lost ECU: reconnect or report
"No ECU data".

## Power with the ignition off

- The diagnostic socket stays powered with the ignition off: the adapter answers Bluetooth and AT
  commands; only the ECU is silent (`UNABLE TO CONNECT`). An adapter left plugged in drains the
  battery even when the app is not connected.
- `ATRV` (now read on every connection): 13.0 V just after switching off, 14.3 V with the engine
  running (charging works).
- Typical ELM327 clones draw roughly 20–80 mA. Measure this one with a multimeter to know how many
  days the bike can stand with it plugged in; until then, unplug it when parking for more than a
  day or two, or use an adapter with a switch or sleep mode.

## Next tests on the bike

1. Validate the rendering fixes: engine idling, 1 min on the dashboard, `dumpsys gfxinfo`.
2. Passive CAN listening (`ATMA` with an `ATCRA` filter, read-only): check whether RPM, speed or
   gear frames reach the diagnostic socket. Needs a small test mode in the app first.
3. Ignition off while connected: check the new "lost ECU" handling.
4. A ride with a few seconds at steady speed in each gear, to calibrate gear from RPM/speed.
5. Optional: adapter current with a multimeter, and `ATRV` after the bike stood for some hours.
