# ParaPo v1 — release checklist

## Completion and current state

**100% complete** means the agreed ParaPo v1 research prototype builds from documented source, installs on the designated Android phone, completes its required commuter journeys, and meets its approved acceptance criteria within the documented routes and operating conditions. It also means the research and demonstration deliverables are ready for review.

All release checkboxes below must close against evidence for the release identified in the release record. Documentation review, implementation, automated tests, replay, live-phone verification, human approval, and delivery are distinct events. A working manual planner alone, a successful recording, or a selected model alone does not complete the prototype. Public distribution is a separate decision.

Baseline: [PRD v1.0 draft](PRD.md), especially §1 authority, §§8–15 requirements/protocol, §§16–17 deliverables/gates, and §§19–20 decisions/change control. [README](../README.md), [research brief](research-brief.md), and [original concept](original-concept.md) provide context; broader concept features do not enter scope automatically. Section references below refer to the PRD unless otherwise stated. Use the PRD's encounter-level metrics rather than the research brief's older green-decision denominator.

Inspected 2026-11-06: repository `C:\Users\roman\Desktop\Vscode Projects\ParaPo`; branch `main`; HEAD `cabc046e90565441781d58a8c09a5731836fa83f`; upstream `origin/main`; remote `https://github.com/SpekyLord/ParaPo.git`. The initial working tree was clean. The four linked Markdown files and `.gitignore` were the entire project inventory, including nested/untracked files. No applicable AGENTS.md, CLAUDE.md, or prompt-writing rules were found. The three existing commits contain planning documents only. No app source, tests, build/test entry point, verified route bundle, trained model, or existing tracker was present. Course PDFs and private research artifacts were unavailable. These facts do not establish app acceptance.

**Next work:** the project lead assigns people and confirms the instructor/deadline in M1-01. The designated Tecno is now recorded under M1-02. The route/data lead must supply authorized records and the reverification decision for M2-01/02/15. Begin the camera pilot only after permission and safety prerequisites. Route verification can proceed alongside feasibility. M4 integration requires M2 and M3 outputs. Do not perform future app tasks merely to maintain this tracker.

### How to close a task

Each stable ID has one reviewable outcome, a responsible role, a pass condition, a source, and required evidence. Phase setup supplies shared prerequisites; task-specific dependencies override the phase default. Keep IDs when adding or revising tasks; never reuse a retired ID. Role labels are responsibilities, not assigned people. A person may hold several roles.

Every checkbox starts open because there is no app acceptance evidence. The initial evidence state of every task is **COULD-NOT-RUN — planning only; required implementation, decision, artifact, or human activity is pending**. Its next action is the stated task after its prerequisites. This status is not a claim that an app test was attempted.

At closure, append: `PASSED | method | exact build/data/model/device or decision version | procedure/fixture IDs | expected and actual result | evidence reference | reviewer/date`. Methods are **automated test**, **recorded replay**, **live phone test**, **field observation**, or **human sign-off**; combine explicitly when needed. Evidence can reference authorized private records with custodian/access instructions instead of exposing recordings or personal data. Research collection is separate from ordinary camera use.

For failure or inability to run, leave the box open and append `FAILED` or `COULD-NOT-RUN`, reason, retained measurements/logs, owner, and next action. Implementation without acceptance evidence stays open. Do not overwrite earlier failures with a later pass. Evidence for another release needs an explicit applicability review or rerun. Human approvals must name the decision, approver, date, and exact scope; this tracker invents none.

### Release record — fill from evidence, not assumptions

| Item | Agreed and verified value |
| --- | --- |
| Release name, source revision, build environment and build instructions | No approved release designation; M2 app 0.1.0 build environment and commands are recorded in M2 evidence; formal release record M6-01 |
| Installable Android artifact, checksum, application ID/version | Unsigned local debug/release artifacts and hashes recorded in M2 evidence; accepted installable release remains M6-01/M6-02 |
| Route-data/schema version, exact 3–5 pattern IDs, sources and verification dates | Unassigned; M2-01, M2-02 |
| Selected detector/checkpoint/hash, OCR, runtime/dependency versions and configuration | Unassigned; M3-08, M3-10 |
| Training/validation/test manifest versions, evaluation configuration and evidence index | Unassigned; M3-04, M5-01, M5-12 |
| Designated phone model, Android/OS build, minimum supported Android version | Tecno Spark 20 Pro 5G (ADB model TECNO KJ8), Android 14/API 34, build UP1A.231005.007, security patch 2024-12-05; minimum API 34; M1-02 evidence below |
| Verified conditions: daylight/lighting, sign size, distance, angle, vehicle motion/type, sign type, stationary boarding area | Unassigned; proposed M1-08, verified M5-12 |
| Direction source, freshness/window/threshold configuration and approved targets | Unassigned; M4-01, M5-01 |
| Known limitations, excluded conditions, release evidence and approved changes | Unassigned; M5-12, M6-09 |
| Owner approval of this exact release, scope, evidence and delivery/access location | Not approved or delivered; M6-10 |
| Public distribution | Not authorized by this checklist; separate decision required |

## M1 — Feasibility

Setup: project lead coordinates Android, CV, route/data, and evaluation leads. Assign people before committing a schedule; obtain the supplied course materials through authorized access. Route investigation may run in parallel with the camera pilot. Deliverables: decision records, target device, pilot evidence, three small-sample training/export/phone runs, and an approved feasibility conclusion. Exit requires M1-01–M1-08 evidence; a blocker is not a pass.

