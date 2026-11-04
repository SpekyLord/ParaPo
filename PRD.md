# ParaPo — Product Requirements Document

Version: 1.0 draft
Date: November 4, 2026
Status: Proposed; implementation and field validation pending
Platform: Android
Initial geography: Selected Iloilo City corridors
Languages: Kotlin for the application; Python for model experiments; SQL/SQLite for local route data

## 1. Purpose and authority

ParaPo helps commuters find a direct jeepney journey and identify whether a specific visible jeepney is consistent with that journey. This document defines the first complete research prototype, its behavior, acceptance criteria, and evidence needed before demonstrating it as useful live assistance.

This PRD is the implementation baseline. The [research brief](docs/research-brief.md) contains evidence and rationale; the [original concept](docs/original-concept.md) preserves the initial vision. Broader features in the original concept do not automatically enter this release. Requirements below are proposed product decisions, not claims that a feature exists or a performance target has been met.

## 2. Problem and opportunity

Unfamiliar commuters need to know which route serves their destination, where to board, and whether the jeepney they see is the appropriate one. Overlapping routes, small signs, alternate names, and directional differences complicate those decisions.

Local route-planning alternatives already exist. ParaPo focuses on connecting a camera observation to a verified direct journey. The project must demonstrate whether this additional camera interaction improves identification over the manual route finder alone. It must remain useful when the camera fails.

## 3. Goals and non-goals

### Goals

- Provide understandable direct-route recommendations within explicitly verified coverage.
- Identify readable route signage using a phone camera and local processing.
- Separate route recognition from journey compatibility and direction evidence.
- Express uncertainty rather than manufacture a route match.
- Compare three related sign detectors in an actual controlled experiment.
- Demonstrate useful data structures and graph traversal with explainable route results.
- Produce an installable prototype and reproducible evaluation materials.

### Non-goals for this release

Multi-jeepney transfers, fares, arrival-time prediction, live fleet tracking, bookings, payments, accounts, community submissions, an administrator portal, cloud video inference, nationwide coverage, full offline basemaps, arbitrary-address geocoding, and turn-by-turn walking navigation are excluded. The system does not determine vehicle capacity, stopping intent, road-crossing safety, or guaranteed service completion.

## 4. Users and key needs

| User | Need | Successful outcome |
| --- | --- | --- |
| Student or first-time commuter | Identify a route between supported landmarks | Understand route name, boarding point, direction, and drop-off point |
| Visitor unfamiliar with local labels | Resolve a visible sign to route information | Recognize a supported route or receive an honest uncertain result |
| Regular commuter exploring a new journey | Inspect a route independently | Browse its directional path and ordered landmarks |
| Research evaluator | Understand algorithm selection and limitations | Reproduce comparisons and distinguish live evidence from recorded demonstrations |

Primary use takes place while the commuter is stationary at a verified boarding area. The application must not require walking into traffic, pursuing a vehicle, or continuously looking at the screen while walking.

## 5. Release scope and assumptions

The first release supports 3–5 verified directional route patterns, not necessarily 3–5 complete bidirectional routes. Every included direction or branch counts as its own pattern. It supports a curated local catalog of landmarks and boarding/alighting points, daylight operation, and one selected vehicle/sign target at a time.

Manual origin and destination selection is sufficient. Location permission and automatic positioning are not required. The initial interface is English with authentic local route and landmark names preserved. Device, minimum Android version, route identities, model variants, and map provider are unresolved decisions recorded in Section 19.

The supported operating envelope must eventually state tested phone models, lighting, sign size, distance ranges, movement conditions, and vehicle/sign types. Unmeasured conditions must not be advertised as supported.

## 6. Product navigation and screens

| Screen | Required content and actions |
| --- | --- |
| Home / Plan journey | Coverage notice; searchable origin and destination; Find routes; Explore routes |
| Route results | Direct matches; route number/name; direction; boarding and alighting points; Open details; Scan |
| Route details | Directional pattern; ordered landmarks; route geometry; selected boarding/alighting markers; source and verification date |
| Scanner | Camera preview; single-target guidance; sign outline; recognized route card; status text; direction evidence; route details and manual fallback |
| Explore routes | Supported route list and search; open directional patterns without requiring a journey |
| About / Data | Prototype limitations; route-data version; verification information; application/model version; permission explanation |

