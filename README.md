# ParaPo

**Camera-assisted jeepney route identification and direct-journey matching for selected Iloilo City routes.**

ParaPo is a proposed Android application that helps commuters answer:

- Which jeepney routes can take me to my destination?
- Does the jeepney in front of me match that journey?

## Project status

**Research and planning.** This repository contains the concept and research brief. It does not yet contain a working Android application, trained models, collected dataset, or verified operational route database. Accuracy and real-time performance have not been established.

## Initial scope

- Manual origin and destination selection.
- Direct-journey lookup for 3–5 verified directional route patterns.
- Route details and a route map.
- Live camera sign detection, OCR, and matching to stored route information.
- Daylight scanning with one targeted vehicle at a time.
- Manual fallback when recognition fails.

The prototype will distinguish three outcomes using text as well as color:

| Result | Meaning |
| --- | --- |
| Green | Identified route and resolved direction match the selected journey. |
| Red | The identified route and direction do not provide a direct match within verified coverage. |
| Gray | Recognition, route coverage, or direction is uncertain. |

An unsupported route is uncertain, not automatically a non-match. Reading a route name alone does not establish travel direction.

Transfers, fares, arrival-time prediction, citywide coverage, accounts, and full offline maps are outside the initial scope.

## Proposed approach

Camera frame → sign detection → original-resolution sign crop → OCR → route candidates → directional journey check → labeled result.

The planned detector comparison is YOLOv8n, a precisely specified SSD MobileNetV2 variant, and EfficientDet-Lite0. All three must be tested on the same sign-detection task with fixed downstream OCR and route matching. Model selection remains subject to training, export, and target-phone feasibility tests.

The proposed Android stack is Kotlin, CameraX, on-device OCR, a compatible mobile inference runtime, and local route storage. Python supports dataset preparation and experiments. These are candidates, not installed dependencies.

Directional route patterns will use ordered stop occurrences. A graph representation must preserve the route pattern so shared landmarks do not create accidental transfers. The initial graph operation is route-constrained reachability; shortest-distance algorithms are only needed if that objective is added.

## Research question

How accurately and quickly can a smartphone identify a jeepney route from its signage and match it to a selected journey while withholding a decision when the evidence is uncertain?

Evaluate sign detection, OCR, route identification, incorrect green decisions, abstention rate, and end-to-end latency. Split data by independent vehicle encounters rather than randomly splitting adjacent video frames.

## Documents

- [Product requirements](docs/PRD.md): implementation scope, user flows, requirements, acceptance criteria, evaluation, and release gates.

- [Research brief](docs/research-brief.md): related work, feasibility, course-rubric alignment, dataset design, comparison protocol, and evaluation plan. Researched November 4, 2026.
- [Original concept](docs/original-concept.md): the initial proposal, preserved for reference. Where its scope is broader, this README and the research brief describe the recommended first version.

## Next steps

1. Collect a small sign-readability pilot on the intended Android phone.
2. Verify selected boarding points and directional route patterns.
3. Build manual direct-journey lookup and route details.
4. Prove training and mobile export for all three detector candidates.
5. Collect and label the experimental dataset; run the controlled comparison.
6. Integrate and evaluate the scanner, including uncertain and unsupported cases.

No build or test commands are available yet. Course materials, temporary research files, camera recordings, and model weights are not included.

## Limitations

This project is a proposed commuting aid, not validated boarding guidance. Route data may become outdated. Recognition must return an uncertain result when it cannot establish a route or direction. Field testing is required before claiming useful recognition of approaching vehicles.
