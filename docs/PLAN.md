# Guitar Tuner — Project Plan

Last updated: Nov 11, 2026

## Overview

v1 is a native Android guitar tuner, locked to 6-string standard tuning (E2 A2 D3 G3 B3 E4). It listens through the phone's mic, works out which string is being played, and shows how many cents sharp or flat it is until the string is in tune. It starts as a personal app on your own phone and ships publicly on Google Play once it has proven itself.

**Goals for the personal build**

- Auto-detect the string being played. The user never has to pick a string first.
- Accuracy within ±1 cent on a clean, sustained note. The "in tune" window is ±5 cents (to be confirmed).
- Visual feedback that responds in under 100 ms and holds steady, without a jittery needle.
- A headstock UI modeled on the reference screenshot, running on your phone.

**Added goals for the public release**

- Listed on Google Play, with original art, a privacy policy, and a completed Data safety form.
- Works on a range of Android phones (brands, Android versions, mic quality), not just yours.
- Audio never leaves the device. The app requests no network permission and collects no data.

**Non-goals**

- iOS, web, or desktop versions.
- Alternate tunings (Drop D, DADGAD, half-step down), 7-string, bass, ukulele.
- Accounts, a backend, or analytics.

## User experience

The user opens the app, grants mic access once, and plucks a string. From then on the app runs hands-free.

1. **First launch:** a short prompt explains why the app needs the mic, then Android's microphone permission dialog appears. If the user denies access, the app shows a screen explaining why, with a button that opens the app's system settings.
2. **Idle:** the top line reads "Start tuning by playing any string." The indicator bubble is empty, and all six string buttons are neutral.
3. **String detected:** the matching string button (for example, A) lights up. The bubble shows the note name and the needle moves left (flat) or right (sharp).
4. **Tuning:** the user turns the peg. The needle and the cents readout follow live, and a hint reads "Tune up" or "Tune down".
5. **In tune:** once the string stays within tolerance for about 0.5 s, the bubble turns green and the string button gets a check mark. A short vibration is optional.
6. **Next string:** the user plays another string. The detected string switches automatically, and strings already tuned keep their check mark.
7. **Silence:** after about 1.5 s of no signal, the needle fades back to the idle state.

Tapping a string button could lock detection to that string, which helps in a noisy room. This is an optional manual override and is listed under Open questions.

## UI design

The layout copies the reference screenshot: a dark grid background, a 3+3 acoustic headstock in the center, string buttons beside the tuning pegs, and an indicator bubble on a stem above the headstock.

| Component | Behavior | Reference match |
| --- | --- | --- |
| Status line | "Start tuning by playing any string." → "Tune up" / "Tune down" / "In tune" | Top text |
| Indicator bubble | Shows the detected note. Ring color goes red → amber → green as cents approach 0. | Circle on a stem |
| Pitch needle | The bubble slides along a horizontal track (±50 cents) above the headstock, and its stem tilts toward the offset | New, not in the screenshot |
| Cents readout | A signed number such as "−12" under or inside the bubble | New |
| String buttons | D A E on the left, G B E on the right, matching peg positions. States: idle, active (highlighted), tuned (check mark). | Gray circles |
| Headstock art | Drawn with Compose Canvas or a vector drawable, with pegs and strings. The active string glows or vibrates. | Center image |

**Visual states**

- Idle: gray bubble, no needle movement.
- Out of tune (more than 15 cents off): red ring, with an arrow pointing the direction to turn.
- Close (5–15 cents off): amber ring.
- In tune (within 5 cents, held for 0.5 s): green fill and a check mark on that string's button.

**Art assets:** the headstock in the screenshot comes from another app, so we will draw our own, either drawn in code or as commissioned or self-made art. We keep the layout idea and not the artwork. Placeholder art is fine for the personal build, but original art is required before the Play release. Open question: are you supplying art, or should we build a clean vector headstock?

## Audio and pitch detection

The plan is to use the YIN algorithm, or its faster variant McLeod (MPM), on mic frames of about 4096 samples at 44.1/48 kHz. Plain FFT peak-picking isn't accurate enough here: low E (82 Hz) has a weak fundamental, so FFT often locks onto the 2nd harmonic and reads the note an octave high.

**Pipeline, run on every audio frame**