Route information must remain readable if the basemap cannot load. A saved local route geometry or ordered landmark list must still explain the route. Maps cannot be the sole representation of direction.

## 7. User journeys

### 7.1 Plan and scan

1. Select a supported origin and destination.
2. Review direct routes and choose a specific boarding point and direction.
3. Open the scanner and grant camera permission if needed.
4. Aim at one visible route sign.
5. Read the route identity and compatibility result, including whether direction is user-selected or sign-confirmed.
6. Open route details or return to manual selection.

### 7.2 Camera unavailable or uncertain

Permission denial, unreadable signs, missing models, or inference failure must preserve the selected journey. The user can immediately return to manual results. No recognition outcome is silently converted into a route recommendation.

### 7.3 Explore without a destination

The user browses routes and directional patterns. A scanner opened without an active journey may identify a route and open details, but must show a neutral identification result rather than green or red compatibility.

### 7.4 No direct match

For supported inputs with no valid same-pattern journey, show “No direct route found in supported coverage.” Explain that transfers and routes outside coverage are not evaluated. An unknown destination instead produces an unsupported-location state, not a search result that claims no service exists.

## 8. Functional requirements

Priority P0 means required for the prototype; P1 means desirable only after P0 is complete.

| ID | Priority | Requirement | Acceptance criterion |
| --- | --- | --- | --- |
| FR-01 | P0 | Search curated locations and aliases | Selection resolves to a unique location ID; ambiguous names show choices |
| FR-02 | P0 | Validate journey inputs | Missing locations are explained; identical origin/destination shows no ride needed |
| FR-03 | P0 | Find direct directional journeys | Every result has an allowed boarding occurrence before an allowed alighting occurrence in the same pattern |
| FR-04 | P0 | Explain each result | Route, direction, boarding, drop-off, and verification information are visible |
| FR-05 | P0 | Support manual selection | Route finding and details work without camera permission or a recognition result |
| FR-06 | P0 | Browse route information | Supported patterns are searchable independently of a journey |
| FR-07 | P0 | Display route geometry | Map shows directional path and selected points; a textual fallback works without map access |
| FR-08 | P0 | Process live camera frames | Use current frames; avoid accumulating a backlog; release the camera when leaving the scanner |
| FR-09 | P0 | Locate route signs | Selected detector returns sign regions; UI outlines the sign rather than pretending to identify a whole vehicle |
| FR-10 | P0 | Read sign text | OCR receives an original-resolution crop corresponding to the detected sign |
| FR-11 | P0 | Resolve route candidates | Exact codes and aliases are supported; ambiguous, conflicting, and unsupported text can remain unknown |
| FR-12 | P0 | Evaluate compatibility | Apply Section 9 rules; never infer direction from route name alone |
| FR-13 | P0 | Stabilize results | Agreement is restricted to the same target and expires when the target or journey changes |
| FR-14 | P0 | Handle multiple signs | Require target selection or alignment; do not combine separate vehicle observations |
| FR-15 | P0 | Expose uncertainty | Each uncertain state has a reason and a manual fallback action |
| FR-16 | P0 | Identify without a journey | Display recognized route information neutrally; no compatibility claim |
| FR-17 | P0 | Operate locally | Bundled models, route lookup, and curated location search work with connectivity disabled |
| FR-18 | P0 | Load valid route data | Invalid relationships or incomplete patterns are excluded without destroying the last valid dataset |
| FR-19 | P0 | Preserve reproducibility | Evaluation builds report dataset/model/runtime versions and export measurements without recording ordinary users' video |
| FR-20 | P1 | Restore a draft journey | Locally restore prior selections only if they still exist in the current dataset; never restore an active recognition status |

## 9. Recognition and decision rules

### 9.1 Distinct evidence

Store route identity, pattern identity, OCR evidence, direction evidence, and journey compatibility separately. A detector's confidence describes localization, not the probability that boarding is correct. Do not display it as boarding confidence.

