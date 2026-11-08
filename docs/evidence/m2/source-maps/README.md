# Iloilo route map sources

The project lead supplied these two City Planning maps and approved using their Jaro Plaza–WVSU corridor for the ParaPo M2 route bundle on 2026-11-06. The footer on each image credits the Iloilo City Planning and Development Office as map preparer. The project lead reports retrieving the maps from official government websites.

- [Route 3 — Ungka to City Proper via CPU](route-03-iloilo-city-planning.png). The map distinguishes Depart (red) from Return (blue).
- [Route 10 — Buntatala/Tagbak Terminal to City Proper](route-10-iloilo-city-planning.png). The map distinguishes Depart (red) from Return (blue).

The bundled data selects only the Jaro Plaza (Cathedral area) ↔ WVSU Gate 2 corridor on Routes 3 and 10. The supplied maps give the directional street sequence; OpenStreetMap road nodes were checked on 2026-11-08 to trace the named streets through this corridor. Route 3 follows Eugenio Lopez Street and the Luna Street approach. Route 10 follows Commission Civil, Del Carmen, and the mapped Eugenio Lopez/Luna connection. Each direction remains a separate pattern; the Route 3 return is no longer routed through La Paz after the WVSU endpoint.

Some connectors cannot be fixed from the supplied map scale and are shown as dashed amber on the in-app map. These are (1) the exact Jaro Cathedral area boarding connector to the Route 3 road at Jalandoni/Plaza Rizal and the Route 3 return loop around Jaro Plaza, and (2) the Route 10 Jaro Plaza-to-Commission Civil approach and the short join from Commission Civil at `10.7201345, 122.5622104` to Del Carmen at `10.7188543, 122.5635263`. The route maps show the broad path and street names but not these exact turns/road centerlines. A georeferenced turn sequence or a more detailed route source is needed to resolve those sections. They must not be read as turn-by-turn guidance. The route extent remains only the Jaro Plaza–WVSU corridor; no schedules, frequency, exact curbside service, detours, or citywide coverage are claimed.

Road geometry and stop coordinates are attributed to [OpenStreetMap contributors](https://www.openstreetmap.org/copyright); OSM data is available under ODbL. The editable source is `data/iloilo-jaro-wvsu.json`, packaged as `app/src/main/assets/routes.bundle` (dataset `iloilo-jaro-wvsu-2026-11-08`). Installing a newer APK validates its packaged bundle and replaces an older local dataset without clearing app data; version/date checks reject rollback and preserve the last valid database on failure.

| Source image | SHA-256 |
| --- | --- |
| `route-03-iloilo-city-planning.png` | `EE67F4BDD488F54D04E348A165164A0A19951BF0BE0B2B7CFAF8DF0289552958` |
| `route-10-iloilo-city-planning.png` | `D52563019CC26362BAA913BECD36B20704A5152B8E436E96DEC229958F54FB74` |
