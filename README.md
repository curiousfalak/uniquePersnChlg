# FaceCollage

This is an Android app. You give it a video, and it:
1. Finds every face in the video
2. Figures out which faces belong to the same person, even if that person appears several times
3. Picks the best-looking photo of each person
4. Puts all those photos together into one nice-looking collage you can save or share

Everything runs on the phone itself — no internet, no server.

## How to run it

1. Open the project in Android Studio, let it sync.
2. **You need to add one file yourself:** download `mobilefacenet.tflite` and put it in
   `app/src/main/assets/`. This is the AI model that recognizes faces — it's too big to keep in
   the code repo, so it's not included automatically.
3. Check that `minSdk = 26` in `app/build.gradle.kts` (this app is required to support Android 8.0
   and up).
4. Run the app, pick a video from your phone's storage, and watch it work — it shows you a
   progress bar while it's thinking.

## How it works, in plain terms

Think of it as four steps, one after another:

**Step 1 — Look at the video, frame by frame.**
The app doesn't watch the whole video at once. It grabs a snapshot about 6 times per second and
looks at each snapshot separately.

**Step 2 — Find faces in each snapshot.**
For every snapshot, it uses Google's ML Kit to find any faces, where exactly they are, whether
the eyes are open, whether the person is smiling, and which way their head is turned.

**Step 3 — Figure out who's who.**
This is the hard part, and it happens in two stages:
- First, it links a face across *consecutive* snapshots — if the same face is in this frame and
  the next frame, they're probably the same moment of the same person. This gives you a
  "sighting" — one continuous stretch of a person being visible. This is also what the assignment
  calls an "appearance."
- Then, it looks at ALL the sightings across the whole video and groups the ones that are likely
  the same real person, even if they showed up in totally different parts of the video. This uses
  an AI model that turns each face into a list of 192 numbers (called an "embedding") — two faces
  that are the same person produce similar numbers, two different people produce different
  numbers. If two sightings' numbers are similar enough, they get grouped as the same person.

**Step 4 — Build the collage.**
For each person, the app picks their single best photo — preferring a shot where they're facing
the camera, the image is sharp (not blurry), their eyes are open, they're smiling, and they're
alone in the frame (not standing next to someone else). Then it arranges everyone's best photo
into a nice grid with rounded corners and a gradient, like an Instagram story.

## The AI model used

**MobileFaceNet** — a small, fast face-recognition model (about 5 MB) that runs well on phones.
It takes in a small photo of a face (112×112 pixels) and outputs those 192 numbers mentioned
above. The specific file used here comes from
[this open-source project](https://github.com/MCarlomagno/FaceRecognitionAuth) (free to use,
BSD-3-Clause license).

## The similarity threshold (how "similar enough" is decided)

When comparing two sightings to see if they're the same person, the app computes a similarity
score between 0 and 1 (roughly — it can technically go slightly negative for very different
faces). **The cutoff used here is 0.45** — above that, two sightings are considered the same
person; below that, they're kept as different people.

This number came from testing against the actual sample videos and watching what happened at
different cutoff values — not just picked out of thin air. It's a "best available" number, not a
perfect one — see limitations below, and the technical section for exactly how it was derived.

## Known limitations (plain terms)

- **Sometimes the same person still gets shown as two different people in the collage.** We
  tested this a lot. The AI model that tells faces apart isn't perfect — if the same person looks
  noticeably different across two moments (different angle, different lighting), the app can
  mistake them for two different people. We tried several fixes for this, but on some videos it's
  still not perfect. It's a limitation of the face-recognition model itself, not something we can
  fully fix by just tweaking a number — see the technical section below for the evidence.
- **A person turned very far away from the camera might not be detected at all**, or might be
  undercounted, since the face detector needs to see enough of the face to work.
- **Very fast camera movements (whip pans)** can occasionally cause one continuous appearance to
  get split into two, or vice versa.
- **A person who appears very briefly and never has a good, clear shot of their face** might not
  show up in the final collage — the app skips extremely low-quality glimpses on purpose, since
  showing a blurry or unrecognizable photo looked worse than leaving that person out.
- Two people standing very close together in the same frame can occasionally make it harder to
  get a clean, single-person photo for the collage tile — the app tries to crop around this, but
  if two people are extremely close together, the crop has to be tighter than usual.

---

## Technical details (for the curious / for code review)

### Architecture

```
video (SAF Uri)
  -> FrameExtractor        MediaMetadataRetriever, ~6 fps sampling
  -> FaceDetectorWrapper   ML Kit, ACCURATE mode, full landmarks + classification
  -> NMS de-dup            per-frame IoU suppression of duplicate ML Kit detections
                           for the same physical face (VideoProcessor.suppressDuplicateDetections)
  -> FaceEmbedder          TFLite MobileFaceNet, 192-d L2-normalized embeddings
  -> Tracker               Stage A: per-frame IoU + embedding matching -> Tracklets
                           (one Tracklet == one continuous "appearance")
  -> quality gate          tracklets whose best frame scores < 0.30 are dropped entirely
                           (prevents blank/background-only collage tiles)
  -> IdentityClusterer     Stage B: agglomerative clustering of tracklets -> Identities
  -> representativeSample  tiered hard filter (isolated + eyes-open + uncropped, relaxing
                           only when a strictly better tier has zero candidates)
  -> CollageComposer       Canvas-based Instagram-story-style grid, with neighbor-aware
                           crop clamping so a tile can't bleed into an adjacent face
```