Direction evidence can be a verified directional headsign or an explicitly user-confirmed boarding direction. A desired destination alone is not confirmation of the observed vehicle's direction. When using user confirmation, always qualify the result as applying to the selected direction. Conflicting sign evidence overrides that assumption and returns uncertainty.

### 9.2 Decision table

| Evidence | Result | Copy |
| --- | --- | --- |
| Supported route, resolved pattern/direction, valid direct journey | Green | “Matches your selected journey” plus direction and evidence label |
| Supported route and direction, known incompatible direct journey | Red | “No direct match for this journey” |
| Route identified but direction unresolved | Gray | “Route identified — confirm direction” |
| Sign unreadable or no reliable candidate | Gray | “Cannot read route — try again or select manually” |
| Unsupported route or unverified pattern | Gray | “Route outside verified coverage” |
| Conflicting codes, aliases, directions, or targets | Gray | “Uncertain — select one sign and confirm route” |
| No active journey | Neutral | Route identity only; prompt to plan a journey |

A gray result cannot become green solely because a fuzzy matcher must return its nearest known label. Retain an unknown option and require a validation-tuned match threshold and separation from competing candidates. Do not restrict recognition candidates only to routes the user hopes to ride.

### 9.3 State and freshness

Scanner states are inactive, permission-needed, searching, reading, identified-neutral, matching, non-matching, uncertain, and unavailable. A new target starts with no inherited votes. Changing origin, destination, boarding occurrence, or direction clears prior compatibility immediately.

Initial engineering target: expire a displayed decision within one second of the last valid observation of that target. This is a proposed configurable evaluation parameter, to be fixed after the pilot. Camera pause, app backgrounding, target loss, or an inference error clears the overlay. Late asynchronous results from a previous session must be discarded.

Temporal agreement must include a limited time window and minimum observations selected using validation encounters. Never combine observations when association is uncertain. Show searching/uncertain until sufficient evidence exists.

## 10. Route model and DSA requirements

A route is a named service. A pattern is one specific ordered operational path, including its direction and branch. A stop occurrence is a particular visit to a location within that pattern; repeated visits must have distinct sequence identifiers.

Use adjacency lists whose nodes are `(pattern_id, sequence)` and whose directed edges connect valid subsequent occurrences. BFS determines same-pattern reachability. An independent ordered-list implementation supplies a correctness baseline. Shared geographic locations do not create transfer edges in this release.

A direct result requires permitted boarding and alighting, destination reachability after boarding, and no change of pattern. Opposite-direction patterns must be independently verified rather than constructed by reversing an outbound list. A loop is represented as a finite operational sequence; do not invent another lap or a ride through a terminal unless continuous service is verified.

The finder returns all eligible direct patterns with deterministic ordering, such as route number then direction. It must not label a result fastest, cheapest, or shortest without an implemented and validated optimization objective. Do not introduce Dijkstra or A* merely for presentation value.

Document adjacency-list space O(V+E), BFS traversal O(V+E), ordered-scan baseline O(N), and average-case hash-map alias lookup. Measure these on the actual implementation; separate route search time from model processing.

## 11. Logical data requirements

| Entity | Minimum fields |
| --- | --- |
| Dataset manifest | version, schema version, publication date, source references |
| Route | ID, public number/name, aliases, support status |
| Pattern | ID, route ID, direction label, branch/headsign meaning, geometry, verification date, verification source, active status |
| Location | ID, display name, aliases, coordinates, location type |
| Stop occurrence | pattern ID, sequence, location ID, boarding allowed, alighting allowed |
| Alias mapping | normalized text, candidate route/pattern IDs, exact-code rules |
| Observation in memory | session/target ID, timestamp, sign box, text, candidate set, evidence state |
| Research sample | sample/encounter/session IDs, split, sign boxes, transcription, independent route/direction ground truth, conditions, collection provenance |
| Experiment record | dataset split/version, model/checkpoint, training setup, seed, export/runtime/device details, metric definitions and results |

Require unique identifiers, valid references, ordered sequences, valid coordinates, and consistent source/verification information. Unknown direction must remain explicitly unknown. Route geometry must correspond to the pattern it illustrates. Landmark proximity alone does not establish an allowed boarding point.

