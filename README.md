# HandMeasure SDK (Android)

## What it is
- Android library `:handmeasure-sdk` providing a drop-in ActivityResultContract to measure ring size on-device.
- Sample app module `:app` shows integration.
- On-device only: no uploads by default; MediaPipe + OpenCV run locally.

## Integrate
1) Include module dependency:
```kts
dependencies {
    implementation(project(":handmeasure-sdk"))
}
```
2) Launch measurement from your screen:
```kotlin
val launcher = rememberLauncherForActivityResult(HandMeasureContract()) { outcome ->
    when (outcome) {
        is HandMeasureOutcome.Success -> /* use outcome.result */
        is HandMeasureOutcome.Cancelled -> /* show message */
        is HandMeasureOutcome.Failure -> /* handle error */
    }
}
launcher.launch(HandMeasureRequest())
```
3) Place MediaPipe model at `handmeasure-sdk/src/main/assets/hand_landmarker.task` (already included).

## Request config (HandMeasureConfig)
- `reference`: `Id1Card` (default) or `Custom(widthMm,heightMm,label)`.
- `requireReference`: enforce card detection for capture.
- `preferredSizeSystem`: VN/US/EU/JP.
- `fitPreference`: SNUG/COMFORT/LOOSE.
- `targetFinger`: RING/INDEX/MIDDLE/LITTLE.
- `preferredHand`: LEFT/RIGHT/AUTO.
- `timeoutMs`: auto-cancel timer.
- `debugEnabled`: show debug panel + debug payload in outcome.

## Outcome
- `Success(result: MeasurementResult)`
  - `recommended`: `RingSizeRecommendation` (size + range + alternatives)
  - `confidence`: score + level (HIGH/MEDIUM/LOW)
  - `warnings`: list of `MeasurementWarning`
  - `debug`: present only if `debugEnabled=true`
- `Cancelled(reason)` with `CancelReason`
- `Failure(error)` with `HandMeasureError`

## Notes
- Requires CAMERA permission at runtime.
- Reference object: ID?1 card (85.60 x 53.98 mm) by default for scale.
- Runs CameraX + MediaPipe Hand Landmarker (live stream) + OpenCV card detection on Y-plane.
- JPEG is encoded only for captured frames; no network upload in SDK.

## Tests
- JVM unit tests are expected for AutoCaptureStateMachine and SizeAggregator (add under `handmeasure-sdk/src/test`).
