# Connection options and safety

Goal: faster engine RPM and vehicle speed without any risk to the bike. Every other reading may
lag by seconds. Baseline on the reference R nineT (2026-11-25): ISO 15765-4 CAN 11/500, ELM327
v2.3 clone over Bluetooth Classic, single-reply `010C0D1`, 55 ms per exchange, 15.3 readings/s.

## Is it safe for the ECU?

What the app sends today:

- **Mode 01 requests only** ("show current data", SAE J1979 / ISO 15031-5). They read values the
  ECU must provide for emissions diagnostics; they change nothing in the ECU.
- **AT commands**, which configure the adapter itself and are not sent to the bike.
- **One request at a time.** The app waits for each reply (or its timeout) before the next request,
  the same pattern scan tools use. P2, the time the ECU has to answer, has been 50 ms in OBD
  protocols since the mid-1990s; our ~55 ms per exchange matches it rather than pushing it.
- **Bus load is tiny.** One request and one reply frame per exchange, ~15 exchanges a second:
  roughly 4 kbit/s of a 500 kbit/s bus, under 1 %.

Polling Mode 01 cannot overheat, wear or "burn" an ECU; the worst a busy ECU does is answer
later. The real risks are elsewhere:

1. **Commands that change things.** Mode 04 (clear trouble codes, resets readiness monitors), Mode 08
   (test control), and UDS services such as diagnostic sessions (0x10), security access (0x27),
   writes (0x2E), routines (0x31) and programming (0x34+) can change ECU state. The app never sends
   them; a command allowlist in the transport would make that impossible by mistake.
2. **Polling a silent bike.** When the ignition goes off the session keeps sending requests about
   20 times a second (seen in the 2026-11-25 logs). Traffic on the bus can keep control units awake
   and drain the battery. The session should stop after ~2 s without data.
3. **The adapter stays powered with the ignition off** (confirmed: it answers AT commands, ATRV
   13.0 V). Unplug it when parking for more than a day or two, or use one with a switch or sleep.
4. **Listening with a clone.** Genuine ELM327 v1.4b+ can listen silently (`ATCSM1`), but clones may
   claim any version and still acknowledge frames ([Linux can327 driver notes](https://docs.kernel.org/6.11/networking/device_drivers/can/can327.html)).
   An extra acknowledging node is normal CAN behaviour and adds no traffic, but keep any listening
   test short and supervised.

## Ways to get RPM and speed faster

### A. Same adapter, software only (safe, small gains)

| Change | Expected gain | Notes |
| --- | --- | --- |
| Write the session log in the background with a buffer | +6–11 % readings | Measured: 13.8–14.6 vs 15.5 /s with logging on/off |
| Read the Bluetooth socket in blocks instead of byte by byte | a few ms per exchange | Fewer calls into the Bluetooth stack per reply |
| Fewer secondary reads (e.g. one every 2–3 s instead of every 1 s) | +3–6 % readings | Each secondary read takes one RPM slot |
| `ATS0` (no spaces in replies) | ~1–2 ms per exchange | Shorter replies; test that this clone honours it |

Realistic ceiling with this clone over Bluetooth: ~17–19 readings/s. Most of the 55 ms is the
Bluetooth round trip and the clone itself.

### B. Listen instead of asking (potentially the biggest gain, unproven)

The engine ECU and the instrument cluster normally exchange RPM and speed (and likely gear) as
periodic CAN frames, often 20–100 times a second. If those frames reach the diagnostic socket,
`ATMA` with an `ATCRA` filter streams them with no requests at all, and could also solve the gear.
Unknowns: whether the R nineT's socket is behind a gateway that only passes diagnostic replies (no
public data found for BMW motorcycle buses), and which frame IDs carry what. Test: a few seconds of
filtered listening with the engine idling, bike stationary, supervised; nothing is sent to the bus.

### C. Better adapter

STN-based adapters (OBDLink MX+/LX) answer much faster than ELM327 clones: owners report up to ~47
PIDs/s over Bluetooth versus ~20 with cheap ELM327s, and the vendor quotes up to 100 PIDs/s
([OBDLink MX+](https://Scantool.net/obdlink-mxp), [HP Tuners forum](https://www.hptuners.com/forum/showthread.php?p=430285)).
They also implement silent monitoring reliably, which makes option B safer. MotoScan names the
OBDLink LX and MX as suitable for BMW motorcycles with K-Line and CAN
([MotoScan](https://play.google.com/store/apps/details?id=de.wgsoft.motoscan&hl=en)). A wired USB adapter has
the lowest latency but needs a cable to the handlebar.

### D. BMW-specific requests

Manufacturer services (UDS ReadDataByIdentifier, 0x22) may expose more data, possibly the gear, but
they are still one request per reply: no speed gain, and they need IDs that are not public. Only
read services would be acceptable; never sessions, security access or writes.

## Recommended order

1. Safety guards: a command allowlist in the transport, and stop polling when the ECU goes silent.
   **Done:** `ElmCommandPolicy` lets through only the adapter's own AT commands and Mode 01 read
   requests, checked in `ElmChannel` before anything is logged or sent; anything else ends the
   session with `COMMAND_BLOCKED`. `EcuSilenceWatch` ends a live session after 2 s without RPM or
   speed (`NO_ECU_DATA`), and that error no longer triggers reconnects.
2. Option A, then measure on the bike.
3. One supervised listening test (option B) to learn whether the socket carries broadcast frames.
4. If more speed is still wanted, an STN-based adapter (option C), which also improves option B.
