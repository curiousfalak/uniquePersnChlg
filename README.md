# Video-based unique-person collage

An Android app. Give it a video, and it:
1. Finds every face in the video
2. Groups faces that belong to the same person, across multiple appearances
3. Picks the best photo of each person
4. Combines them into a shareable collage

Everything runs on-device — no internet, no server.

## How to run it

1. Open in Android Studio, let it sync.
2. Add `mobilefacenet.tflite` to `app/src/main/assets/` (not included in the repo — download
   separately, see model section below).
3. Confirm `minSdk = 26` in `app/build.gradle.kts`.
4. Run the app, pick a video, watch the progress bar.

## How it works

**1. Sample the video.** Grabs a frame about 6 times per second.

**2. Detect faces.** ML Kit finds each face per frame: position, eyes open/closed, smiling,
head angle.

**3. Group faces into people.**
- Faces are linked across *consecutive* frames into one continuous "appearance" (a sighting).
- Appearances are then compared across the whole video using a 192-number face embedding — two
  sightings with similar numbers get grouped as the same person.

**4. Build the collage.** For each person, picks the best available photo (facing camera, sharp,
eyes open, smiling, alone in frame), then arranges everyone into a grid collage.

## The AI model used

**MobileFaceNet** — ~5 MB, TFLite, runs on-device. Input: 112×112 face crop. Output: 192-number
embedding. Sourced from [MCarlomagno/FaceRecognitionAuth](https://github.com/MCarlomagno/FaceRecognitionAuth)
(BSD-3-Clause).

## Similarity threshold

**0.45** — two appearances are grouped as the same person if their embeddings score above this;
kept separate below it. Tuned by testing against sample videos at different cutoff values (see
technical section for exact method).

## Known limitations

- The same person can occasionally still appear as two different people in the collage. Caused
  by the face-recognition model's embeddings shifting across different angles/lighting — not
  fully fixable by adjusting the threshold. See technical section for evidence.
- A person facing away from the camera may be missed or undercounted.
- Fast camera movements can occasionally split one appearance into two, or merge two into one.
- A person who never gets a clear shot of their face may be excluded from the collage rather
  than shown with a low-quality photo.
- Two people very close together in frame can force a tighter-than-ideal crop to avoid bleeding
  into the other person's face.

---

## Technical details

### Architecture

```
video -> extract frames -> detect faces -> remove duplicate detections
       -> get face embeddings -> track faces across nearby frames (= appearances)
       -> drop low-quality appearances -> cluster appearances into people
       -> pick best photo per person -> render collage
```

Code layout: `pipeline/` (steps above), `data/` (`FaceSample`, `Tracklet`, `Identity`),
`ui/` (screens), `viewmodel/` (connects pipeline to UI).

Tracking and clustering are kept separate: tracking only compares nearby frames (cheap, reliable
short-range matching), clustering compares across the whole video (where "is this the same
person as earlier" gets decided). This keeps appearance counting accurate even if long-range
matching isn't perfect.

Runs on a background thread (`Dispatchers.Default`), reports progress to the UI via
`StateFlow`. Nothing touches the main thread.

### Clustering algorithm

Compares appearances by their individual best frames, not one averaged vector per appearance
(averaging washes out strong matches when the same person is captured at different angles).

For each pair of appearances: take each one's top-6 highest-quality frames, compare every frame
from one side against every frame from the other, and score the pair by the **average of all
those comparisons**. Two appearances merge if that average is above the threshold.

Threshold: **0.45**, chosen by testing against logged similarity scores across sample videos and
picking the value that avoided incorrect merges. `VideoProcessor.logPairwiseSimilarities()` logs
this exact score per appearance pair on every run.

### Known clustering limitation, with evidence

On two test videos, a confirmed same-person pair scored 0.21–0.42 — overlapping the typical
different-person range on the same footage. Simulating clustering across thresholds 0.30–0.60
showed no single cutoff separates them cleanly: lower thresholds fixed the intended pair but
wrongly merged unrelated people first (agglomerative clustering always takes the single best
available merge each round); higher thresholds avoided wrong merges but kept the duplicate. 0.45
is the value that never produced a wrong merge on the evidence gathered.

### Representative shot selection

`FaceSample.qualityScore()`: frontality 30%, sharpness 25%, eyes-open 20%, smiling 10%, face size
15%, with a crowding penalty when another face is nearby.

`Identity.representativeSample()` applies a tiered hard filter on top:
1. isolated + eyes open + not touching frame edge
2. isolated + eyes open
3. isolated only
4. any frame (only if this person has no solo frame anywhere in the video)

Collage crops use `BitmapUtils.cropWithMargin` at ~55% padding around the detected box, clamped
so the crop can't cross the midpoint to a nearby face. Falls back to the full sampled frame if a
clamped crop comes out too small.