Package layout: `pipeline/` (the stages above), `data/` (models: `FaceSample`, `Tracklet`,
`Identity`), `ui/` (Compose screens), `viewmodel/` (bridges pipeline to UI via `StateFlow`).

**Why tracking and clustering are two separate stages, not one:** Tracking only ever compares
*consecutive* sampled frames (cheap, local, and should essentially never confuse two different
people since the face barely moves frame-to-frame). Clustering then compares tracklets *globally*
across the whole video, which is where "is this the same person as 10 seconds ago" gets decided.
Splitting these lets the appearance-count logic (tracklet start/end) stay simple and correct
independent of whether long-range identity matching gets every case right.

**Concurrency:** `VideoProcessor.process()` runs entirely under `Dispatchers.Default` inside
`viewModelScope.launch`; progress publishes via `StateFlow<ProcessingState>`, collected in
Compose with `collectAsState()`. No bitmap decoding, ML Kit inference, or TFLite inference ever
touches the main thread.

### Why the clustering algorithm went through three versions

This is worth documenting because each version fixed one real, observed failure and introduced
another — useful context for anyone extending this.

**v1 — centroid averaging.** Average all of a tracklet's frame embeddings into one vector,
compare the two vectors' cosine similarity. Failed on head-pose variance: if one appearance of a
person is mostly frontal and another is mostly turned, averaging dilutes the strong
frontal-to-frontal evidence a direct frame comparison would show, so the two vectors end up
too dissimilar to merge. Confirmed on real test footage — same person, split into two identities.

**v2 — top-2-of-best-frames.** Instead of blending into one vector, compare each tracklet's top-6
individual frames directly, and score a pair by the average of just its 2 highest cross-frame
similarities. This fixed the pose problem, but agglomerative clustering **chains** on a
best-pair-like criterion: a merged cluster has more frames, hence more chances for 2
coincidentally-high similarities against an unrelated cluster, which then merges too, cascading
until everyone collapses into a single identity. Also confirmed on real footage (an entire
collage collapsed to one person).

**v3 — full average-linkage over frame pairs (current).** Still compares individual frames
directly (keeps v2's pose-robustness — no vector-averaging), but scores a pair by the **mean of
every cross-frame similarity** in the top-6×top-6 comparison grid, not just the top 2. Requiring
the whole comparison set to agree resists the chaining failure far better than cherry-picking a
couple of high values. Threshold: **0.45**, chosen by simulating the full clustering result
against logged similarity data at a range of threshold values (0.30 through 0.60) and checking
which value avoided both under-merging and wrong-merging on the available evidence.
`VideoProcessor.logPairwiseSimilarities()` logs this exact score for every tracklet pair on every
run, which is how the threshold was tuned — from real numbers, not guesses.

### The confirmed clustering limitation, with evidence

On at least two different test videos, a same-person pair was visually confirmed (not just
inferred from the numbers) to have similarity scores as low as 0.21–0.42 — overlapping with the
typical range for different-person pairs on the same footage. Simulating the clustering result at
every threshold from 0.30 to 0.60 on that data showed no single cutoff separates them cleanly:
lower thresholds fixed the intended pair but wrongly merged unrelated people first (since
agglomerative clustering always takes the single best available merge each round); higher
thresholds left the duplicate but avoided wrong merges. 0.45 is the value that avoids ever
creating an actively wrong merge on the evidence gathered, at the cost of occasionally leaving a
genuine duplicate. This is treated as a known model limitation (MobileFaceNet's embedding
consistency across pose/lighting shifts, on this specific footage), not a clustering-logic bug —
time-boxed and documented rather than pursued further.

### Representative shot selection

Base score in `FaceSample.qualityScore()`: frontality 30%, sharpness 25%, eyes-open 20%, smiling
10%, face size 15%, with a crowding penalty when another face is nearby. On top of that,
`Identity.representativeSample()` applies a **tiered hard filter** (not just a soft weight) for
the assignment's explicit "avoid closed eyes / avoid clipped faces" language:
1. isolated + eyes open + not touching frame edge
2. isolated + eyes open
3. isolated only
4. any frame (last resort — this person has no solo frame anywhere in the video)

Collage crops use `BitmapUtils.cropWithMargin` at ~55% padding around the detected box (never the
tight bounding box itself), further clamped so the crop can never cross the midpoint to a nearby
face — an earlier version had a margin floor that ignored this and mathematically guaranteed
bleed for any two people closer than ~1.3× a face-box width apart (a normal distance for two
people framed together); that floor was removed. If a clamped crop still comes out too small,
the tile falls back to the full sampled frame, which the assignment explicitly allows.