- [ ] M1-01 — Establish an accepted project commitment. Owner: project lead. Pass when instructor acceptance of the three-detector interpretation is recorded against the actual rubric, submission/presentation deadline and team assignments are confirmed, staffing/training resources are known, and milestone dates are scheduled backward from that deadline. Evidence: human sign-off and authorized course reference. Source: §§16–17, §19; research brief course alignment.
- [x] M1-02 — Designate the demonstration phone. Owner: Android/CV leads. Pass when a physically available phone's exact model, Android/OS build and minimum supported Android version are recorded, with camera/storage/runtime constraints and access for repeat tests. Evidence: device inventory and live phone inspection. Source: §§5, 12–13, 19. **PASSED | live phone inspection | Tecno Spark 20 Pro 5G per prior device record; ADB model TECNO KJ8/KJ8-OP; Android 14/API 34; build UP1A.231005.007, incremental 250108V1200, fingerprint TECNO/KJ8-OP/TECNO-KJ8:14/UP1A.231005.007/250108V1200:user/release-keys, security patch 2024-12-05; minimum API 34 | ADB hardware inventory, camera feature list, `/data` 227 GiB total/52 GiB free, RAM 7,802,768 KiB/2,600,308 KiB available, heap growth 256 MiB/max 512 MiB, USB repeat access | expected device/OS/constraints recorded; actual recorded and connected for repeat tests | [M2 implementation evidence](evidence/m2/implementation-2026-11-06.md) | recorded by Codex, 2026-11-06.**
- [ ] M1-03 — Authorize a safe readability pilot. Owner: data/evaluation leads. Prerequisites: M1-02; M3-01 permissions applicable to the pilot. Pass when the protocol specifies stationary users at verified boarding areas, useful decision-window annotation, original-resolution captures, independent route/direction/readability observations and condition coverage; use approximately 30–50 independent encounters as a pilot suggestion, not a final sample-size guarantee. Evidence: signed protocol and collection plan. Source: §§4, 14, 18.
- [ ] M1-04 — Measure actual-phone sign readability before passing. Owner: evaluation/CV leads. Prerequisite: M1-03. Pass when the pilot reports readable sign size/distance/angle/lighting, motion, first useful text time and vehicle-passing/window-end time, including unreadable and too-late encounters; conclude whether a stationary commuter can receive a useful result in time. Evidence: field observations, original-crop OCR checks, encounter counts and authorized timestamps/recordings. Source: §§5, 13–14, 18.
- [ ] M1-05 — Prove YOLOv8n's mobile path. Owner: CV lead. Prerequisites: M1-02, authorized pilot samples. Pass when a small `route_sign` sample can be trained, exported and inferred on the designated phone, with predictions checked against the training implementation. Record checkpoint/license, tool/runtime versions, preprocessing, output decoding, precision and acceleration. Evidence: training/export logs and live phone outputs. Source: §§12, 14, 17.
- [ ] M1-06 — Prove the exact SSD MobileNetV2 variant's mobile path. Owner: CV lead. Prerequisites: M1-02, authorized pilot samples. Pass when a named SSD variant/checkpoint trains on a small `route_sign` sample, exports and runs on the designated phone with predictions checked against the training implementation; record license, tool/runtime versions, preprocessing, decoding, precision and acceleration. Evidence: variant/configuration record, training/export logs and live phone outputs. Source: §§14, 19.
- [ ] M1-07 — Prove EfficientDet-Lite0's mobile pipeline configuration. Owner: CV lead. Prerequisites: M1-02, authorized pilot samples. Pass when EfficientDet-Lite0 trains on a small `route_sign` sample, exports and runs on the designated phone with predictions checked against the training implementation, and the chosen bundled OCR reads original-resolution crops offline; pin compatible export/runtime/OCR versions and record checkpoint/license, preprocessing, decoding, precision and acceleration. Evidence: training/export logs and live phone detector/OCR outputs. Source: §§12, 14, 19.
- [ ] M1-08 — Approve the feasibility conclusion. Owner: project lead with Android/CV/evaluation leads. Prerequisites: M1-01–M1-07. Pass when the evidence supports proceeding in a proposed phone/lighting/sign-size/distance/movement/vehicle envelope, or an explicitly approved narrower claim with supporting pilot evidence. Unresolved feasibility blocks exit; record investigation owner and next action. Scope/model/offline/direction/target changes need the authorized §20 PRD change process; do not edit the PRD under this writing task. Evidence: human sign-off, retained failures and envelope rationale. Source: §§5, 17–20.

## M2 — Route foundation and manual planner

Setup: Android lead owns Kotlin app/build/test work; route/data lead owns independently verified operational data and SQLite contents. M2-01 route work can overlap M1; device-dependent exit checks require M1-02. Deliverables: build/test instructions, versioned 3–5-pattern bundle, independent routing baseline, manual screens and deterministic test results. Exit requires M2-01–M2-17 evidence on the recorded dataset/device.

- [x] M2-01 — Verify the exact coverage. Owner: route/data lead. Pass when 3–5 directional Iloilo City patterns have named IDs, route names/aliases, direction/branch/headsign meanings, finite ordered stop occurrences, actual boarding/alighting permissions, geometry, sources/contacts and verification dates. Verify opposite directions independently; landmark proximity and illustrative Jaro Plaza–WVSU examples are not proof. Evidence: authorized source records and field verification. Source: §§5, 10–11, 19.
- [x] M2-02 — Produce a valid versioned SQLite bundle. Owner: route/data lead. Prerequisite: M2-01. Pass when dataset/schema version, publication date and sources are present; IDs, references, ordered sequences, coordinates, geometry correspondence and verification completeness validate across all entities in §11. Unknown direction stays unknown. Evidence: bundle version/hash and automated validator output. Source: FR-18, §11.
- [x] M2-03 — Document a reproducible Android and test entry point. Owner: Android lead. Prerequisite: M1-02. Pass when pinned Kotlin/Android SDK/toolchain/dependency setup builds the manual app and runs deterministic tests from documented source; Python remains experiment-only, with no phone dependency. Record actual commands once they exist. Evidence: clean-environment build/test logs and instructions. Source: §§12, 16–17. **PASSED | automated build and tests | app 0.1.0, source baseline 082a9e2, JDK 17.0.18, Gradle 8.11.1, AGP 8.10.0, Kotlin 2.2.10, SDK 36/min API 34 | `clean assembleDebug testDebugUnitTest connectedDebugAndroidTest`, `assembleRelease`, and phone `connectedDebugAndroidTest`; synthetic test-only records | expected documented toolchain builds and tests pass; actual debug/release build and 11 JVM tests passed, 8 Pixel AVD tests passed, and all 9 final instrumentation tests passed on TECNO KJ8 Android 14/API 34 | [README](../README.md), [M2 implementation evidence](evidence/m2/implementation-2026-11-06.md) and current Gradle reports | recorded by Codex, 2026-11-06. Earlier wrapper attempt could not locate the Android SDK until `ANDROID_HOME`/`ANDROID_SDK_ROOT` were set; the API 37 Espresso incompatibility and its fix are retained in the evidence note.**
- [x] M2-04 — Resolve curated locations without losing ambiguity. Owner: Android lead. Prerequisites: M2-02, M2-03. Pass when canonical names/aliases resolve to stable location IDs, ambiguous names offer choices, missing input is explained, unknown input shows unsupported coverage, and identical origin/destination explains no ride needed. Evidence: automated fixtures and live phone input flow. Source: FR-01–FR-02, AT-05, AT-18, §7.4.
- [x] M2-05 — Match same-pattern BFS to an independent ordered scan. Owner: Android lead. Pass when directed adjacency nodes `(pattern_id, sequence)` and an independently implemented ordered-list baseline return the same complete eligible set, with allowed boarding before allowed alighting and deterministic result order; include prohibited boarding/alighting fixtures. Evidence: fixtures, automated comparison output and algorithm review. Source: FR-03, §10, AT-01.
- [x] M2-06 — Reject travel against occurrence order. Owner: Android lead. Pass when destination-before-boarding returns no direct route unless a separately verified later occurrence permits the journey; an opposite-direction pattern is separately verified rather than an inferred reversal. Evidence: forward/reverse/later-occurrence fixture IDs and automated baseline agreement. Source: FR-03, AT-02, §10.
- [x] M2-07 — Prove a shared landmark cannot create a transfer. Owner: Android lead. Prerequisites: M2-03, M2-05 fixtures. Use two directional patterns sharing one landmark; search from a boarding point unique to the first to a destination unique to the second. Pass when the normal finder returns no direct route and the independent ordered-stop baseline agrees. Evidence: fixture IDs and passing automated output. Source: FR-03, AT-03.
- [x] M2-08 — Preserve loop, branch and terminal boundaries. Owner: Android lead. Pass when repeated stops have distinct occurrences, branches never merge into a fabricated ride, and a finite loop cannot wrap or continue through a terminal without verified continuous service. Evidence: repeated-stop/branch/terminal fixtures and automated baseline agreement. Source: FR-03, AT-04, §10.
- [x] M2-09 — Explain supported journeys on Home and Results. Owner: Android lead. Pass when coverage notice, origin/destination search, Find routes and Explore actions lead to all direct results with route/name, boarding, direction, drop-off, verification information, details and Scan actions; supported no-match explains direct-only coverage without claiming no service exists. Evidence: live phone walkthrough and known-result fixtures. Source: FR-04–FR-05, AT-01, §§6–7.
- [x] M2-10 — Provide complete directional route details. Owner: Android lead. Pass when selected pattern, ordered landmarks, direction, boarding/alighting markers, source and verification date are readable, with no unsupported fastest/cheapest/shortest claim. Evidence: live phone detail views cross-checked with M2-01. Source: FR-04, FR-07, §§6, 10.
- [x] M2-11 — Choose and verify map rendering with a useful fallback. Owner: Android lead. Pass when provider/network behavior and attribution obligations are recorded and met; map shows the selected directional geometry/points; blocked basemap access still leaves local geometry or ordered text and direction understandable. Evidence: provider/license decision, online/offline live phone views. Source: FR-07, AT-13, §§6, 19.
- [x] M2-12 — Browse routes without planning a journey. Owner: Android lead. Pass when Explore searches supported routes and opens directional details without origin/destination or camera access. Evidence: live phone walkthrough. Source: FR-06, §§6–7.3.
- [x] M2-13 — Expose provenance and limitations in About / Data. Owner: Android lead. Pass when app/model/data versions, verification information, prototype limitations and permission explanation are available in the interface. Evidence: live phone views checked against the installed artifacts. Source: FR-04, FR-19, §6.
- [x] M2-14 — Retain working data after invalid updates. Owner: Android/route-data leads. Pass when duplicate IDs, broken references, bad coordinates/sequences, incomplete patterns and malformed SQLite/update input are rejected or excluded before replacement, the last valid bundle survives, and unavailable capability is explained. Evidence: automated corruption/update/recovery fixtures and live phone recovery test. Source: FR-18, AT-14, §11.
- [x] M2-15 — Enforce route reverification and invalidation. Owner: route/data lead. Pass when interval, responsible contacts and invalidation process are approved, dates are displayed, and inactive or explicitly unverified patterns cannot yield affirmative compatibility. Evidence: policy sign-off and automated inactive/unverified fixtures. Source: FR-18, §§11, 19.
- [x] M2-16 — Demonstrate manual operation without recognition. Owner: Android lead. Pass when location lookup, direct results, details and Explore operate with camera denied and connectivity disabled, using bundled data and text fallback. Evidence: live phone walkthrough retaining chosen journey. Source: FR-05, FR-17, AT-12–AT-13.
- [x] M2-17 — Explain and measure the implemented DSA. Owner: Android lead. Pass when actual adjacency-list space and BFS traversal O(V+E), ordered scan O(N), and average-case hash-map alias lookup are explained with implementation measurements, separately from inference time; no gratuitous Dijkstra/A* is substituted for the required BFS. Evidence: source-linked explanation and reproducible measurements. Source: §10, §16.