The first release may bundle a versioned database with the app; no remote administration service is required. Updates must be validated before replacing working data. Explicitly unverified or inactive patterns cannot produce affirmative compatibility. A maintenance/reverification interval remains to be chosen; show verification dates and avoid claiming current live operational status.

## 12. Technical boundaries

Kotlin owns the Android interface, camera lifecycle, route logic, local storage, and result display. Python owns collection tooling, annotation preparation, training, and evaluation. SQLite stores versioned route information. Python is not required on the user's phone.

CameraX, a bundled on-device OCR engine, and a compatible mobile inference runtime are planned components. Exact dependency versions and SDK compatibility are selected after export tests. Keep the selected model and OCR component replaceable within the recognition pipeline without building a general plugin system.

Preserve the original image for crops and correctly transform bounding boxes through resizing, rotation, and preview scaling. Handle image resources and errors without leaving the camera locked. Route lookup must not block the camera UI. App release, route data, model, and experiment versions must be traceable.

## 13. Nonfunctional requirements and proposed targets

The numerical values below are initial engineering targets, not measured capabilities or statistical safety claims. Freeze or explicitly revise them after the pilot and before final testing.

| ID | Requirement | Verification |
| --- | --- | --- |
| NF-01 | Local route results appear within 500 ms at the 95th percentile on the designated phone | Repeated supported queries against the full bundled dataset |
| NF-02 | Recognition capture-to-display latency is at most 1 second at the 95th percentile within the supported envelope | Instrument the complete pipeline, not only inference |
| NF-03 | A stable correct result appears within 2 seconds of a readable target entering the usable view | Independently annotated held-out encounters; separately report results arriving too late to be useful |
| NF-04 | Ten minutes of continuous scanning produces no crash, unbounded queue, or memory growth trend | Sustained device run; report throttling and latency over time |
| NF-05 | Local lookup, OCR, and detection work without connectivity after installation | Airplane-mode first launch with bundled assets |
| NF-06 | Status is understandable without color | Text, icons, accessible names, sufficient contrast, and no color-only instructions |
| NF-07 | Text scaling and screen-reader navigation preserve manual functionality | Inspect large text and TalkBack flows; camera targeting is not claimed to be independently accessible to blind users |
| NF-08 | Interactive targets are at least 48 dp and status updates do not overwhelm assistive announcements | UI review and device accessibility testing |
| NF-09 | Permission denial and model failure leave manual planning operational | Denial, revocation, missing/corrupt model, and camera-unavailable scenarios |
| NF-10 | Ordinary use neither stores nor uploads camera frames | Review storage/network behavior and application permissions |

## 14. Dataset and experimental design

### Collection

Start with approximately 30–50 independent encounters to test readability and collection practicality. This is a pilot suggestion, not the training size needed for final performance. Expand according to route/sign diversity and validation learning curves. Include different vehicles, sessions, angles, distances, glare, partial occlusion, motion, unsupported signs, and distracting text. Record excluded conditions rather than silently dropping difficult examples.

Ground truth must come from independent verified observations, not the model prediction. Store exact visible text separately from route labels. Keep incidental faces and plates out of shared examples where unnecessary. Raw recordings and model weights remain outside ordinary Git history. Record collection permissions, provenance, and distribution rights before releasing any dataset.

### Split and training

Group samples by encounter, and where possible by vehicle and collection session. Augment only training samples; variants of one source never cross splits. Freeze the held-out test manifest before model selection. Report independent encounter counts as well as frame counts. Tune thresholds and temporal rules on validation data only.

Compare YOLOv8n, a specified SSD MobileNetV2 variant, and EfficientDet-Lite0 on one shared `route_sign` task. Confirm all training/export paths on a small sample first. Use comparable documented tuning budgets and identical downstream OCR, alias data, and compatibility rules. Record differing input sizes and acceleration so the comparison remains interpretable. Re-evaluate exported models on the designated Android phone.

An OCR-only aligned-crop baseline helps assess the added value of sign detection. It supplements the required three-detector comparison. Candidate substitutions require an explicit rationale and updated experiment plan before final testing.

### Metrics and decision units

