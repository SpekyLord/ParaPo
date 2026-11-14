# Garmino — DSA explanation and presentation

## What you are helping finish

Prepare the actual algorithm explanation, evidence-backed results and demo, not just slide decoration. Everyone will explain their own contribution; the group's photos are existing contributions.

## Before starting

Ask for the actual course rubric, instructions and deadline if available; current screenshots, evidence and build ID; and a short contribution summary from each person. A phone or Git is not required: use a shared document or plain text.

## Steps

1. Check the original course materials for individual assessment, any required programming contribution, report format, comparison requirements and deadline. Record missing items as questions; noncoding work is not guaranteed to satisfy individual grading. The [research brief](../research-brief.md) describes a three-algorithm comparison, 70/30 group/individual weighting and a maximum 15-page, 12-point, long-bond report, but confirm against the actual materials.
2. Use [JourneyPlanner.kt](../../app/src/main/java/com/parapo/prototype/routing/JourneyPlanner.kt) and [route data](../../data/iloilo-jaro-wvsu.json) to draw directional stop-occurrence nodes and forward adjacency edges. A node is a pattern ID plus stop sequence; shared place names do not add transfer edges between patterns.
3. Trace the real Route 3 Jaro-to-WVSU pattern: queue and visited start with its Jaro occurrence; remove it, follow its forward edge to the WVSU occurrence, add that to both; remove WVSU and finish. Both are visited, so the allowed later WVSU stop can match. Reverse travel uses its own directional pattern. Explain that the planner checks each active verified pattern and returns eligible direct journeys, not transfers.
4. Explain that [OrderedScanBaseline.kt](../../app/src/main/java/com/parapo/prototype/routing/OrderedScanBaseline.kt) independently scans ordered stops as a correctness baseline. For one BFS, time is O(V + E) and space O(V) for reachable occurrence nodes and edges. The complete query also scans patterns/stops and may run BFS for each eligible pattern; do not describe its total as one global BFS. Dijkstra and A* are not implemented.
5. Make a presentation outline, a results table citing actual evidence, limitations, and a demo script with manual planning as fallback. Use [scanner](../evidence/m4/scanner-2026-11-08.md) and [OCR](../evidence/m4/ocr-2026-11-09.md) notes; ask Austria for the local stability note with their sample and measurement limits. The old model-output mismatch was resolved; the three-detector comparison is still unfinished. Do not invent comparison results or present outside benchmarks as ParaPo results.
6. Collect each member's actual summary and speaking section. List unresolved course questions and dependencies rather than guessing dates or claiming work done.

## Checklist

- [ ] Course requirements checked or missing items listed.
- [ ] Graph diagram and real queue/visited trace prepared.
- [ ] Results, limitations, demo fallback and speaking sections drafted from evidence.

## Copyable results / presentation template

```text
Course source / unresolved question:
Graph pattern and nodes:
Queue and visited trace:
Evidence source / build / sample and measurement limits:
Actual result:
Limitation:
Demo step / manual fallback:
Member / actual contribution / speaking section:
```

## Send back and completion

Send Austria the DSA explanation and worked example, presentation/demo outline, evidence table, speaking sections and unresolved course questions in the agreed group chat as text or a document. Complete means each claim points to real evidence, each person has a section for their own work, and unknown requirements remain clearly open.

## Presentation

Explain the graph and your worked trace. Be ready to answer:

1. What does one occurrence node represent?
2. How do the queue and visited set change in your example?
3. Why don't shared landmarks automatically permit transfers?
4. What does the independent baseline check, and what are BFS's time and space costs?
5. Which results are still missing or limited?

## If blocked

Ask Austria or the group for accessible source excerpts, evidence or course materials. Draft from what is available and list missing facts as questions; do not invent results or a deadline.