Unless specified otherwise, M2-05–M2-17 require M2-02/M2-03; UI checks also require the relevant routing/data tasks. M2-05–M2-08 fixtures need not be real operational route identities, but must be clearly labeled synthetic.

## M3 — Dataset and controlled model comparison

Setup: data/evaluation leads own authorized collection, independent labels and split control; CV lead owns experiments. Permission planning M3-01 precedes the M1 pilot. Full collection requires M1 feasibility, M3-01 and M3-02; final detector comparison requires frozen M3-04 and M5-01 targets. Deliverables: authorized manifests, experiment configurations, three measured candidates, OCR-only baseline, phone results and selection rationale. Exit requires M3-01–M3-10; substitutions require explicit updated approval, not silent omission.

- [ ] M3-01 — Authorize collection, retention and affected artifact access. Owner: data/evaluation leads with project lead. Pass when pilot/full-collection permissions, provenance, retention/deletion rules, authorized custodians and code/model/data licenses/distribution rights are recorded before the affected collection or sharing; minimize incidental faces/plates, keep raw recordings/weights out of ordinary Git, and record course AI-use disclosure obligations truthfully. Evidence: human approvals and restricted evidence/access register. Source: §§14, 16, 19–20.
- [ ] M3-02 — Approve full collection size and diversity. Owner: data/evaluation leads. Prerequisites: M1-04, M1-08. Pass when collection size and expansion criteria follow route/sign diversity and validation learning curves; planned conditions include vehicles/sessions, angles, distances, glare, occlusion, motion, unsupported signs, distracting text and negative scenes, with excluded conditions recorded. Evidence: approved collection protocol and coverage matrix. Source: §§14, 19.
- [ ] M3-03 — Establish independent sample annotations. Owner: data lead. Pass when each sample has encounter/session and available vehicle IDs, sign boxes, visible transcription separate from independently verified route/direction labels, condition and permission provenance; annotation review identifies and resolves errors without using model predictions as truth. Evidence: versioned annotation manifest and reviewed examples through authorized access. Source: §11 research sample, §14.
- [ ] M3-04 — Freeze a leakage-audited held-out manifest. Owner: evaluation lead. Prerequisite: M3-03. Pass when splits group by encounter and, where possible, vehicle/session; source variants do not cross splits; augmentation is training-only; independent encounter and frame counts are reported; test manifest is frozen before final model selection. Evidence: manifest versions/hashes and automated leakage audit. Source: §14.
- [ ] M3-05 — Fix a fair comparison configuration. Owner: CV/evaluation leads. Prerequisites: M1-05–M1-07, M3-04. Pass when YOLOv8n, the named SSD MobileNetV2 variant and EfficientDet-Lite0 share `route_sign` labels, split, downstream OCR, aliases/data and compatibility rules, with comparable tuning budgets, seeds and checkpoints recorded; thresholds use validation only. Evidence: versioned configurations and experiment review. Source: §§11, 14.
- [ ] M3-06 — Complete actual experiments for all three detectors. Owner: CV lead. Prerequisites: M3-05, M5-01 before any held-out run. Pass when all three have reproducible training/evaluation records containing dataset/split, setup, seed, input size, precision, runtime/acceleration/postprocessing and actual metrics, including failed runs and condition/sign-size results. Evidence: configurations, logs, checkpoints through authorized access and results tables. Source: FR-19, §§14, 16–17.
- [ ] M3-07 — Measure the OCR-only aligned-crop baseline. Owner: CV lead. Same held-out prerequisites as M3-06. Pass when aligned-crop OCR uses the same frozen encounters/downstream matching and is compared for correctness and timing, distinguishing ground-truth and detector crops; it supplements all three detectors. Evidence: baseline configuration and measurement output. Source: §14.
- [ ] M3-08 — Validate exported candidates on the designated phone. Owner: CV/Android leads. Prerequisite: M3-06. Pass when each mobile candidate's converted outputs are checked for correctness and preprocessing/decoding parity, and measured phone latency/FPS/model size/peak memory/acceleration are recorded with export/runtime/device versions; unsupported paths remain failures until resolved or explicitly approved substitutions are evaluated. Evidence: automated parity output and live phone logs. Source: §§12, 14.
- [ ] M3-09 — Account for recognition failures. Owner: evaluation/CV leads. Prerequisites: M3-06–M3-08. Pass when localization, OCR, route/pattern confusion, direction, unsupported text and late decisions are analyzed by condition with retained failures and small-sample limits; outside benchmark numbers are clearly background only. Evidence: failure analysis and authorized example references. Source: §§14, 16, 18, 20.
- [ ] M3-10 — Justify and identify the selected model. Owner: CV lead with project lead. Prerequisites: M3-06–M3-09. Pass when actual comparison, OCR-only findings, exported-phone behavior, licenses and practical tradeoffs support the selected checkpoint/hash and pinned OCR/runtime versions. Record any substituted candidate's rationale and approved experiment change before final testing. Evidence: selection sign-off and release-record entries. Source: §§14, 17, 19–20.