1. **Capture:** Android AudioRecord, mono at 48 kHz, read on a background thread. Use the UNPROCESSED audio source where the phone supports it, and VOICE_RECOGNITION otherwise. That skips echo cancellation, noise suppression, and auto-gain, which all distort pitch.
2. **Buffer:** a 4096-sample window with 50% hop, which gives a new reading about every 45 ms. That window is long enough for 2+ periods of E2 (12.1 ms period).
3. **Noise gate:** skip frames whose RMS falls below a threshold, so room noise doesn't move the needle.
4. **Pitch estimate:** YIN/MPM returns a frequency and a confidence (clarity) score. Frames under about 0.9 clarity are dropped.
5. **Octave-error guard:** if the estimate is about 2× or 0.5× the previous stable pitch, check the sub-harmonic before accepting it.
6. **Smoothing:** take the median of the last 3–5 readings, then apply light exponential smoothing for display. This is what keeps the needle from jittering.
7. **Output:** {frequency Hz, confidence, timestamp} goes to the string-matching layer.

**Pluck transient:** the first 50–100 ms after a pluck is noisy and slightly sharp. Ignore readings during that time, or weight them lower.

**Library vs. hand-rolled:** TarsosDSP (Java, includes YIN and MPM) runs on Android, but it is GPL-licensed, which matters once the app is public (confirm the license before relying on it). YIN is about 80 lines to write ourselves in Kotlin. The recommendation is our own implementation, which is small, testable with plain JUnit, and free of license questions. We'd use TarsosDSP only as a benchmark during development.

## String auto-detection and cents

The detected frequency maps to whichever standard-tuning string is closest on a log (cents) scale. The boundary between two strings sits at the geometric midpoint of their frequencies. All values below use A4 = 440 Hz.

| String | Note | Target (Hz) | Detected if frequency is in (Hz) |
| --- | --- | --- | --- |
| 6 (thickest) | E2 | 82.41 | < 95.2 |
| 5 | A2 | 110.00 | 95.2 – 127.1 |
| 4 | D3 | 146.83 | 127.1 – 169.6 |
| 3 | G3 | 196.00 | 169.6 – 220.0 |
| 2 | B3 | 246.94 | 220.0 – 285.3 |
| 1 (thinnest) | E4 | 329.63 | > 285.3 |

The offset shown to the user is in cents (1/100 of a semitone):

```math
\text{cents} = 1200 \cdot \log_2\left(\frac{f_{\text{detected}}}{f_{\text{target}}}\right)
```

**Rules**

- **Hysteresis:** once a string is locked, it stays locked until the pitch passes the boundary by 30+ cents, or until silence is followed by a new pluck. This stops the display flickering between B and G near 220 Hz.
- **Valid range:** ignore pitches below 70 Hz or above 400 Hz. Harmonics above the 12th fret are out of scope.
- **Badly detuned strings:** a string more than half a gap off (for example, an A string slack enough to read under 95 Hz) will show up as the neighboring string. Restringing is the main case for this. The manual string lock in the UI covers it.
- **Display clamp:** the needle covers ±50 cents. Beyond that it pins to the edge and shows an arrow.
- **In-tune check:** within ±5 cents for 0.5 s straight (tolerance and hold time to be confirmed).

## Architecture and tech stack

The recommendation is a native Android app in Kotlin with Jetpack Compose. Audio is captured with AudioRecord on a background thread, and pitch detection is a pure Kotlin module. Going native gives the lowest mic latency and the most direct control over the audio source, which is what a tuner depends on.

| Option | Pros | Cons |
| --- | --- | --- |
| **Kotlin + Jetpack Compose** (recommended) | Google's standard Android stack. Direct AudioRecord access with low latency. Compose Canvas suits the headstock and needle. Simplest path to Play. | Android only. A later iOS version would mean rewriting the UI and audio layers. |
| Kotlin + Oboe (C++ audio) | Lowest possible latency | Adds C++ and the NDK. A tuner doesn't need sub-20 ms latency. Keep it as a fallback if AudioRecord is too slow on some phones. |
| Flutter | One codebase for Android and iOS | Mic streaming and DSP need plugins or platform channels. More moving parts for an Android-only goal. |
| React Native | Cross-platform, in JS/TS | Same plugin problem, plus real-time audio crossing the JS bridge |

**Proposed modules**

- `audio/`: microphone permission handling, AudioRecord setup, and a capture loop that emits frames as a Kotlin Flow.
- `dsp/`: pure Kotlin YIN/MPM, noise gate, and smoothing. It has no Android dependencies, so its tests run on your PC under plain JUnit.
- `tuning/`: the string table, nearest-string matching with hysteresis, cents math, and the in-tune hold timer.
- `ui/`: a TunerViewModel exposing a StateFlow of {detectedString, cents, status, tunedStrings}, plus Compose screens: Headstock, StringButton, IndicatorBubble, StatusLine, PermissionScreen.

**Stack:** Kotlin, Jetpack Compose, Coroutines/Flow, Gradle (Kotlin DSL), JUnit for DSP tests, and Compose UI tests on an emulator. Builds target SDK 36 (Android 16), the version on your Galaxy S23 (One UI 8.5). minSdk stays at 26 (Android 8.0) so the public release still reaches older phones.

