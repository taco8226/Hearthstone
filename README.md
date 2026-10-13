# Hearthstone

Android app for controlling a Proflame Connect gas fireplace over the local network.
It talks directly to the Proflame WiFi module, so it needs no cloud account. It only works
on the home WiFi.

- **App:** power, flame/fan/light levels (0–6), split flow, aux, continuous pilot, and
  room temperature. **Settings** shows/hides controls, switches °F/°C, and sets the module IP.
- **Home screen widget:** ON/OFF, Split toggle, and −/+ steppers for flame, fan and light.
- **Notification:** appears while the fireplace is on, with Turn off / Flame − / Flame +.

## Protocol

WebSocket at `ws://<module-ip>:88/`.

1. After connecting, send `PROFLAMECONNECTION`. The module replies `PROFLAMECONNECTIONOPEN` plus a JSON state snapshot.
2. Keep the connection alive with `PROFLAMEPING` (reply `PROFLAMEPONG`) every 30 s.
3. Control the fireplace with JSON objects, e.g. `{"main_mode":1}` (on), `{"main_mode":0}` (off),
   `{"flame_control":4}`, `fan_control`, `lamp_control`, `split_flow`, `auxiliary_out`, `pilot_mode`.
4. The module pushes the changed fields back as JSON.
5. `room_temperature` is in tenths of a degree. `temperature_unit` 0 is assumed to mean °C.

Based on [homebridge-proflame](https://npmjs.com/package/homebridge-proflame).

## Build and install

Optionally, set the module's address as the default in `local.properties`. This file isn't
committed. Without it, enter the address in the app's Settings.

```properties
fireplace.host=<module-ip>
```

Then, with the phone connected and USB debugging enabled:

```fish
cd ~/AndroidStudioProjects/Hearthstone
set -x JAVA_HOME /opt/android-studio/jbr
gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Notes for this machine:
- The build uses the CachyOS `gradle` package, not `./gradlew`. The VPN blocks the wrapper's
  GitHub download.
- `~/.gradle/gradle.properties` forces IPv4, because IPv6 is broken on the LAN.
- The SDK is at `~/Android/Sdk`.

## Layout

- `data/ProflameClient.kt`: live WebSocket client used by the app and the notification.
- `data/ProflameOneShot.kt`: connect / read / send / disconnect, used by the widget.
- `ui/`: Compose screen, settings, temperature formatting.
- `widget/HearthstoneWidget.kt`: Glance widget.
- `notify/FireplaceService.kt`: foreground service and notification.
- `design/`: source icon art (color and monochrome).