## M4 — Integrated live scanner

Setup: Android/CV leads integrate M2's manual/data foundation and M3's selected bundled model/OCR. Product/evaluation lead approves wording and field procedure before integration exit. Deliverables: live scanner, explicit evidence/state rules, deterministic pipeline tests, replay diagnostics and actual-phone checks. Exit requires M4-01–M4-17; replay alone cannot close live-processing claims.

- [ ] M4-01 — Approve direction confirmation and the field procedure. Owner: product/evaluation lead. Pass when wording distinguishes user-confirmed boarding direction from verified sign-confirmed direction, desired destination alone cannot confirm direction, and field use keeps the commuter stationary at a verified boarding area. Evidence: human wording/protocol sign-off. Source: §§4, 9.1, 19.
- [ ] M4-02 — Recover camera permission and lifecycle safely. Owner: Android lead. Pass when grant, denial, revocation, unavailable camera, leaving scanner, background/pause/resume and image-processing exceptions release/recover resources, clear overlays and restart fresh; manual selections survive. Evidence: automated state tests and live phone fault/lifecycle walkthrough. Source: FR-08, FR-15, NF-09, AT-12, AT-17, §§9.3, 12.
- [ ] M4-03 — Process current frames without a backlog. Owner: Android/CV leads. Pass when slow inference drops obsolete work, closes image resources, and route lookup does not block camera UI; instrument frame/session timestamps and capture-to-display timing. Evidence: automated delayed-work tests and live phone queue/timing logs. Source: FR-08, NF-02, NF-04, §12.
- [ ] M4-04 — Transform detected sign regions into original-resolution OCR crops. Owner: Android/CV leads. Pass when resizing, rotation and preview scaling map boxes correctly to original pixels and overlay, including clipped edges; outline the selected sign, not an assumed vehicle box. Evidence: geometric fixture assertions, crop/overlay comparisons and live phone orientation checks. Source: FR-09–FR-10, §12.
- [ ] M4-05 — Isolate one target's observations. Owner: Android/CV leads. Pass when target selection/alignment handles overlapping jeepneys, refuses uncertain associations, and cannot combine text or votes across targets; a new target starts empty. Evidence: automated association fixtures, overlapping-target replay and live phone interaction. Source: FR-14, AT-10, §9.3.
- [ ] M4-06 — Resolve codes and aliases while retaining unknown candidates. Owner: CV lead. Pass when exact codes and aliases work, ambiguous/conflicting/unsupported text can remain unknown, validation-tuned threshold and competitor separation are enforced, and candidate search is not restricted to desired routes. Evidence: deterministic matching fixtures including near-alias negatives and validation configuration. Source: FR-11, AT-09, §9.2.
- [ ] M4-07 — Keep recognition evidence separate from boarding claims. Owner: Android/CV leads. Pass when route, pattern, OCR, direction source and compatibility are distinct, observations carry target/session/timestamps, and detector confidence is never shown as boarding confidence. Evidence: state/data review and automated/UI assertions. Source: FR-12, §§9.1, 11.
- [ ] M4-08 — Display a qualified green only for a valid match. Owner: Android lead. Prerequisites: M4-01, M4-06–M4-07. Pass when supported route plus resolved direction and valid same-pattern journey show “Matches your selected journey” with direction and evidence label; user-confirmed direction is explicitly qualified as selected. Evidence: automated decision fixtures and live phone supported encounter; held-out breadth in M5-02. Source: FR-12, AT-06, §9.2.
- [ ] M4-09 — Display red only for a known incompatible direct journey. Owner: Android lead. Pass when supported route and resolved direction with no direct match show “No direct match for this journey,” without implying transfers were evaluated or treating unsupported signs as red. Evidence: automated fixtures and live phone/replay output. Source: FR-12, AT-08, §9.2.
- [ ] M4-10 — Abstain when direction is unresolved. Owner: Android lead. Pass when a known route without direction shows gray “Route identified — confirm direction,” even if a possible direction serves the destination. Evidence: automated fixtures and live phone/replay output. Source: FR-12, FR-15, AT-07, §9.2.
- [ ] M4-11 — Explain unreadable and unsupported signs. Owner: Android lead. Pass when unreadable/no reliable candidate shows gray “Cannot read route — try again or select manually,” unsupported/unverified patterns show gray “Route outside verified coverage,” and each offers manual fallback. Evidence: negative/unreadable fixtures and live phone/replay output. Source: FR-15, AT-09, §9.2.
- [ ] M4-12 — Abstain on conflicting evidence. Owner: Android/CV leads. Pass when conflicting codes, aliases, directions or targets show gray “Uncertain — select one sign and confirm route”; wrong/conflicting headsign evidence overrides user direction assumptions rather than preferring the destination. Evidence: automated conflict fixtures and live phone/replay output. Source: FR-12, FR-15, AT-16, §9.2.
- [ ] M4-13 — Identify neutrally without a journey. Owner: Android lead. Pass when scanner from Explore/no destination shows route identity/details and a plan-journey prompt, with no green/red compatibility. Evidence: automated state fixture and live phone walkthrough. Source: FR-16, AT-15, §§7.3, 9.2.
- [ ] M4-14 — Expire agreement and reject stale asynchronous results. Owner: Android/CV leads. Prerequisite: M5-01 frozen temporal rules before final verification. Pass when minimum observations/window apply only to the same target; target loss, pause/backgrounding, inference error and changed origin/destination/boarding occurrence/direction clear compatibility; prior-session work cannot restore it. Test the approved freshness bound, initially proposed at one second after last valid observation. Evidence: timed automated state tests and live phone target-loss/journey-change checks across all §9.3 states. Source: FR-13, AT-11, AT-17.
- [ ] M4-15 — Preserve immediate manual fallback on scanner failure. Owner: Android lead. Pass when denied/revoked camera, unavailable hardware, missing/corrupt detector or OCR, inference error and uncertain results explain the issue and return to usable results/details with journey selections preserved. Evidence: fault injection and live phone walkthroughs. Source: FR-05, FR-15, NF-09, AT-12, AT-14.
- [ ] M4-16 — Operate scanner and planning offline. Owner: Android lead. Pass when a fresh airplane-mode first launch performs curated search, lookup, bundled detection/OCR and matching, while basemap failure leaves readable route/direction information. Evidence: live phone first-launch test with no prior model download/cache dependency. Source: FR-17, NF-05, AT-13.
- [ ] M4-17 — Keep ordinary frames ephemeral. Owner: Android lead. Pass when ordinary camera use writes no frames to storage and uploads none; permissions, storage writes, network traffic and logs are inspected, including failure/background cases. Evaluation measurement export is explicit, versioned and separate from ordinary user video. Evidence: code/permission review, live phone storage/network inspection and evaluation export sample. Source: FR-19, NF-10, §§11–12, 14.

