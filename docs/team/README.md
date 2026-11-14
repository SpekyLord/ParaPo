# ParaPo team contribution plan

This is a starting split; swap tasks if that works better. Everyone has already helped by contributing jeepney photos. The assignments below are future work, not completed contributions. Each person should explain their own work in the presentation.

## Current status

The manual planner supports direct Route 3 and 10 journeys between Jaro Plaza and WVSU in both directions. The normal app requires Android 14/API 34 or newer. Its default scanner reads a frozen aligned crop; **Try checked sign detector** is experimental. The earlier model-output mismatch was resolved, but sign reading remains limited: a working camera does not prove reliable roadside recognition. The three-detector comparison is unfinished. Existing diagnostic observations are in the [scanner](../evidence/m4/scanner-2026-11-08.md) and [OCR](../evidence/m4/ocr-2026-11-09.md) evidence. The local stability note is not included in this repository snapshot.

| Member | Assignment | First action | Deliverable | Needs |
| --- | --- | --- | --- | --- |
| [Austria](austria.md) | Development, fixes, integration | Share current normal APK and build ID | Build, materials, fixes and targeted retest handoff | Reports from teammates |
| [Camariosa](camariosa.md) | Planner, maps, usability | Try Jaro Plaza → WVSU | Test table and journey explanation | Build/device or recording |
| [Tanagon](tanagon.md) | Scanner behavior and failures | Read one supplied sign | Test table, failure list and scanner explanation | Build/device or recording; sample IDs |
| [Garmino](garmino.md) | DSA, presentation and demo | Check available course materials | Worked graph trace, outline and open questions | Course materials and actual evidence; teammate summaries |
| [Santotome](santotome.md) | Independent photo and label review | Inspect a starter batch | Review table and sample-quality summary | Permitted photos, IDs, existing labels and boxes |

## Austria's handoff

Share the prepared **normal** demo APK `parapo-debug-0.1.0-c52ace1b62f4` and private team photo batch `parapo-review-v11-2026-11-15` (12 photos, with existing labels and boxes separately); current screenshots, recordings and evidence; and a place to send findings (for example, the group chat). Agree on access to the Tecno or a current recording. Return an updated build and ID when fixes need retesting. Don't add raw photos to Git.

Anyone can send plain text, screenshots, or a document in the agreed group chat; Markdown or Git is optional. Austria can add actual submissions to the repository while crediting their real authors. Record failed tests and no-issue results honestly. A failed test with useful evidence is a contribution.

## Contribution log

Add entries only for work actually done; link or describe evidence without publishing private photos.

| Member | Actual work | Date | Evidence/location | Status |
| --- | --- | --- | --- | --- |
|  |  |  |  |  |

## Questions to settle

- What is the actual presentation/submission deadline? Agree on a team deadline after checking.
- Who can access the Tecno or another compatible phone, and who needs pairing or a current recording?
- What do the actual rubric and instructor require for individual assessment, programming contributions, report format and algorithm comparison? The research brief describes group/individual weighting, a report limit and an actual three-algorithm comparison, but check the original materials and interpretation. Noncoding work may not satisfy every individual requirement.
