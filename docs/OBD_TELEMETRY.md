# OBD-II Telemetry Inventory

## Field verification

R9T Dashboard queried the reference 2023 BMW R nineT Scrambler directly through the RFCOMM ELM327 adapter. The active protocol was ISO 15765-4 CAN, 11-bit identifiers at 500 kbit/s. All 41 advertised data requests in the table returned valid payloads with the engine running and the motorcycle stationary. The examples are one response per PID and were sampled in sequence. They record the ECU output at the time of the test.

The support pages `0100`, `0120`, `0140`, `0160`, and `0180` are discovery requests. The last response was `41 80 00 08 00 00`, so the ECU did not advertise `01A0`, standard actual gear (`01A4`), or standard odometer (`01A6`). The distance counters `0121` and `0131` have diagnostic meanings. Neither represents the motorcycle's total mileage. The local clock comes from the phone.

## Verified readings

The payload column lists the bytes after the `41 <PID>` response header. Numeric examples are rounded for readability. The 5 raw statuses are retained intact as hexadecimal payloads.

| Request | ECU parameter | Sample payload | App representation |
| --- | --- | --- | --- |
| `0101` | Monitor status since DTCs cleared | `00 05 60 00` | Raw status |
| `0103` | Fuel system status | `02 00` | Raw status |
| `0104` | Calculated engine load | `47` | 27.843 % |
| `0105` | Engine coolant temperature | `9B` | 115.0 °C |
| `0106` | Short-term fuel trim bank 1 | `7F` | -0.781 % |
| `0107` | Long-term fuel trim bank 1 | `76` | -7.813 % |
| `0108` | Short-term fuel trim bank 2 | `7E` | -1.563 % |
| `0109` | Long-term fuel trim bank 2 | `7C` | -3.125 % |
| `010A` | Fuel pressure | `7B` | 369.0 kPa |
| `010C` | Engine speed | `12 02` | 1152.5 rpm |
| `010D` | Vehicle speed | `00` | 0.0 km/h |
| `010E` | Ignition timing advance | `8C` | 6.0 ° |
| `010F` | Intake air temperature | `49` | 33.0 °C |
| `0111` | Throttle position | `22` | 13.333 % |
| `0113` | Oxygen sensor locations | `13` | Raw status |
| `0115` | Oxygen sensor 2 | `80 FF` | Voltage: 0.64 V |
| `011C` | OBD standard | `25` | Raw status |
| `011F` | Engine run time | `02 F1` | 753.0 s |
| `0121` | Distance with malfunction indicator on | `00 00` | 0.0 km |
| `012E` | Commanded evaporative purge | `00` | 0.0 % |
| `0130` | Warm-ups since DTCs cleared | `FF` | 255.0 count |
| `0131` | Distance since DTCs cleared | `0E 32` | 3634.0 km |
| `0133` | Barometric pressure | `4F` | 79.0 kPa |
| `0134` | Wideband oxygen sensor 1 | `7F DF 7F FE` | Equivalence ratio: 0.999 ratio, Current: -0.008 mA |
| `0138` | Wideband oxygen sensor 5 | `7F BF 7F FD` | Equivalence ratio: 0.998 ratio, Current: -0.012 mA |
| `0141` | Monitor status this drive cycle | `00 05 20 20` | Raw status |
| `0142` | Control module voltage | `38 4B` | 14.411 V |
| `0143` | Absolute engine load | `00 28` | 15.686 % |
| `0144` | Commanded equivalence ratio | `80 00` | 1.0 ratio |
| `0145` | Relative throttle position | `0C` | 4.706 % |
| `0147` | Absolute throttle position B | `22` | 13.333 % |
| `0148` | Absolute throttle position C | `23` | 13.725 % |
| `0149` | Accelerator pedal position D | `2F` | 18.431 % |
| `014A` | Accelerator pedal position E | `2F` | 18.431 % |
| `014D` | Time with malfunction indicator on | `00 00` | 0.0 min |
| `014E` | Time since DTCs cleared | `1D 2A` | 7466.0 min |
| `0156` | Long-term secondary oxygen sensor trim bank 1 | `7F` | -0.781 % |
| `0158` | Long-term secondary oxygen sensor trim bank 2 | `7F` | -0.781 % |
| `015A` | Relative accelerator pedal position | `00` | 0.0 % |
| `015C` | Engine oil temperature | `88` | 96.0 °C |
| `018D` | Throttle position G | `23` | 13.725 % |

Parameter names follow the generic SAE definitions. In particular, the field named **Engine coolant temperature** reflects how the ECU reports PID `0105`. This capture establishes the decoded value for that PID. It does not identify the physical sensor BMW uses. [SAE J1979 Digital Annex](https://saemobilus.sae.org/standards/j1979da_202305-j1979-da-digital-annex-e-e-diagnostic-test-modes)

## Using the readings in a screen

Observe `DiagnosticSession.state` through the ViewModel. In `DiagnosticState.Live`, use `engineRpm` and `speedKmh` for the dashboard signals. Use `telemetry.advertisedPids` to check ECU support and `telemetry.readings[pid]` for all additional parameters in the table. Live polling targets a 120 ms RPM cycle and publishes each RPM reading immediately. Between RPM requests the session can issue one secondary request. Speed is eligible for refresh after 250 ms and one additional PID is eligible after 600 ms, rotating through the supported sensors. Requests remain serialized. Each additional reading carries its own monotonic timestamp. The map continues to refresh as long as the session is live. It starts empty, so an advertised PID without a map entry is awaiting its first sample.

- `NUMERIC` includes one or more named measurements and units, plus the original payload.
- `RAW` includes a valid payload for a status or enum.
- `NO_DATA` and `INVALID_RESPONSE` indicate that the current request did not produce a usable reading. A numeric zero is a valid value.

Disconnect and failure states carry no live readings. The exported session log remains available for reviewing exact requests and raw adapter replies.

## Field update rate

On the Moto G30 with the same ELM327 and ISO 15765-4 CAN connection, the median interval between RPM responses fell from 505 ms (250 intervals) to 168 ms (253 intervals), equivalent to approximately 2.0 and 6.0 samples/s respectively. The median ELM327 RPM exchange was 72 ms before the scheduling change and 81 ms after it. The optimized capture still included intervals up to 536 ms. These are session-log timings measured on the phone, without display rendering latency.