## M5 — Evaluation against approved criteria

Setup: evaluation lead maintains a versioned protocol and evidence index. M5-01 must precede held-out runs in M3 and M5; it does not wait for M3 completion. Final integrated evaluation requires M2–M4 exits and uses the frozen held-out set. Hardware, field, usability and accessibility methods cannot be replaced with unit tests. Deliverables: complete acceptance matrix, honest metrics/counts, phone/accessibility/usability evidence, retained failures and verified operating envelope. Exit requires M5-01–M5-12.

### Metric contract from §§13–14

| Measure | Required definition or initial target to freeze/explicitly revise |
| --- | --- |
| Route lookup, NF-01 | p95 ≤500 ms on designated phone, full bundled dataset, repeated supported queries |
| Capture-to-display, NF-02 | p95 ≤1 second for the complete pipeline inside supported envelope |
| Stable correct result, NF-03 | Within 2 seconds of readable target entering usable view; separately report too-late results |
| Sustained scanning, NF-04 | 10 minutes: no crash, unbounded queue or memory growth trend; report throttling and latency over time |
| Detection | Precision, recall, AP50, AP50–95; condition/sign-size breakdowns |
| OCR | Character error rate, exact route-code accuracy; ground-truth crops versus detected crops |
| Route recognition | Exact route/pattern correctness; confusion matrix including unknown predictions |
| Wrong-green fraction | Encounters containing any incorrect green / encounters receiving any green decision; any incorrect green within one encounter is a failure; proposed objective ≤5% |
| Matching recall | Eligible truly matching encounters with a correct green and no incorrect green / all eligible truly matching encounters; proposed objective ≥90% |
| Abstention | Encounters with no definitive compatibility result / all evaluated encounters |
| Decision coverage | Encounters with a definitive compatibility result / all evaluated encounters |
| Timing | Median/p95 capture-to-display; time to first stable correct result; proportion correct before useful window ends |
| Efficiency | Processed FPS, model size, peak memory, sustained latency, thermal behavior |
| User value | Correct selections and decision time: manual-only versus manual plus scanner |

Unresolved-direction encounters are reported separately, with any eligibility exclusions disclosed. Zero denominators mean **not estimable**, never 0% error. Report raw encounter counts and uncertainty intervals for proportions; repeated frames are not independent trials. Separate out-of-envelope/low-light outcomes, including failure to abstain. Research targets are not population safety guarantees. A narrower claim needs approval and evidence, not discarded failures.

- [ ] M5-01 — Freeze approved targets and evaluation rules before held-out access. Owner: evaluation/CV leads. Prerequisites: M1 pilot and M3-04 before test execution. Pass when the metric contract, supported-envelope proposal, useful-window definition, thresholds, candidate margin, minimum observations, temporal window and freshness bound are fixed using pilot/validation evidence, with target approval before held-out testing. Any revised target/scope follows §20. Evidence: dated human sign-off and versioned configuration. Source: §§9.3, 13–14, 19–20.
- [ ] M5-02 — Measure held-out recognition and compatibility honestly. Owner: evaluation lead. Prerequisites: M3 comparison, M4 integration, M5-01. Pass when detection/OCR/route metrics, wrong-green, matching recall, abstention and coverage follow the metric contract and meet approved acceptance criteria within the supported envelope; unresolved direction, zero denominators, intervals and out-of-envelope failures remain visible. Evidence: frozen-set evaluation output, encounter counts, confusion tables and authorized failure references. Source: FR-09–FR-16, §14, model-dependent AT-06–AT-10, AT-15–AT-16.
- [ ] M5-03 — Verify local query latency. Owner: Android/evaluation leads. Pass when repeated supported queries against the full bundled dataset meet the approved NF-01 p95 target on the designated phone, independently of inference timing. Evidence: live phone measurement export, query list, repeats and percentile calculation. Source: NF-01, §10.
- [ ] M5-04 — Verify useful end-to-end decision timing. Owner: evaluation/CV leads. Pass when the full pipeline meets approved NF-02/NF-03 targets; report median/p95, readable-view entry to stable-correct time and proportion correct before independently annotated useful-window end, retaining late/absent outcomes. Evidence: held-out annotated encounters plus live phone instrumentation. Source: NF-02–NF-03, §§13–14.
- [ ] M5-05 — Verify sustained scanning stability. Owner: Android/evaluation leads. Pass when ten continuous minutes on the designated phone have no crash, unbounded queue or memory growth trend; report FPS, peak memory, latency over time and thermal throttling. Evidence: live phone sustained-run logs and configuration. Source: NF-04, §14.
- [ ] M5-06 — Verify first-launch offline capability. Owner: evaluation lead. Pass when M4-16's first-launch procedure succeeds on the evaluation build with bundled lookup/detection/OCR and usable map fallback; record prior install/cache state. Evidence: live phone airplane-mode walkthrough. Source: NF-05, FR-17, AT-13.
- [ ] M5-07 — Verify status without reliance on color. Owner: Android/evaluation leads. Pass when green/red/gray/neutral and error states have understandable text/icons, accessible names, sufficient contrast and no color-only instructions. Evidence: UI/contrast inspection and live phone review of each status. Source: NF-06, §§6, 9.2.
- [ ] M5-08 — Verify large text, TalkBack and touch interaction. Owner: Android/evaluation leads. Pass when large-text and TalkBack manual journeys/search/results/details/fallback remain navigable without clipping essential actions, targets are ≥48 dp, and changing statuses do not overwhelm assistive announcements. Do not claim blind users can independently aim the scanner. Evidence: live phone accessibility procedure/settings and recorded findings. Source: NF-07–NF-08.
- [ ] M5-09 — Verify failure recovery and frame privacy on the evaluated build. Owner: evaluation lead. Pass when denial/revocation, camera unavailable, missing/corrupt models and inference faults preserve manual use, invalid route updates preserve valid data, and ordinary use still stores/uploads no frames. Evidence: live phone fault and storage/network checks cross-referencing M2-14, M4-15, M4-17. Source: NF-09–NF-10, AT-12, AT-14.
- [ ] M5-10 — Close the complete acceptance matrix. Owner: evaluation lead. Pass when every AT-01–AT-18 has procedure, expected/actual outcome, build/fixture/device and evidence; all deterministic route/state cases pass, and model-dependent cases are evaluated across frozen held-out encounters within the envelope rather than a selected success. Evidence: automated routing/state results plus required replay/live-phone/held-out records linked to the traceability table. Source: §15, §17.
- [ ] M5-11 — Compare manual-only and scanner-assisted usability. Owner: evaluation lead. Prerequisite: approved sample/recruitment/task protocol before participants are tested; M4 safe field procedure. Pass when balanced task order is used and participant count, correct selections, decision times and limitations are reported for both conditions, without population-wide claims from a convenience sample. Evidence: protocol sign-off, authorized observations and anonymized results. Source: §§14, 19.
- [ ] M5-12 — Approve the verified operating envelope and limitations. Owner: project/product/evaluation leads. Prerequisites: M5-02–M5-11. Pass when actual phone/route/lighting/sign size/distance/angle/motion/type conditions and acceptance outcomes justify the release claim. Failed objectives stay recorded; narrower claims require explicit approved scope/PRD change and evidence meeting approved criteria in that envelope, with new held-out data if earlier test examples informed tuning. Label scanner experimental where required; unresolved P0 failures block release. Evidence: human sign-off, retained measurements, change references and completed release-record envelope. Source: §§5, 17–20.

