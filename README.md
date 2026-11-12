# Guitar Tuner

An Android guitar tuner for 6-string standard tuning (E2 A2 D3 G3 B3 E4). Play any string: the app
detects which one it is and shows how many cents sharp or flat it is until it's in tune.

See [docs/PLAN.md](docs/PLAN.md) for the full plan and roadmap.

## Features (v0.1)

- Automatic string detection, with an optional manual lock (tap a string button; tap again to unlock).
- Needle bubble on a ±50 cent track: red beyond 15 cents, amber within 15, green within 5.
- A string counts as in tune after 0.5 s within ±5 cents: the bubble fills green, the string gets a
  check mark, and the phone gives a short vibration.
- Fully offline: the only permission is the microphone, and audio never leaves the phone.
- A small readout at the bottom shows the detected frequency, input level, and audio source, for
  checking how a given phone's mic behaves.

## Build and run

Requirements: Android Studio (or JDK 17+ and the Android SDK), and a phone running Android 8.0+.

1. On the phone, enable **Developer options** and **USB debugging**, then plug it in.
2. Open the project in Android Studio and press **Run**, or from a terminal:

   ```sh
   ./gradlew installDebug
   ```

Unit tests (pitch detection, string matching, tuning logic) run on the PC, no phone needed:

```sh
./gradlew testDebugUnitTest
```

If the repo is inside a OneDrive folder, builds can fail with "Unable to delete directory": OneDrive
marks new build folders read-only and locks them while syncing. Either move the repo out of
OneDrive, or add a line like this to `local.properties` to build outside the project folder:

```properties
buildRoot=C\:/Users/<you>/AppData/Local/GuitarTunerBuild
```

## How it works

| Package | What it does |
| --- | --- |
| `audio/` | Opens the mic with `AudioRecord` (raw `UNPROCESSED` source when the phone supports it) and emits 4096-sample frames every 2048 samples. |
| `dsp/` | YIN pitch detection and a median + exponential smoother. Pure Kotlin. |
| `tuning/` | String targets, nearest-string matching with hysteresis, the in-tune hold timer, and `TunerEngine`, which ties the pipeline together. Pure Kotlin. |
| `ui/` | Jetpack Compose screens: the headstock, the needle bubble, string buttons, and the permission flow. |
