# HandQualityGateAutoCapture

This Android app performs live quality gating for hand capture, then estimates ring size from the best frames.

## Pipeline Overview
1. **Camera input** (CameraX, YUV frames).
2. **Hand ROI tracking** via MediaPipe Hand Landmarker in **LIVE_STREAM** mode.
   - Landmarks drive a bounding ROI and confidence for the quality gate.
3. **Quality gate** evaluates blur, motion, exposure, and ROI stability.
4. **Card detection** (OpenCV) runs on the **Y-plane** for speed and lower memory use.
5. **State machine** triggers capture when quality is stable.
6. **Capture** encodes JPEG only when persisting or uploading.
7. **Size estimation** (offline on captured frames):
   - Card detection -> scale (mm/px)
   - Hand landmarks -> ring finger axis
   - Finger width measurement -> ring size aggregation

## Model Asset Setup
This project requires the MediaPipe Hand Landmarker model file:

- **Path:** `app/src/main/assets/hand_landmarker.task`
- If missing, the hand landmarker will not initialize.
- A placeholder instruction file also exists at `app/src/main/assets/README.txt`.

After placing the model, sync Gradle and rebuild the app.