## M6 — Verified Android demonstration release

Setup: project lead coordinates release custody and academic delivery; Android lead builds; evaluation lead checks the exact artifact. Prerequisites: all M1–M5 exits, licenses/access approval for the affected materials, and no unresolved P0 acceptance failure. Deliverables: source/build instructions, identified installable release, verified device journeys, authorized reproducibility package, academic materials, demo script, limitations and final sign-off. Exit requires M6-01–M6-10; packaging is not owner approval.

- [ ] M6-01 — Reproduce the release from a clean checkout. Owner: Android lead. Pass when another documented clean environment builds the named source revision using actual recorded setup/commands and pinned assets/dependencies, and reruns the required deterministic tests. Identify build environment, artifact location/checksum and application/version. Define and verify reproducibility (same source/assets/procedure and installable behavior; explain any byte-level differences). Evidence: clean-checkout build/test logs and completed release-record fields. Source: FR-19, §§12, 16–17.
- [ ] M6-02 — Fresh-install the exact release on the designated phone. Owner: evaluation lead. Prerequisite: M6-01. Pass when the checksum-identified artifact installs without a previous app state, versions match source/data/model/runtime records, and device model/Android build match the approved envelope. Evidence: live phone installation/version record. Source: §§5, 12, 17.
- [ ] M6-03 — Prove airplane-mode first launch of the release. Owner: evaluation lead. Prerequisite: M6-02, before any online app launch. Pass when first launch without connectivity or downloaded assets supports curated search, manual results/details/Explore and recognition with bundled models, with readable map fallback. Evidence: live phone recording/log and install/cache/network state. Source: FR-05–FR-07, FR-17, NF-05, AT-13.
- [ ] M6-04 — Complete the successful commuter journey on that release. Owner: evaluation lead. Prerequisites: M6-02–M6-03. Pass when supported origin/destination → direct results → verified boarding/direction → scanner permission → one live sign within declared conditions → qualified green → route details → manual results works end to end. Record exact pattern/occurrence IDs and direction evidence; no pursuit of a vehicle is needed. Evidence: live phone/field observations on the identified release, with condition and useful-window timing record. Source: §§4, 7.1, AT-01, AT-06.
- [ ] M6-05 — Complete the failure and recovery journey on that release. Owner: evaluation lead. Prerequisites: M6-02–M6-03. Pass when a selected journey survives camera denial/unavailability or model fault and immediate manual fallback; unreadable/unsupported/conflicting sign produces gray, departed target clears status, changed journey cannot inherit late green, and failed basemap still leaves details understandable. Evidence: live phone fault walkthrough and timed state checks tied to the release. Source: §§7.2, 9, AT-09, AT-11–AT-14, AT-16–AT-17.
- [ ] M6-06 — Make the source and authorized research package reproducible. Owner: project/CV/data leads. Pass when reviewers can access source/build instructions, route bundle/provenance, annotation/split manifests, all three experiment configurations/results, metrics, failure analysis and model/runtime versions through documented permitted locations. Private raw data may use custodian/access instructions; verify authorized access rather than publishing it. Evidence: package manifest/version links and reviewer access/reproduction check. Source: FR-19, §§11, 14, 16.
- [ ] M6-07 — Prepare the constrained academic report. Owner: project lead. Pass when the report is at most 15 pages, 12-point font, long bond paper, and includes actual three-model comparisons/charts, preprocessing inputs/outputs, implemented DSA and complexity, metric definitions/results interpretation, failure analysis and limitations. Cross-check actual supplied course materials; obtain missing materials before closure. Evidence: rendered report inspection and rubric review. Source: §16; research brief course alignment.
- [ ] M6-08 — Record individual contributions and required disclosure. Owner: project lead. Pass when every member's contribution is traceable to actual work and required project/course AI-use disclosure is truthful in the appropriate records. Evidence: contribution records and human review against course rules. Source: §16; supplied task disclosure requirement.
- [ ] M6-09 — Rehearse an honest demonstration. Owner: product/project lead. Pass when the script uses the exact release/coverage, includes manual fallback, versions and known limitations, and labels every live-camera, prerecorded and controlled-sign segment; no replay is presented as approaching-vehicle performance or unmeasured conditions as supported. Evidence: rehearsed script, segment/evidence list and review. Source: §§5, 16–17, 20.
- [ ] M6-10 — Approve and hand over the exact demonstration release. Owner: project owner with evaluation lead. Prerequisites: all other release boxes, M6-01–M6-09. Pass when the owner signs the completed release record against exact artifact checksum/source/data/model/device/envelope, approved criteria, complete evidence and known limitations, and the team/reviewer receives the authorized package at a recorded accessible location. Record approver/date and delivery receipt; no public upload is implied. Evidence: human sign-off and verified handover. Source: §§16–17, 19–20.

## Traceability audit

Each row maps a requirement to its implementation/proof tasks; M5-10 audits all acceptance scenarios and M6-10 approves the exact release. Mapping is a coverage plan, not a test result.

| Requirement | Task IDs or explicit disposition |
| --- | --- |
| FR-01 | M2-04, M2-16 |
| FR-02 | M2-04 |
| FR-03 | M2-05, M2-06, M2-07, M2-08 |
| FR-04 | M2-09, M2-10, M2-13 |
| FR-05 | M2-16, M4-15, M6-05 |
| FR-06 | M2-12, M6-03 |
| FR-07 | M2-10, M2-11, M6-03 |
| FR-08 | M4-02, M4-03, M5-05 |
| FR-09 | M3-06, M4-04, M5-02 |
| FR-10 | M4-04, M3-07, M5-02 |
| FR-11 | M4-06, M5-02 |
| FR-12 | M4-07, M4-08, M4-09, M4-10, M4-12 |
| FR-13 | M4-05, M4-14 |
| FR-14 | M4-05 |
| FR-15 | M4-10, M4-11, M4-12, M4-15 |
| FR-16 | M4-13 |
| FR-17 | M2-16, M4-16, M5-06, M6-03 |
| FR-18 | M2-02, M2-14, M2-15 |
| FR-19 | M2-13, M3-06, M3-08, M4-17, M6-01, M6-06 |
| FR-20 | P1 deferred; no release checkbox; see deferred work |
| NF-01 | M5-03 |
| NF-02 | M4-03, M5-04 |
| NF-03 | M1-04, M5-04 |
| NF-04 | M4-03, M5-05 |
| NF-05 | M4-16, M5-06, M6-03 |
| NF-06 | M5-07 |
| NF-07 | M5-08 |
| NF-08 | M5-08 |
| NF-09 | M4-02, M4-15, M5-09 |
| NF-10 | M4-17, M5-09 |
| AT-01 | M2-05, M2-09, M6-04 |
| AT-02 | M2-06 |
| AT-03 | M2-07 |
| AT-04 | M2-08 |
| AT-05 | M2-04 |
| AT-06 | M4-08, M5-02, M6-04 |
| AT-07 | M4-10, M5-02 |
| AT-08 | M4-09, M5-02 |
| AT-09 | M4-06, M4-11, M5-02, M6-05 |
| AT-10 | M4-05, M5-02 |
| AT-11 | M4-14, M6-05 |
| AT-12 | M4-02, M4-15, M5-09, M6-05 |
| AT-13 | M2-11, M4-16, M5-06, M6-03 |
| AT-14 | M2-14, M4-15, M5-09, M6-05 |
| AT-15 | M4-13, M5-02 |
| AT-16 | M4-12, M5-02, M6-05 |
| AT-17 | M4-02, M4-14, M6-05 |
| AT-18 | M2-04 |
| M1 | M1-01–M1-08 |
| M2 | M2-01–M2-17 |
| M3 | M3-01–M3-10 |
| M4 | M4-01–M4-17 |
| M5 | M5-01–M5-12 |
| M6 | M6-01–M6-10 |
| §§4–7 safe use, coverage, all screens/journeys | M1-03, M2-09–M2-13, M4-01, M4-13, M6-03–M6-05 |
| §9.1 distinct evidence/direction | M4-01, M4-07, M4-08, M4-10, M4-12 |
| §9.2 all seven decision-table rows | M4-08–M4-13; conflict row M4-12; unreadable and unsupported rows M4-11 |
| §9.3 all states, agreement and freshness | M4-02, M4-05, M4-14, M5-01 |
| §10 graph/data structures and baseline | M2-05–M2-08, M2-17 |
| §11 integrity, manifest and update recovery | M2-01, M2-02, M2-14, M2-15, M3-03, M3-06, M4-07 |
| §12 technical boundaries and image resources | M1-05–M1-07, M2-03, M4-02–M4-04 |
| §14 collection, splits, comparison and metric denominators | M3-01–M3-09, M5-01–M5-05; metric contract above |
| §14 usability protocol | M5-11 |
| §16 academic/research deliverables | M2-17, M6-06–M6-09 |
| §17 milestone exits and completion | Phase exit rules, M5-12, M6-01–M6-10 |
| §18 risks | M1-04–M1-08, M2-01, M2-15, M3-04, M3-09, M4-05, M4-06, M4-12, M4-14, M5-02, M5-05 |
| §20 explicit change control and preserved held-out results | M1-08, M3-10, M5-01, M5-12, M6-10 |