| Metric | Definition or reporting requirement |
| --- | --- |
| Detection quality | Precision, recall, AP50, AP50–95, and results by condition/sign size |
| OCR quality | Character error rate and exact route-code accuracy; ground-truth crops versus detected crops |
| Route recognition | Exact route/pattern correctness and confusion matrix including unknown predictions |
| Wrong-green fraction | Incorrect green encounter decisions / encounters receiving any green decision; any incorrect green within an encounter counts as a failure |
| Matching recall | Eligible truly matching encounters with a correct green and no incorrect green / all eligible truly matching encounters |
| Abstention | Encounters with no definitive compatibility result / all evaluated encounters |
| Decision coverage | Encounters with a definitive compatibility result / all evaluated encounters |
| Latency | Median/p95 capture-to-display, time to first stable correct result, and proportion correct before the useful decision window ends |
| Device efficiency | Processed FPS, model size, peak memory, sustained latency and thermal behavior |
| User value | Correct selections and decision time with manual planning versus manual planning plus scanner |

Report unresolved-direction encounters separately; they must not inflate matching recall by being excluded without disclosure. A zero denominator produces “not estimable,” never 0% error. Report raw counts and uncertainty intervals for proportion metrics; repeated frames must not be treated as independent trials.

Proposed pilot acceptance objectives are at least 90% matching recall and at most 5% wrong-green fraction in the declared supported envelope. These are research targets, not permission to issue unreliable boarding instructions. A small test set cannot establish a low population error rate; report that limitation. Keep low-light and other out-of-envelope outcomes separate, including failures to abstain.

For usability, compare manual-only and scanner-assisted tasks using balanced task order. Determine participant count and recruitment before testing; do not claim population-wide improvement from a convenience sample.

## 15. Acceptance scenarios

| ID | Scenario | Required outcome |
| --- | --- | --- |
| AT-01 | Supported origin and downstream destination on one pattern | Correct direct result with boarding, direction, and drop-off |
| AT-02 | Destination precedes boarding in the pattern | No direct result unless a separately verified later occurrence permits it |
| AT-03 | Two patterns share a landmark | No accidental cross-pattern direct journey |
| AT-04 | Loop repeats a location | Correct occurrence is used; no invented wraparound |
| AT-05 | Unknown destination | Coverage message, not an assertion that no jeepney serves it |
| AT-06 | Clear supported sign and confirmed compatible direction | Green with explicit evidence label |
| AT-07 | Clear route but ambiguous direction | Gray, even if one possible direction serves the destination |
| AT-08 | Clear incompatible route and direction | Red labeled as no direct match |
| AT-09 | Unsupported sign resembles a known alias | Unknown/gray unless independent evidence resolves it |
| AT-10 | Two jeepneys overlap | No combined text or result; request a single target if association fails |
| AT-11 | Target leaves or user changes journey | Prior compatibility clears; late work cannot restore it |
| AT-12 | Camera denied, revoked, or unavailable | Manual planning remains available with journey selections retained |
| AT-13 | Network unavailable | Local planning/recognition work; basemap failure has a useful fallback |
| AT-14 | Missing model or malformed route update | Explain unavailable capability; retain valid manual data |
| AT-15 | Scanner used without a destination | Neutral route identification only |
| AT-16 | Wrong or conflicting headsign direction | Gray; do not silently prefer the user's desired direction |
| AT-17 | App pauses and resumes | Camera resources recover; recognition starts with fresh state |
| AT-18 | Same origin and destination | Explain no ride is needed rather than recommending a loop |

All deterministic route and state scenarios must pass. Model-dependent scenarios are evaluated across the held-out dataset and documented supported envelope, not a handpicked successful image.

## 16. Deliverables and academic alignment

Deliver an installable Android prototype, source code and build instructions, versioned verified route data, annotation/split manifests, three-model experiment configurations and results, failure analysis, and a demonstration script. Dataset distribution depends on permissions; reproducibility may use documented access instructions instead of public raw recordings.

The course rubric emphasizes actual comparison, data structures, results interpretation, and a working demonstration. Prepare preprocessing examples with inputs/outputs, algorithm explanations, metric definitions, model comparison charts, limitations, and individual contribution records. The final academic report follows the supplied limit of at most 15 pages, 12-point font, long bond paper. This PRD is a planning document and is not that submission report.

