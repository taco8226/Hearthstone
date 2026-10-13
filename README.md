# Hearthstone

Android app for controlling a Proflame Connect gas fireplace over the local network.
It talks directly to the Proflame WiFi module, so it needs no cloud account. It only works
on the home WiFi.

- **App:** power, thermostat (target temperature), flame/fan/light levels (0–6), split flow,
  aux, continuous pilot, and room temperature, with Toasty, a flame-spirit mascot who reacts to the fire.
  **Settings** picks the theme (System/Light/Dark, optional wallpaper colors), switches °F/°C,
  reorders tiles by dragging and shows or hides them, and sets the module IP.
- **Home screen widget:** ON/OFF, Split toggle, and −/+ steppers for flame, fan and light.
- **Notification:** appears while the fireplace is on, with Turn off / Flame − / Flame +.
- **Thermostat safety:** the room reading comes from the remote, which mostly reports when the
  temperature changes but can miss changes. In thermostat mode, if the burner burns for 15 minutes with
  no new reading, the app turns the fireplace off and says why. An idle burner is left alone. This runs
  in the notification's service, so it needs notifications allowed.
- **Remote takeover:** after the remote turns the fireplace on, it re-sends its settings about twice a
  second until it turns it off, undoing app changes. The app detects this and says so.

## Protocol

WebSocket at `ws://<module-ip>:88/`.

1. After connecting, send `PROFLAMECONNECTION`. The module replies `PROFLAMECONNECTIONOPEN` plus a JSON state snapshot.
2. Keep the connection alive with `PROFLAMEPING` (reply `PROFLAMEPONG`) every 30 s.
3. Control the fireplace with JSON objects, e.g. `{"main_mode":1}` (on), `{"main_mode":0}` (off),
   `{"flame_control":4}`, `fan_control`, `lamp_control`, `split_flow`, `auxiliary_out`, `pilot_mode`.
   `{"main_mode":2}` is thermostat mode, holding `temperature_set` (tenths of a degree in the module's
   unit, rounded by the module to whole degrees). The module lights and idles the burner itself.
   `{"temperature_unit":1}` switches the module to °F (0 = °C); it converts its stored values at once.
4. The module pushes the changed fields back as JSON.
5. `room_temperature` is in tenths of a degree, but always whole degrees in the module's unit.
   With the module in °C, readings and set points are whole °C, so set it to °F for 1°F steps.
   The reading comes from the handheld remote's thermometer. The module pushes it only when the remote
   reports a change, so it can go quiet for many minutes. The app shows how long ago it last updated.
6. `sh_memory_1` is undocumented. It reads 32672 whenever the burner is out, including while the thermostat
   idles, 32678 while it burns, and passes through other values while it lights.

Based on [homebridge-proflame](https://npmjs.com/package/homebridge-proflame).

## Build and install

Optionally, set the module's address as the default in `local.properties`. This file isn't
committed. Without it, enter the address in the app's Settings.

```properties
fireplace.host=<module-ip>
```

Then, with the phone connected and USB debugging enabled:

```fish
cd ~/Projects/Hearthstone
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
- `ui/`: Compose screens (main tiles, settings, mascot), settings model, temperature conversion.
- `widget/HearthstoneWidget.kt`: Glance widget.
- `notify/FireplaceService.kt`: foreground service and notification.
- `design/`: source art: icon (color and monochrome) and the mascot.

## License

MIT. See [LICENSE](LICENSE).