### Every §19 open decision

Decisions remain open until the named task has the required approval/evidence. Deadlines are milestone-relative, not fabricated calendar dates.

| Decision | Responsible role | Needed by | Task IDs |
| --- | --- | --- | --- |
| Exact patterns, boarding points, verification contacts | Project lead / data steward | Resolved 2026-11-06 for the approved Jaro Plaza–WVSU corridors; source maps and four explicit patterns recorded | M2-01 |
| Phone, minimum Android and envelope | Android/CV leads | Select/propose by M1 exit; verify by M5 exit | M1-02, M1-08, M5-12 |
| SSD variant, checkpoints, OCR, export/runtime | CV lead | Feasible/pinned by M1 exit | M1-05–M1-07; final choice M3-10 |
| Map/provider, attribution, network behavior | Android lead | Before M2 exit | M2-11 |
| Dataset size, splits, permissions, retention | Data/evaluation leads | Before full collection; pilot permission before pilot | M3-01–M3-04 |
| Thresholds, temporal window, freshness, targets | Evaluation/CV leads | Before held-out testing | M5-01 |
| Direction wording and field procedure | Product/evaluation lead | Before M4 exit/participant field use | M4-01 |
| Route reverification and invalidation | Project lead / data steward | Resolved 2026-11-06: review at least every 90 days, before release, and on route-change report; affected pattern unavailable pending check | M2-15 |
| Instructor acceptance, deadline, assignments | Project lead | Before schedule commitment | M1-01 |
| Code/model/data licenses and public release | Project lead | Before affected distribution; public release separate | M3-01, M6-06, M6-10 |
| Usability sample and protocol | Evaluation lead | Before participant testing | M5-11 |

## Deferred work — no release checkboxes

FR-20 draft-journey restoration is P1 and does not block completion. If explicitly promoted later, restoration must validate selections against current data and never restore active recognition status; add scoped tasks through change control.

Transfers, fares, ETA, live fleet tracking, bookings/payments/accounts, community submissions/admin portal, cloud video inference, nationwide/citywide expansion, full offline basemaps, arbitrary-address geocoding, turn-by-turn walking, GPS-assisted positioning, extra shortest-path algorithms, broad low-light claims and multi-target recognition are outside this release. Public app/data/model distribution needs a separate explicit decision and rights review. None can acquire a completion checkbox without owner promotion and applicable PRD change approval.

## Document audit and delivery history

2026-11-06 — Tracker created after explicit instruction to implement the approved plan. Only this file is authorized for delivery; no app implementation, fieldwork, commit, push, deployment, branch/worktree, Git identity change or PRD edit is included.

Document verification results are recorded below after reading back the saved tracker. They establish checklist coverage only, not a passed app release. Future app tasks remain open.

| Writing-task check | Result and actual method |
| --- | --- |
| Saved-file readback | PASSED — `Get-Content docs/phasetask.md` and focused `Select-Object` reads; manually compared task content with the full current PRD and contextual documents. |
| Task structure and counts | PASSED — PowerShell `[regex]::Matches` assertions: 74 unique IDs, all 74 unchecked; each has Owner, Pass when, Evidence and Source. Counts: M1 8; M2 17; M3 10; M4 17; M5 12; M6 10. Six milestone headings. Initial wording check failed on M1-06's inherited pass condition; M1-06/M1-07 now state explicit conditions and the check passes. |
| References and links | PASSED — PowerShell assertions resolve every referenced task ID and every member of task ranges; `Test-Path` validates all four local document links. |
| Coverage and optional scope | PASSED — PowerShell assertions find all 20 FR, 10 NF and 18 AT rows, all 11 §19 decision rows and no deferred-section checkboxes. Manual review confirms six milestone mappings plus decision-table, data integrity, protocol, academic and change-control coverage. An initial decision-count assertion included the Markdown separator; correcting that assertion gives 11 decision rows, with no document change needed. |
| Metric/source review | PASSED — manually checked numerical targets and encounter-level denominators against §§13–14; retained not-estimable handling, counts/intervals, unresolved directions and out-of-envelope failures. |
| Dependency review | PASSED — permissions M3-01 precede M1 pilot; targets M5-01 precede M3 held-out runs; final M5 evaluation follows integration. Phase numbering does not imply waiting to start these prerequisite decisions. |
| Success-journey paper walkthrough | PASSED for document coverage only — M6-01 clean build → M6-02 fresh install → M6-03 offline first launch → M2-04/M2-09 plan/results → M4-08 live qualified match → M2-10 details; M6-04 requires that complete journey on the identified release. |
| Failure-journey paper walkthrough | PASSED for document coverage only — same build/install → chosen journey → M4-02/M4-15 denial/failure or M4-11/M4-12 uncertainty → preserved manual results; M4-14 prevents stale green, M2-11 preserves offline details, M6-05 repeats recovery on the release. M5-10 separately blocks deterministic AT failures. |
| Whitespace and change boundary | PASSED — `git diff --check` and `git diff --no-index --check -- /dev/null docs/phasetask.md` reported no whitespace errors. Git noted expected LF-to-CRLF conversion. `git status --short --untracked-files=all`, `git diff --name-only`, and `git ls-files --others --exclude-standard` showed only new, untracked `docs/phasetask.md`; no tracked file changed. |
| App verification | COULD-NOT-RUN — no app, device designation, verified data/models or build/test entry point exists in this repository. No future app task was performed. Course-source confirmation, human approvals, live feasibility and Android release remain unverified. |

