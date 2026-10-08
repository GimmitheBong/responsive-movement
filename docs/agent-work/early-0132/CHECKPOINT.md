# Object-boundary and delayed-action Walk continuity — 2026-10-08

Status: **implemented and regression-tested; awaiting the user's in-game confirmation**.
README/current VALIDATION remain authoritative. Existing attribution/documentation edits
were present before this task and preserved.

## Trace evidence

Read-only inspection of the managed `movement.log` found session `1791383551670`,
**01:32:31.670–01:33:37.056 (+11:00)**, 30 clicks and 3,271 position samples.
Settings: speed **5.8** (effective **1.2**), smoothing **60 ms**, aligned native player
enabled, adaptive camera enabled. Renderer identity is not retained by this trace.
All clicks use scene input; minimap parity below is API-double coverage.

### Wrong-side tree previews

Click **371**, Chop down at **01:32:39.032**, starts at `(5952,6080)` with native flag
`(5312,5568)` inside the captured 3x3 footprint `(5312,5568)`–`(5568,5824)`.
The geometric perimeter selector chooses `(5696,5824)`. The checked visual route is
`(5824,6080)` → `(5696,5952)` → `(5696,5824)`. At **39.641**, display Y is **5918**,
below the eventual native stopping Y **5952**. At **39.659**, authority first advances
to `(5696,6080)` and publishes the real approach `(5568,5952)`; the display reverses
north, then west. Final authority arrives at **40.258**.

Click **1279**, at **01:32:56.972**, repeats the unnecessary southern travel from
`(6080,6080)`. First authority/refinement arrives **864012 us** after the click and
final authority **1284413 us** afterward. Other tree clicks corroborate early anchor
flags later being replaced by exact native perimeter destinations. These are checked
but unsuitable visual forecasts, not plugin-written server routes.

### Interrupted Walk continuation

Click **1388**, Walk at **01:32:59.136**, observes `(6336,6080)` at age **19720 us**
and starts after its smoothing window. Its full straight-first route contains six
steps; the bounded initial forecast ends at `(6080,5952)`.

At age **342358 us** (**59.479**), native action **10071** appears and the flag disappears.
The old controller applies `replacement(false)`, changing flags **265 → 261**:
route agreement is lost, but the existing bounded preview continues. Matching authority
at ages **923459 / 1539775 us** confirms `(5824,5952)` and `(6080,5952)` without
replenishing the retired forecast. The display stops from **01:33:00.274** until
**01:33:01.280** (about **1006 ms**), then follows final authority `(6336,6080)`.
The action has ended by the first matching step, but its retirement decision persists.

The trace proves action/flag/authority timing, not why the native client published
that delayed action. The adjacent Chop down activity provides context; no action-ID
or tree-ID whitelist is added to production code.

## Shared policies corrected

- `MovementRoute` factors its existing search into a shared reached-predicate routine
  and adds rectangular cardinal-boundary search. The same **64-step / 2048-visited**
  limits and reversible edges apply; cardinal-first tie ordering is retained.
- `ObjectApproach` uses that search for captured solid rectangular footprints when
  the native publication lies inside them. Already being on the boundary stays put;
  unreachable boundaries return no forecast. Walls retain their established policy;
  native destinations outside the footprint stay exact. Staging never proves allowed
  interaction sides/reach. MovementPath still constructs and owns every itinerary.
- `MovementController.walkInput` records an eligible explicit Walk's input ownership.
  A delayed non-location action cannot retire its observation/smoothing or checked
  continuation. The same ownership selects capped travel facing through that action.
  Missing/new destination evidence, forecast deadlines/distance, reverse/corridor
  checks and native authority remain decisive. Effects retain their existing gates.
  World replacement, scene/native-location/renderer/lifecycle changes, disabled starts
  and completed native/displayed arrival clear ownership. Fresh Walk during an already
  active ordinary action retains its conservative gate. Existing combat escape/effect
  handling is retained.

Production changes are confined to MovementController, MovementRoute and ObjectApproach.
No movement clock, native primary animation/frame, model-provider, camera or config edit
is introduced. No game input was automated; the game was not launched.

## Verification and evidence limits

Both initial captured regression methods fail before the production correction.
`EarlyMorningContinuityTest` has seven methods: two northern tree timelines and the
interrupted Walk replay at **8333/20000/33333-us** cadences, plus observation/smoothing,
scene/minimap/walk/run parity, missing/unchanged publications, finite repeated-click
recovery, input/effect/native-location/scene/disabled-start invalidation and existing
ordinary-action gating. Assertions cover monotonic approach geometry and a single
movement budget; API doubles reject primary animation/frame writes.

`ObjectBoundaryRouteTest` has five methods covering cardinal-prefix ties, already
adjacent starts, inaccessible/blocked-side and one-way routes, step/visited bounds,
native destination isolation and different approach sides. These guards use synthetic
geometry, separate from the capture's actual collision crop.

`early-0132-map.txt` copies click 371's raw flags for **X=40..51/Y=40..51**. Unknown
cells are blocked. Replay local geometry, footprint and event timings are retained;
fixture world coordinates are translated. Native fractions are held between retained
samples, and frame-zero availability/scene objects use API doubles. These tests do
not run the native renderer or establish every object side/server route.

Focused replay/object/door/midnight checks pass. Full
`.\gradlew.bat build --offline --console=plain --no-daemon` succeeds:
**548 tests, zero failures/errors/skips**, retaining all 536 earlier regressions.
Fresh XML timestamps: **2026-10-07T14:50:03.240Z–14:50:07Z**.
`git diff --check` passes. No commits or staging occurred.

## User check

Launch `.\gradlew.bat run` with 117 HD or GPU, using the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide. Start at **5.8**, smoothing **60**, aligned native player on,
and record a short trace:

1. Repeat the same tree approaches from the north/northeast and offsets; check no
   extra southern leg followed by a reverse, and normal arrival at the native stop.
2. Walk away just before an interaction action arrives, including the longer eastward
   run. Check no intermediate idle/stop and no facing turn back toward the old action.
3. Include scene/minimap Walk, walking, different solid rectangular objects, item use,
   repeated clicks, combat escape and door approaches.

Turn tracing off to flush. Report any remaining case's local timestamp/UTC offset,
renderer and visual symptom. Only the user confirms in-game behavior.