**Distribution:** the personal build is a debug or self-signed APK installed from Android Studio. The public build is a signed App Bundle (.aab) uploaded to the Play Console.

## Milestones

The build has six phases. Phases 1–5 produce the personal app on your phone, and phase 6 turns it into a public Play Store release. Each phase ends on a check you can actually run. No dates are set yet.

1. **Project setup:** Android Studio project with Compose, ktlint, and GitHub Actions running unit tests on push.
   - Exit: the empty app installs on your phone over USB, and `./gradlew test` passes.
2. **Pitch engine (no UI):** YIN/MPM, noise gate, smoothing, string matcher, and cents math, all as pure Kotlin.
   - Exit: unit tests pass on synthetic tones for all 6 strings at −50, −10, 0, +10, +50 cents, with error under 1 cent.
3. **Live mic prototype:** the permission flow, the AudioRecord loop, and a bare debug screen showing Hz, note, and cents.
   - Exit: on a real guitar, your S23 detects every open string correctly and holds steady within ±2 cents on a sustained note. Also confirm whether the S23 supports the UNPROCESSED audio source, since Samsung can apply its own mic processing.
4. **Headstock UI:** the components from UI design, with the visual states and in-tune hold.
   - Exit: a full 6-string tune-up using only the app, checked against a second tuner.
5. **Personal release:** app icon, portrait layout tuned for your screen, and a signed release APK you install yourself.
   - Exit: you use it as your only tuner for 2 weeks without needing to fall back to another one.
6. **Public release on Google Play:** original art, accessibility (TalkBack labels, a text cents readout, contrast), testing on several other phones, a privacy policy, the Data safety form, a store listing, a Play Console developer account, and a closed test track before production.
   - Exit: the app is approved and live on Google Play.

Play's rules for new developer accounts change often. From memory: registration costs a one-time $25, and new personal accounts must run a closed test with a minimum number of testers for 14 days before production access. Check the current requirements when phase 6 starts.

## Testing

Testing has three layers, ordered from cheapest to most realistic. Most of the confidence comes from the cheap layer, because the DSP is pure functions.

| Layer | What | Pass bar |
| --- | --- | --- |
| Synthetic | Generated sine and harmonic-rich (sawtooth plus decay) tones at known frequencies, plus added noise | Error under 1 cent. Correct string 100% of the time. No octave errors. |
| Recorded | A small set of WAV clips of real plucks on each string, both in and out of tune, checked into `test/fixtures/` | Correct string and cents within ±2 of the ground truth from a reference tuner |
| Manual | A real guitar in a quiet room and in a noisy room. Your Galaxy S23 first. Before the public release, 3+ other Android phones of different brands and Android versions. | Tune-up matches a clip-on tuner within ±3 cents |

Edge cases to cover: a weak low E fundamental, a pluck right after another string rings, a string at the edge of a detection boundary, silence, and talking near the mic.

## Open questions

Platform and audience are decided: a native Android app, personal first and public on Google Play later. Question 1 affects the setup phase, so it comes first. For each question, the right-hand column is the default the plan uses if you don't answer.

| # | Question | Default if unanswered |
| --- | --- | --- |
| 1 | Are you OK with Kotlin + Jetpack Compose, or is there another stack you want to learn? | Kotlin + Jetpack Compose |
| 2 | How strict should "in tune" be? | ±5 cents, held for 0.5 s |
| 3 | Should tapping a string button lock detection to that string? | Yes, as an optional override. Auto-detect stays the default. |
| 4 | Should the needle be the sliding bubble described above, or a separate gauge or strobe? | Bubble slides on a ±50 cent track |
| 5 | Headstock art: will you supply it, or should we draw a vector version? | We draw an original headstock in code |
| 6 | Is there feedback beyond visuals: a sound when in tune, vibration, or a reference tone? | Visual, plus an optional short vibration when in tune |
| 7 | Should the A4 reference be adjustable (432–446 Hz), and should settings persist? | Fixed at 440 Hz for the personal build. Adjustable and saved for the public release. |
| 8 | Mic only, or also USB audio interfaces for electric guitars? | Built-in mic only |
| 9 | Answered: test phone is a Galaxy S23 on Android 16, One UI 8.5. | targetSdk 36, minSdk 26 |
| 10 | For the public release: free, paid, or with ads? | Free, no ads, no network permission |
| 11 | App name and package ID (for example, com.yourname.tuner)? The package ID can't change after the first Play upload. | A placeholder until phase 6 |
| 12 | Is there a target date? | None |
