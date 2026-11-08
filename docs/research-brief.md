# ParaPo: feasibility and research brief

Research date: 4 November 2026. Prepared from the supplied concept, DSA rubric, three lecture PDFs, and public sources. This is research and a proposed evaluation plan; no application, model benchmark, or route field verification was performed.

Follow-up: the [vehicle-first scanner review (8 November 2026)](#vehicle-first-scanner-review-2026-11-08) records the current code/data findings, alternatives, and recommendation. The original brief below remains dated context; use the follow-up for this scanner decision.

Implementation follow-up: [scanner and Tecno evidence, 8 November 2026](evidence/m4/scanner-2026-11-08.md) now records the independent ML Kit crop diagnostic, corrected matcher/scoring, and resolved same-input desktop/Android parity. Aligned snapshots are the default; the checked v10 detector is an explicit experimental option. The review below describes its earlier inspection state. These code/phone checks do not establish live roadside accuracy or complete the three-detector study.

## Assessment

**ParaPo is a defensible Computer Vision and DSA project if its main contribution is testing camera-assisted identification of a particular jeepney against a verified, directional journey.** A small Android prototype is a reasonable objective. Reliable recognition of distant, fast-moving jeepneys under arbitrary conditions remains unproven.

The most useful research question is: **How accurately and quickly can a phone identify a jeepney route from its signage and determine whether it serves a selected journey, while withholding a decision when route or direction is uncertain?**

My recommendation is to retain the manual route finder, scanner, and route detail view, but limit the initial experiment to 3–5 verified route patterns, a small set of boarding locations, daylight, and one selected vehicle at a time. These numbers are scope recommendations, not scientific minimums.

## Existing work and the actual research gap

| Source | What the evidence establishes | Implication for ParaPo |
|---|---|---|
| [Malabay: Iloilo Jeepney Routes](https://play.google.com/store/apps/details?hl=en&id=com.artodyto.lugarlang) | Its developer listing describes origin/destination planning, boarding and alighting guidance, transfers, walking connections, maps, and community route corrections. The listing read on the research date shows an October 7, 2026 update. | Route planning alone is already available locally. Its listing does not describe camera sign recognition; absence from the listing is not proof that no such feature exists. |
| [ByaHero, University of San Agustin, 2024](https://www.ijres.org/papers/Volume-12/Issue-11/121197100.pdf) | A local paper reports a survey of 27 respondents: 63% reported uncertainty selecting the correct bus or jeepney and 74.1% relied on asking locals. | Supports the problem rationale, but this small survey does not establish citywide prevalence or prove that a camera interface helps. The paper mixes prototype language with concept/preference evaluation; treat its evidence accordingly. |
| [Android smart city traveler for Iloilo City, CPU, 2018](https://repository.cpu.edu.ph/handle/20.500.12852/2212) | The repository abstract describes tourist information, jeepney routes, and directions from a user's location. Only the abstract was available in the retrieved record. | Cite as local prior navigation work, not evidence of live sign recognition. |
| [Enhancing Mobility for the Blind: An AI-Powered Bus Route Recognition System, 2025](https://www.mdpi.com/1999-4893/18/10/616) | The authors combine YOLOv8 bus-front detection, image enhancement, OCR, and route-lexicon correction. They report 95% accuracy on 120 test images from Genova. | The detection–OCR–dictionary pipeline already has close precedent. Its reported image-test result does not establish performance on moving Iloilo jeepneys or your Android hardware. |

**Defensible contribution:** a locally collected jeepney-sign dataset, a controlled comparison of lightweight detectors, and a measured end-to-end route-matching system with direction checks and explicit uncertainty. Avoid claiming to be the first jeepney navigation app or to have invented vehicle-sign OCR. This search cannot establish worldwide novelty.

## Relationship to your course materials

The supplied rubric explicitly requires an actual comparison of three related algorithms. It assigns 15 points to algorithm selection, 15 to results and interpretation, and 25 to the working application/demonstration. These three areas account for 55 of the 100 group points. The report limit is at most 15 pages, 12-point font, long bond paper. Group work contributes 70% and individual assessment 30%; groups must have at least five members. The presentation is the week before the scheduled final exam, so the actual calendar deadline is still unknown.

These are requirements described in the attachment, not additional instructions to build or submit anything now.

The Computer Vision slides cover object detection, classical versus learned features, preprocessing, and numerical convolution examples. The Deep Learning slides cover learned representations, activation functions, forward propagation, and optimization. ParaPo fits these through detector training and evaluation. The NLP slides discuss normalization and text representation; route aliases and text matching provide a modest connection, but OCR plus dictionary lookup does not require a separate NLP model or chatbot.

The rubric does not explicitly say the three compared algorithms must all be CV detectors, nor does it explicitly require three graph algorithms as well. Comparing three detectors is consistent with your chosen CV emphasis; instructor acceptance of that interpretation remains unconfirmed.

## Technical feasibility: the important distinctions

### Detecting a sign is different from reading it

Use one detection class, `route_sign`, initially. The detector locates the sign; OCR extracts text; a route matcher resolves that text to stored route patterns. A general vehicle detector identifying a jeepney as a bus does not identify its route.

Recommended flow:

**Live camera → sign detection → crop from original camera image → OCR → route candidates → directional route check → labeled result.**

Keep the original camera resolution for the OCR crop, even if the detector uses a smaller resized frame. Google recommends sufficient character detail, ideally at least 16×16 pixels per character, and emphasizes focus and image quality. This makes distant signage a practical bottleneck. Upscaling an unreadable crop cannot be assumed to recover the correct letters. [Google ML Kit input guidance](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)

Detecting only the sign also supports a simpler interface: outline the sign and show a route card. Outlining the entire jeepney would require an additional vehicle box or reliable sign-to-vehicle association.

### “Real-time” must be measured end to end

A smooth camera preview is not evidence of fast recognition. Record capture-to-result latency, including preprocessing, detection, OCR, matching, and display. Also measure how long the system takes to reach a stable result during a vehicle encounter, and whether it does so before the useful boarding window ends.

Google's live OCR guidance recommends dropping obsolete frames rather than building a processing backlog. Its bundled OCR option also makes the model available immediately, unlike a model that first needs downloading. [ML Kit Android documentation](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)

The first pilot should use the phone the team actually intends to demonstrate. Desktop inference speed cannot support a phone performance claim. Offline recognition and route lookup can be separate from offline maps and place search; test those promises separately.

### Direction cannot be inferred from a route name alone

Model a route as one or more **directional patterns with ordered stop occurrences**. Do not assume that the return journey is simply the outbound list reversed. Loop routes, repeated landmarks, and branches need explicit representation.

For the first prototype, let the user select a verified boarding point and travel direction. Label that direction as user-selected. A clearly directional headsign can add evidence when its meaning has been verified. The phone's GPS position or camera compass alone does not establish which way the vehicle is traveling along its route.

Use three outcomes with both text and color:

| Output | Required evidence | Suggested wording |
|---|---|---|
| Green | Identified route pattern, resolved direction, and a valid direct path from boarding point to destination | “Matches your selected journey” |
| Red | Identified route/direction and a known lack of a direct match within verified coverage | “No direct match for this journey” |
| Gray | Unreadable sign, ambiguous alias, unsupported route, conflicting evidence, or unresolved direction | “Uncertain — confirm route and direction” |

Red must not imply that no possible transfer journey exists. An unsupported route must not automatically be red. When multiple signs appear, ask the user to target one; never combine text across vehicles or reuse a departed vehicle's result.

## Comparing the three proposed models fairly

Keep YOLOv8n, SSD MobileNetV2, and EfficientDet-Lite0 as **candidates**, subject to a small training/export feasibility test. Specify exact variants and checkpoints: “SSD MobileNetV2” is not a complete experimental configuration.

Google documents EfficientDet-Lite and SSD MobileNetV2 as mobile detector options, including their size/accuracy tradeoffs. Ultralytics documents YOLOv8 variants and deployment formats. None of those published results determines which will be best on your signs. [Google detector guide](https://developers.google.com/edge/mediapipe/solutions/vision/object_detector), [YOLOv8 documentation](https://docs.ultralytics.com/models/yolov8/)

Recommended comparison protocol:

1. Fine-tune all three for the same sign-detection task, using the same source images and bounding-box definitions.
2. Freeze one training/validation/test split by recording encounter, and preferably by vehicle and collection session. Do not randomly scatter adjacent video frames across splits.
3. Give each model a documented, comparable tuning budget. Select thresholds on validation data, not the held-out test set.
4. Keep OCR, alias matching, route data, and decision rules fixed while comparing detectors.
5. Report input size, precision/quantization, runtime, acceleration, and postprocessing. Use the same phone and document any unsupported configurations. If native input sizes differ, describe the experiment as a deployed-system comparison rather than a pure architecture comparison.
6. Validate the exported mobile models again. Converting a trained model successfully does not demonstrate identical predictions or good phone speed.

The current Ultralytics documentation deprecates its standalone `tflite` export option in favor of LiteRT while retaining the `.tflite` file format. Pin the tested tool versions rather than following incompatible tutorials. No export was tested for this brief. [Ultralytics export documentation](https://docs.ultralytics.com/integrations/tflite/)

Add one inexpensive diagnostic baseline: OCR on a user-aligned camera crop without a trained detector. If this performs similarly, investigate whether automatic sign localization provides enough benefit. This baseline supplements the required three-model experiment; it does not automatically replace it.

## DSA that contributes to correctness

For direct journeys, an ordered route scan is a sensible baseline: find a valid boarding occurrence followed by an allowed alighting occurrence on the same directional pattern. With N stored stop occurrences, a straightforward full scan is O(N). A stop-to-route index can reduce the candidate set.

If graph algorithms remain in the title, implement and explain an actual graph operation. Represent nodes as `(pattern_id, stop_sequence)` and edges as permitted forward travel. Use BFS for reachability within a route pattern. This prevents overlapping routes from silently becoming transfers at shared landmarks. A plain location-only graph can incorrectly label a two-vehicle path as a direct ride.

BFS minimizes edge count, not automatically distance, travel time, or transfers. Dijkstra is appropriate for nonnegative documented weights when a shortest-distance objective is actually needed. A* is optional; its heuristic must be appropriate to the cost being minimized. There is no need to implement all three just to make the proposal appear more complex. [Princeton BFS reference](https://algs4.cs.princeton.edu/code/javadoc/edu/princeton/cs/algs4/BreadthFirstDirectedPaths.html), [Princeton shortest-path reference](https://algs4.cs.princeton.edu/44sp/index.php)

Useful data structures are an adjacency list for directed connections, ordered lists for stop occurrences, hash maps from aliases to candidate route IDs, a queue for BFS, and a small per-target buffer for recent recognition results. An ambiguous alias must map to multiple candidates, not whichever route was inserted last.

The GTFS specification offers useful examples of separating routes, trips, ordered stops, and geometry. Its `direction_id` is a timetable grouping field, not a substitute for routing through ordered stop data. A small local database borrowing these distinctions is sufficient; a complete GTFS service is outside this prototype's needs. [GTFS reference](https://gtfs.org/documentation/schedule/reference/)

## Data collection and route verification

Iloilo's City Planning and Development office describes a revised route network and implementation challenges in its LPTRP presentation. This is useful historical planning context, not confirmation of every route operating today. [City planning presentation, hosted by UP NCTS/TSSP](https://ncts.upd.edu.ph/tssp/wp-content/uploads/2024/11/2-Iloilo-City-Experiences-in-LPTRP-Development-and-Implementation_EnP-Cartagena.pdf)

The accessible Ilonggo Engineer route guide is dated April 17, 2025, is based on LTFRB MC 2023-036, and explicitly warns that actual operations may differ. Use it to identify candidate routes, then verify selected patterns with the appropriate city transport office, LTFRB Region VI, cooperatives, and observed journeys. [Route guide and caveat](https://ilonggoengineer.com/iloilocity-lptrp/)

For every route pattern, store its identifier, displayed names, aliases, headsign meaning, ordered boarding/alighting locations, geometry, source, verification date, and known variants. Treat the proposed Jaro Plaza–WVSU journey as illustrative until checked; this research did not verify a route for that journey.

For every image or video encounter, record route ground truth from independent observation, sign boxes, exact visible transcription, vehicle/session identifier, lighting, angle, movement, and readability. Include unsupported routes, advertisements, unreadable signs, and non-jeepney scenes as negatives. Do not derive ground truth from the model being tested.

Begin with roughly 30–50 independent encounters as a feasibility pilot. This is a planning suggestion, not enough evidence by itself for a reliable production model. Expand according to missing conditions and validation learning curves. Repeated frames are not independent examples. Reserve separate encounters for final testing before tuning begins; keep augmented versions of each source image in the same split.

Use realistic brightness, scale, perspective, and blur variation. Avoid mirrored-text augmentation for OCR evaluation. For shared datasets and reports, minimize incidental personal information and remove identifying faces or plates where unnecessary; retain route signs. The proposed app need not upload live video.

## Evaluation that answers the commuter's question

| Layer | Measures |
|---|---|
| Sign detection | Precision, recall, AP at IoU 0.5 and AP averaged over IoU 0.5–0.95; results by sign size and condition |
| Text reading | Character error rate and exact route-code accuracy, first on ground-truth crops and then on detector crops |
| Route identification | Exact route-pattern accuracy; confusion matrix including unsupported/unknown outcomes |
| Journey decision | Fraction of green decisions that are wrong; fraction of genuinely matching encounters correctly marked green; abstention rate |
| Live behavior | Median and 95th-percentile capture-to-result latency, time to first stable correct result, processed frames per second, sustained performance |
| User value | Identification time and correct choices with the manual finder alone versus the finder plus scanner |

Define denominators explicitly. For example, wrong-green fraction = incorrect green decisions / all green decisions. If the system makes no green decisions, that measure is undefined, not zero. Also report coverage: a system that always returns gray must not appear successful simply because it never gives a wrong green result.

Evaluate decisions per vehicle encounter as well as per image. Tune temporal agreement on validation encounters and report its added delay. Consecutive frames can repeat the same OCR mistake, so agreement is not proof of correctness. Keep an unsupported-route option even when a fuzzy text matcher finds a nearest known name.

Test route logic separately with hand-verified cases: correct direction, reverse direction, shared landmark with no direct ride, repeated stop on a loop, route branch, unknown route, and destination beyond a terminating point. Test stale overlays when a vehicle leaves the camera view.

For a small usability pilot, counterbalance whether participants use the manual finder or scanner first, and report the sample size. Such a pilot can identify usability problems; it cannot establish broad accessibility or citywide effectiveness. Recorded-video demonstrations are useful but must be labeled separately from live roadside tests.

## Smallest complete development sequence

1. **Prove image readability:** collect a small pilot, test OCR on original-resolution sign crops, and determine the conditions in which useful text is visible.
2. **Verify route logic:** implement a few directional patterns, direct-journey lookup, manual route selection, and route details. This preserves a working demonstration while CV develops.
3. **Prove all three training/export paths:** train a tiny sample and run exported models on the target phone before committing to large annotation work.
4. **Run the controlled comparison:** use the fixed dataset split and common downstream pipeline; retain actual logs and failure examples.
5. **Integrate the selected model:** live scanner, uncertainty labels, single-target handling, and manual fallback.
6. **Evaluate and document:** show measured outcomes, limitations, route provenance, model versions, reproducible steps, and each member's contribution.

Defer transfers, fares, ETA prediction, citywide coverage, background vehicle tracking, accounts, cloud video processing, and full offline maps. No evidence currently justifies adding these to the core experiment.

A suitable revised title is: **“ParaPo: Camera-Assisted Jeepney Route Identification and Direct-Journey Matching for Selected Iloilo City Routes.”** Keep “real-time” in the title only if measured on-device latency and encounter tests justify it. Keep “graph algorithms” if the implementation actually uses and evaluates graph-based route logic.

## Suggested research questions

1. Which of YOLOv8n, the selected SSD MobileNetV2 variant, and EfficientDet-Lite0 gives the best sign-detection accuracy/latency tradeoff on the chosen Android phone?
2. How do sign size, motion, angle, and lighting affect end-to-end route identification?
3. Does route-aware text matching and temporal agreement improve identification without increasing incorrect green decisions or excessive delay?
4. Does the scanner help unfamiliar commuters identify a matching jeepney more accurately or quickly than the manual route finder alone?

## Evidence limits and decision

Confidence is high that related navigation and vehicle-sign recognition work exists, and high in the rubric transcription. Confidence is moderate in the proposed architecture's suitability for a student prototype. Performance on local moving vehicles, the current correctness of selected route patterns, instructor acceptance of the experiment, and development feasibility within the team's remaining time are unresolved.

The study used targeted web searches for local route planning, related OCR research, transit data modeling, graph methods, and official mobile inference documentation. The full ByaHero paper, local planning presentation, app listing, and technical documentation were examined; the CPU item was abstract-only, and the bus-recognition paper was accessible through indexed publisher excerpts after direct-page access failed. No source's reported accuracy was treated as a ParaPo result.

**Proceed with a narrow prototype, and make the camera-readability pilot the first decision point.** If readable signs only become available after the vehicle has passed, the live approaching-jeepney claim must be narrowed to stationary or slow-moving vehicles rather than hidden behind a high image-test accuracy.

## Vehicle-first scanner review (2026-11-08)

**Recommendation: modify the proposal. Make the selected target and sign ownership explicit, keep vehicle type optional, and defer automatic vehicle-first detection until a small comparison shows that it helps.** This is a research finding and proposed next step, not an implemented scanner change or a completed experiment. No training, inference, phone test, dependency installation, or recognition enablement was performed for this review.

### What identifying the correct vehicle requires

Keep these conclusions separate:

| Evidence | What it establishes |
| --- | --- |
| Vehicle type | Jeepney, bus, or uncertain appearance; optional supporting information. |
| Physical target identity | This is still the particular vehicle/sign selected by the user, including across frames. A permanent vehicle identifier is unnecessary. |
| Sign ownership | This sign belongs to that physical vehicle. Neither vehicle type nor a nearby rectangle establishes ownership automatically. |
| Readable text | OCR recovered enough visible sign content to identify a route. |
| Route identity | Observed codes/aliases resolve against the catalog, retaining unknown and conflicting outcomes. |
| Direction/branch | A verified directional headsign or explicit confirmation about the selected vehicle establishes the pattern. Listing both route termini does not establish travel direction. |
| Journey compatibility | The pattern permits boarding at the selected occurrence and alighting later, within supported coverage. |

The destination the commuter wants is never evidence of the observed vehicle's route or direction. Unreadable text stays unreadable; ambiguous ownership stays uncertain; unsupported routes receive a coverage message; unresolved direction requires confirmation. A target or journey change, target loss, pause, or conflicting evidence clears prior compatibility. Keep manual planning available. These distinctions follow [PRD section 9](PRD.md#9-recognition-and-decision-rules) and the [decision logic](../app/src/main/java/com/parapo/prototype/scanner/ScannerDecisions.kt#L223-L263).

### Findings from the current project

| Finding | Evidence and interpretation |
| --- | --- |
| The app currently provides a back-camera preview without live recognition. | Current [MainActivity camera binding](../app/src/main/java/com/parapo/prototype/ui/MainActivity.kt#L837-L870) binds Preview without attaching ScannerPipeline. Older M4 references to a LIVE_SCANNER_ENABLED flag describe an earlier implementation. Selection/callback helpers in source are not live recognition evidence. |
| The detector learns one class, route_sign. | [Dataset preparation](../tools/prepare_detection_demo.py#L73-L98) writes only sign boxes and that class. There is no learned vehicle-to-sign relationship. |
| Source-resolution OCR cropping already exists. | [ScannerPipeline](../app/src/main/java/com/parapo/prototype/scanner/ScannerPipeline.kt#L129-L154) detects on a resized input, maps boxes back, and sends a crop of the unresized upright analysis bitmap to ML Kit. This is existing dormant pipeline behavior, not a proposed new feature or proof of live OCR quality. |
| Selected-sign association and stale-result protection already exist. | [SingleSignTarget](../app/src/main/java/com/parapo/prototype/scanner/ScannerDecisions.kt#L95-L127) rejects weak/competing overlap matches. [TemporalDecisionTracker](../app/src/main/java/com/parapo/prototype/scanner/ScannerDecisions.kt#L276-L361) uses provisional two-observation agreement and one-second freshness, with target/session/context checks. [Unit fixtures](../app/src/test/java/com/parapo/prototype/scanner/ScannerDecisionsTest.kt#L117-L175) check deterministic behavior, not real ownership through crossings. |
| Localization and export differences remain unresolved. | [Recorded v10 validation](evidence/m3/results-2026-11-08.md#L28-L32): desktop found 2/3 signs with 2 false boxes; phone found 2/3 with 1 extra box. Host parity failed and phone outputs differed. No v10 test inference was recorded. Three repeatedly inspected validation photos cannot establish general performance. |
| Recorded phone speed covers the detector only. | [Local phone record](../datasets/m3-samples/runs/demo-v10/phone-record.json): median 110.18 ms, p95 161.32 ms over 60 inferences on TECNO KJ8 CPU. The boundary is an in-memory bitmap through detector boxes, excluding image decoding, OCR, and UI. It is not complete camera response time. |
| Desktop text errors warrant a phone OCR diagnostic. | Reported CER is 0.656 on ground-truth crops and 1.453 on detector crops. However, the helper [writes null predicted route codes](../tools/run_yolo_demo.py#L94-L117), so the reported 0/2 code score does not establish that OCR failed to read both numeric codes. The [latest local OCR rows](../datasets/m3-samples/runs/demo-v10-eval-025/ocr-validation.jsonl) contain text beginning with 10 for image 32. EasyOCR with 3x enlargement on desktop does not measure Android ML Kit. |
| Matching can fail even with perfect OCR. | By source inspection, the printed 03 in 03 UNGKA-ILOILO CITY PROPER VIA CPU is not accepted by the standalone 3 rule, and that whole wording has no matching complete alias. See [matcher](../app/src/main/java/com/parapo/prototype/scanner/ScannerDecisions.kt#L165-L181) and [catalog aliases](../data/iloilo-jaro-wvsu.json#L30-L49). This is an identified implementation gap, not a newly run test or a fix made by this review. |

The catalog supports only Routes 3 and 10 across four Jaro Plaza–WVSU directional patterns. Recognizing a bus-shaped vehicle does not add a supported service. Another service needs verified codes/aliases, directional patterns, allowed boarding/alighting, and compatible ordered stops. See [coverage](../README.md#route-coverage) and [journey lookup](../app/src/main/java/com/parapo/prototype/routing/JourneyPlanner.kt#L28-L43).

### Benefits, failures, and realistic alternatives

A reliable vehicle region could reduce background text and give a small sign more detector pixels. But overlapping vehicle rectangles can both contain one sign; a background sign can project inside a foreground box; a partial vehicle box can exclude its own sign; multiple signs can disagree; and crossings can switch identities. Use visible attachment/context and a uniquely supported owner, or ask the user to select/reframe. A single-frame owner assignment does not establish identity continuity in video.

| Approach | Likely benefit and failures | Data needed | Phone cost / integration / demo fit |
| --- | --- | --- | --- |
| Improve existing sign-first selection | Reuses current work and addresses localization/text/matching; crowded ownership and target switching remain possible. | Correct sign boxes/text; owner labels for evaluating crowded examples. | One detector plus selected-crop OCR; least integration work; useful foundation. |
| User aims at/selects one sign and reads a tight region | Makes intent explicit; aligned OCR can bypass detector misses. Wrong selection or overlapping targets still requires uncertainty. | Text, intended target, and ownership evidence; no new type training. | OCR alone or existing detector plus OCR; modest UI work; best immediate experiment. |
| Vehicle first, then signs within its region | Could suppress distractions and enlarge signs for detection. A missed/partial vehicle blocks reading; containment remains ambiguous. | Vehicle/sign boxes and explicit owner links; type labels only if classification is tested. | Two dependent detector stages, crop mapping, and more export checks; defer pending evidence. |
| Detect vehicles and signs together, then associate | Retains full-scene context and permits explicit owner decisions. Correct boxes can still be paired incorrectly. | Both box types and owner links; video IDs for continuity. | One joint model or two models, plus association and retraining; consider only if crowded failures justify it. |

Prefer simple single-target selection/reselection for this demo. A full tracker is justified only if fresh encounters show that this loses useful readable observations. Tracking, including ByteTrack, does not itself solve sign ownership or prove text readability.

### External evidence and transfer limits

- [ML Kit's official guidance](https://developers.google.com/ml-kit/vision/text-recognition/v2/android) emphasizes focus and sufficient character detail, ideally about 16×16 pixels per character. Another detector cannot recover missing image detail; upscaling is not proof of restored text.
- [Ultralytics' actual COCO class list](https://github.com/ultralytics/ultralytics/blob/main/ultralytics/cfg/datasets/coco.yaml) has car, bus, and truck, but no jeepney, modern jeepney, or route_sign. A hard jeepney-versus-bus gate could reject a useful modern-jeepney sign. A reliable generic vehicle region may be enough for spatial context; local applicability is unmeasured.
- [Nanaura et al., ICAROB 2025](https://alife-robotics.co.jp/members2025/icarob/data/html/data/GS/GS6/GS6-3.pdf), sections 2.4–3.2, report 95.5% bus-region recognition but correct route numbers in only 4/6 videos (about 67%). Fixed panel position, orange-display assumptions, and a camera/computer setup limit transfer to local signs and the Tecno.
- The Philippine [Few-Shot Car Sticker Detection project](https://eneii313.github.io/few-shot-car-sticker-detection/) reports benefits from windshield-first crops on its own sticker dataset. It supports testing contextual crops, not assuming improved jeepney OCR or phone performance.
- [ByteTrack's original paper](https://arxiv.org/html/2110.06864v3) describes motion/detection association across video frames. It does not establish vehicle-to-sign ownership, and its GPU benchmark is not a Tecno timing result.
- [PH Vehicles 2](https://universe.roboflow.com/vehicle-detection-and-data-generation/ph-vehicles-2/dataset/1) publicly lists 249 images, one Jeepney class, CC BY 4.0, and export formats. The listing was verified; the archive, sign coverage, and owner annotations were not. It cannot by itself supply jeepney-versus-bus training or the ownership task. [UP Diliman's original Philippine vehicle-detection thesis record](https://digitalarchives.upd.edu.ph/item/54421/9/home) is limited-access; no downloadable dataset was verified.

The Genova paper cited in the original brief remained inaccessible as full text during this follow-up; its reported image accuracy was not treated as evidence of ParaPo performance.

### Minimum missing data and a comparison that could change the recommendation

The [local v10 manifest](../datasets/m3-samples/manifest-v10.jsonl) contains 38 photos and 40 sign boxes, split 32 train / 3 validation / 3 test, with no vehicle IDs/boxes, known patterns, or known directions. The images contain traditional jeepneys, modern bus/minibus-shaped vehicles, crowded scenes, side strips, windshield placards, and an illuminated display. Bus-versus-modern-jeepney service counts remain unknown. Photo 34 shows a front windshield display, despite the earlier rear description. Full-size photos 22 and 28 show different vehicles; that pair is not evidence of split leakage. The ignored photos and run records linked here are local artifacts, unavailable in a clean checkout.

Reuse existing photos for readability, localization, and single-image owner review. On a small crowded subset, label vehicle boxes, individual sign boxes, and explicit sign-owner IDs, allowing owner unknown. Keep exact text separate from independently established route/direction; type labels are optional unless classification is evaluated. For video, add per-vehicle track IDs, occlusion/crossing/target-loss events, and a useful reading window. Still photos cannot establish identity continuity or whether a result arrives before the vehicle passes.

For a focused association pilot, roughly 20–30 fresh independent encounters is a practical planning estimate, not a sufficient training size or a reliability guarantee. Include isolated/crowded views, supported and unsupported routes, traditional/modern layouts, small text/glare, crossings, and target changes. Keep frames/crops/augmentations from one encounter together, group repeated vehicles and sessions where identifiable, and check source lineage and near-duplicates as well as exact hashes. Per-photo encounter/session names do not prove independence. Treat the repeatedly inspected validation set as development evidence; the three test photos already evaluated in v6 do not become untouched because v10 skipped test inference. Preserve old manifests and reports.

**First practical step, proposed only:** test ML Kit on hand-labeled sign crops on the Tecno before investing in another detector. Poor desktop text recovery makes this worth checking, but it is only a hypothesis about the Android engine.

Then compare the same fresh encounters using (1) existing sign-first selection, (2) user-aligned tight-region OCR, and (3) the same sign detector restricted to hand-labeled vehicle regions. Keep OCR, matching, catalog, decision settings, and phone conditions fixed. Hand-labeled vehicle regions are a favorable diagnostic of context benefit, not a deployable detector benchmark. A benefit would justify testing an actual vehicle detector. This supplements, rather than replaces, any course-required three-detector comparison.

Use the [PRD's encounter definitions](PRD.md#metrics-and-decision-units), which supersede the original brief's older green-decision denominator:

- Correct owner assignment and incorrect owner assignments, with the number of attempted assignments and rejected/uncertain cases.
- Wrong-green fraction: encounters with any incorrect match / encounters receiving any match. A wrong owner is a failure even if its route happens to match the intended vehicle's route.
- Correct-match coverage: truly eligible encounters with a correct match and no incorrect match / all truly eligible encounters. Report unresolved-direction cases separately instead of silently excluding them.
- Uncertainty and definitive-decision coverage across all evaluated encounters. With no green encounters, wrong-green fraction is undefined; always refusing to answer is not success.
- Complete phone response time, time to first stable correct result, and results arriving before the useful window closes; include image processing, OCR, matching, stabilization, and display.

The [current encounter evaluator](../tools/experiment.py#L355-L418) compares pattern IDs, so an ownership field/manual record is needed to expose wrong-owner results when both vehicles share a route. Report raw counts and uncertainty intervals; the pilot cannot establish 100% accuracy or a low population error rate.

Reconsider vehicle-first detection if readable text and corrected matching work adequately, but fresh encounters still show ownership errors under explicit selection, and an actual vehicle-context pipeline reduces them without unacceptable losses in correct-match coverage or phone response time. Until that evidence exists, single-target selection is the smallest useful direction. Live ML Kit readability, service-class counts, encounter independence, and timely ownership through motion remain unverified.