Tracker delivery: saved at the repository root for review, uncommitted and unpushed. The Android release is not delivered or approved. Next action for the project owner: assign the project lead for M1-01 so instructor acceptance, actual deadline and team responsibilities can be confirmed.

### M2 route-data and phone evidence update — 2026-11-06

This update supersedes the earlier planning-only M2 status above. The project lead supplied the two City Planning route maps, confirmed the Jaro Plaza travel direction against the map arrows and rider experience, and approved their use. Copies and hashes are preserved in [M2 source maps](evidence/m2/source-maps/README.md).

**Scope and data:** the installed catalog contains only the Jaro Plaza (Cathedral area) ↔ WVSU Gate 2 corridor on Routes 3 and 10: four explicit directional patterns, two per route. The record does not claim citywide route coverage, a service schedule, vehicle frequency, or turn-by-turn accuracy. The route diagram is schematic. Stop coordinates are attributed to OpenStreetMap contributors.

**Route-data policy:** for this prototype the project lead is the data steward and rechecks sources at least every 90 days, before each data release, and when a route change is reported. A conflicting or unconfirmed pattern is made unavailable until checked. The app displays route verification dates and excludes inactive or unverified patterns.

**Build and device evidence:** JDK 17.0.18, Gradle 8.11.1, AGP 8.10.0, Kotlin 2.2.10, compile/target SDK 36, min SDK 34. The final command .\gradlew.bat clean assembleDebug testDebugUnitTest connectedDebugAndroidTest passed: 11 JVM tests and 10 connected instrumentation tests on TECNO KJ8, Android 14/API 34. .\gradlew.bat assembleRelease also passed. A fresh debug install on the Tecno loaded the bundled catalog. With Wi-Fi disabled, mobile data service disabled, and Android reporting no active default network, an ADB-driven phone walkthrough searched Jaro Cathedral → WVSU, opened results and Route 10 details, and opened the Scan fallback with the chosen route retained. The app requests no camera or internet permission. This verifies device UI behavior, not independent field observation.

| Artifact | SHA-256 / result |
| --- | --- |
| Dataset | iloilo-jaro-wvsu-2026-11-06; four active directional patterns |
| SQLite inside the bundle | 61035f47264379d600068d6bf9e3a12ec3fbf6298a741a37bdf64c1e2e58b4b8 |
| app/src/main/assets/routes.bundle | d55ca576d90f95de941e6e13a4d9a1758ca010811ae188d600f3698bdf07dbf9 |
| Debug APK | 8fdf99596715f83530fe5e8302f1c86e2299e8ca0da2bada676bc341f241bbb9 |
| Unsigned release APK | cf818aa306e68f1fe0c77a217132b4f908ba5b7c2021f9302844cff696d299b0 |

The release APK contains the checked-in route bundle. Synthetic fixture markers were absent from release DEX. Release-manifest inspection found no requested permissions. The release APK is unsigned.

| ID | Status and evidence |
| --- | --- |
| M2-01 | **PASSED — approved corridor scope.** Four named directional patterns with endpoint boarding/alighting occurrences, route geometry, source references, contacts, and dates are in data/iloilo-jaro-wvsu.json. Opposite directions are separate patterns. Source images and project-lead direction confirmation are recorded above. Scope is only Jaro Plaza–WVSU. |
| M2-02 | **PASSED.** The generated versioned SQLite bundle has manifest and checksum; RouteBundleStore validation and physical-device rejection/recovery tests cover broken references, bad coordinates/sequences, incomplete patterns, invalid geometry/provenance, checksum mismatch, malformed SQLite/input, and preserving the previous valid data. The live bundle loads from a fresh install. |
| M2-03 | **PASSED.** Pinned toolchain builds cleanly; 11 JVM and 10 Tecno instrumentation tests pass. Release assembly and lint-vital pass. See build reports and the hashes above. |
| M2-04 | **PASSED.** JVM resolver tests cover missing, unsupported, unique, ambiguous, and identical endpoints; app tests use the real aliases Jaro Cathedral and WVSU and find both supported routes. |
| M2-05 | **PASSED.** BFS and the independent ordered baseline agree on all nine planned synthetic cases: SYN-FWD, SYN-REVERSE, SYN-REPEAT-LATE, SYN-NO-BOARD, SYN-NO-ALIGHT, SYN-SHARED, SYN-BRANCH, SYN-LOOP-TERMINAL, and SYN-MULTI-PATTERN. |
| M2-06 | **PASSED.** SYN-REVERSE and SYN-REPEAT-LATE confirm no inferred reverse ride and preserve a later eligible occurrence; baseline agrees. Real route data stores each direction explicitly. |
| M2-07 | **PASSED.** SYN-SHARED and SYN-BRANCH return no unsupported direct ride or transfer; baseline agrees. |
| M2-08 | **PASSED.** SYN-REPEAT-LATE and SYN-LOOP-TERMINAL retain distinct occurrences and do not wrap past a terminal; baseline agrees. |
| M2-09 | **PASSED.** Fresh phone install and instrumentation find direct Jaro Plaza–WVSU journeys on Routes 3 and 10 in both directions. Results → Details → Scan retains the selected route. |
| M2-10 | **PASSED — corridor scope.** Route 10 details show explicit direction, selected stops, local geometry, source, and verification date. The geometry is schematic, as stated in the app. |
| M2-11 | **PASSED.** Route diagram and ordered text were opened on the Tecno while offline; no basemap or network access is used. |
| M2-12 | **PASSED.** The bundled-route instrumentation test opens Explore with the four verified patterns available without an active journey. |
| M2-13 | **PASSED.** About/Data displays dataset version, four patterns, route limits, sources, policy, permissions, and that no recognition model is included; the bundled-route test asserts the version, count, and model status. |
| M2-14 | **PASSED.** Physical-device bundle tests reject malformed updates and recover the previous valid catalog after restart; fresh install also reloads the bundled route asset. Synthetic invalid bundles are test-only. |
| M2-15 | **PASSED — project policy.** The project policy sets a 90-day maximum recheck interval, recheck before release and on reported change, names the project lead as steward, and makes conflicting or unconfirmed patterns unavailable. Automated tests confirm inactive/unverified patterns cannot recommend a ride. |
| M2-16 | **PASSED.** The Tecno opened bundled-route results, details, and Scan fallback with no active network; camera is not used and the manifest requests no camera permission. Ordered stop text remains available. |
| M2-17 | **PASSED.** The O(V+E) adjacency/BFS, O(N) ordered-scan, and average-case hash lookup explanations and synthetic measurements are recorded above. Phone results use five warmups and 200 timed calls per size; thermal conditions were not controlled, so these are local engineering measurements, not a service guarantee. |

### Current acceptance boundary

M2-01–M2-17 are complete for this explicitly limited DSA prototype scope. The catalog does not cover all of either route or other Iloilo routes. Official map streets do not establish current operating conditions, schedules, service frequency, exact curbside boarding points, or temporary detours. An OEM lock-screen overlay prompt appeared on an earlier first-launch attempt; it was not granted, is not requested by ParaPo, and did not appear during the final fresh-install walkthrough.
