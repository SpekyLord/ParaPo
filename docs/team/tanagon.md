# Tanagon — scanner testing and failures

## What you are helping finish

Check scanner interactions and record recognition failures accurately. Your photo contributions already helped; this is a separate task.

## Before starting

Get the normal APK, build ID, permitted sign photos with sample IDs, and an Android 14/API 34+ phone. No developer tools are needed. Alternatively pair with a Tecno user as observer/recorder, or review a current recording for specific visible issues. Label your work **device testing**, **observation**, or **recording review** and mark unseen cases NOT RUN.

## Steps

1. Open **Scan**, allow camera access when prompted, and aim the rectangle at one supplied photo. Scanning a photo or screen is a **controlled replay**, not a live vehicle test. No roadside collection is required.
2. Tap **Read aligned sign**. Record the sample ID and exact displayed ML Kit words, including **EMPTY** or any error, and the route result. A clear-looking sign may still be read incorrectly.
3. Tap **Resume camera / reselect** and check that the old result clears. Read a different sample; leave and reopen Scan, then background and resume the app. Record what happens.
4. Test denied camera permission when practical: deny it on request or use Android app settings, then check that manual planning remains accessible. Restore permission afterwards if needed.
5. If **Try checked sign detector** is available, try it on the supplied samples, select one outlined sign, and record missing or incorrect boxes and any direction prompt. If absent or unavailable, record that fact; don't treat it as a completed detector test.
6. Separate recognition limitations (wrong or empty text) from reproducible interaction bugs (for example, stale results after resuming). A frozen crop is a captured image and does not track a moving target. In live detector mode, an old selected-target result should clear when the target disappears or changes.

## Checklist

- [ ] Aligned read and exact output recorded by sample ID.
- [ ] Resume, navigation, background and permission fallback checked where possible.
- [ ] Optional detector status and small failure list recorded.

## Copyable test result

```text
Case ID / sample ID:
Work type (device testing / observation / recording review):
Build / device / Android version:
Steps:
Expected result:
Actual result (exact words, EMPTY or error):
Status (PASS / FAIL / BLOCKED / NOT RUN):
Evidence (screenshot or recording location):
```

For each failure also note the sample ID, what went wrong, and whether it looks like recognition or interaction behavior. Use PASS only when the expected behavior occurred; a useful FAIL is valid work.

## Send back and completion

Send the table, small failure list or truthful no-issue result, and a short explanation to Austria in the agreed group chat as text, screenshots or a document. Complete means attempted cases have honest outcomes and evidence, and inaccessible cases are marked BLOCKED or NOT RUN.

## Presentation

Explain detection, OCR and route matching in your own words. Be ready to answer:

1. What does a detector box do, and what does OCR do?
2. How does route matching use the read text?
3. How is a frozen crop different from a live selected target?
4. What did your controlled replay show, and what can't it prove?

## If blocked

Tell Austria the missing build, permission, sample or device. Pair on the Tecno or review a current recording; don't report unseen behavior as tested.