Record which demonstration segments use a live camera, prerecorded footage, or controlled signs. A replay is not evidence of live approaching-vehicle performance. Instructor acceptance of the detector-comparison interpretation remains an open item.

## 17. Milestones and release gates

| Milestone | Exit evidence |
| --- | --- |
| M1 — Feasibility | Target phone selected; readability pilot; supported envelope proposed; three export paths checked |
| M2 — Route foundation | 3–5 verified patterns; valid data bundle; manual planner and route details; deterministic route tests pass |
| M3 — Model comparison | Fixed splits; actual results for all three candidates; mobile validation; selected model justified |
| M4 — Integrated scanner | Live flow, target isolation, direction evidence, uncertainty, expiration, offline behavior, and fallback implemented |
| M5 — Evaluation | Held-out results, sustained phone test, usability pilot, accessibility review, failure analysis |
| M6 — Demonstration release | Reproducible build, documented artifacts, rubric materials, contribution records, known limitations |

No dates are fabricated: schedule backwards from the confirmed presentation deadline after staffing and training resources are known. Route verification and camera feasibility can proceed independently; final integration depends on both.

The academic prototype is complete only when P0 behavior, deterministic acceptance scenarios, actual three-model comparison, and honest evaluation are delivered. Passing documentation review alone does not satisfy completion. If recognition targets fail, publish the measurements and narrow the supported envelope; label the scanner experimental. Do not silently remove failures or claim general real-time reliability.

## 18. Risks and mitigations

| Risk | Impact | Response |
| --- | --- | --- |
| Signs readable only after vehicle passes | Scanner cannot help boarding decisions | Run readability pilot first; narrow to stationary/slow vehicles if necessary |
| Route data differs from operations | Correct OCR yields wrong guidance | Verify patterns, record provenance/dates, exclude uncertain variants |
| Direction assumption is mistaken | False matching result | Separate direction evidence, show user-confirmed status, abstain on conflict |
| Similar frames leak across splits | Inflated accuracy | Encounter/session grouping and split audit |
| Unknown route forced into known labels | False match | Unknown option, candidate margin, negative examples |
| Small test sample | Unstable error estimates | Report counts/intervals; expand independent encounters |
| Training/export incompatibility | Delayed comparison or unusable model | Test all candidates early; pin working versions |
| Phone throttling and stale work | Late or misleading overlays | Drop obsolete frames, expire results, sustained device tests |
| Multiple targets mix observations | Wrong vehicle receives a result | Single-target interaction and per-session evidence |
| Excessive scope | Incomplete working prototype | Protect P0 and defer excluded features |

## 19. Open decisions and ownership

Roles describe responsibilities, not assigned people. A small team may combine roles.

| Decision | Responsible role | Needed by |
| --- | --- | --- |
| Exact route patterns, boarding points, and verification contacts | Route/data lead | M1–M2 |
| Target phone(s), minimum Android version, and supported envelope | Android and CV leads | M1 |
| Exact SSD variant, checkpoints, OCR engine, export/runtime versions | CV lead | M1 |
| Map rendering/provider, attribution requirements, and network behavior | Android lead | M2 |
| Dataset size, split manifest, collection permissions, and retention | Data and evaluation leads | Before full collection |
| Thresholds, temporal window, freshness limit, and final performance targets | Evaluation and CV leads | Before held-out testing |
| Direction confirmation wording and field-test procedure | Product/evaluation lead | M4 |
| Route reverification policy and invalidation process | Route/data lead | M2 |
| Instructor acceptance, submission date, team assignments | Project lead | Before schedule commitment |
| Code/model/data licenses and any public artifact release | Project lead | Before distributing affected artifacts |
| Usability sample and task protocol | Evaluation lead | Before participant testing |

## 20. Change control and references

Changes to coverage, direction rules, model comparison, offline promises, or evaluation targets require an explicit PRD update with rationale. Preserve the original held-out results if a later version is tuned using those examples; establish a new test set for new claims.

Primary project references are the [research brief](docs/research-brief.md), its linked primary sources, the [original concept](docs/original-concept.md), and the supplied course rubric and lecture PDFs. Source documents are not redistributed here. External benchmark figures remain background evidence and must never be presented as ParaPo measurements.
