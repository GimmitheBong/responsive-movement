# Exact ground-item destination — 2026-10-04

Status: **implemented, regression-tested and user-confirmed good**.
The user confirms the preceding combat input/bow targeting changes are working
really well. README/current VALIDATION remain authoritative.
The user subsequently reports **"that change was good"** for this exact-destination
pickup correction. The later all-attack effect/gait work needs its own confirmation.

## Recordings and evidence

The three new sets are:

- `1791106332706`: **20:32:12.706–20:33:12.364 (+11:00)**.
- `1791106491512`: **20:34:51.512–20:35:21.629 (+11:00)**.
- `1791106630170`: **20:37:10.170–20:38:04.312 (+11:00)**, excluded from detailed
  analysis as requested. Only session-boundary metadata was used to identify it.

The relevant final pickups are in `movement.previous.log` at investigation time.
Their world-view is 0, base `(3152,3416)`, configured speed **5.6** (effective
**1.1**), smoothing **0**, Original player when aligned **off**. Both use item ID
`21326` and have `action=-1`, no retained combat target and no new combat pose.

### 20:33:06.893 — Take, click 2798; repeated at 07.091, click 2809

Origin local `(8768,6080)`, clicked scene `(66,48)`, native flag `(8512,6208)`.
The straight-first route's final diagonal is blocked by the adjacent solid cell;
an alternate diagonal-first route to the item is collision-clear.

The old generic `approach=true` search stops after a single checked diagonal at
`(8640,6208)`, adjacent to the real goal. The display reaches that point at
**07.493**, while authority remains at the origin. At **07.573**, authority reaches
`(8512,6208)` and withdraws the flag. `alignApproachAuthority` still sees the shortened
clicked goal `(8640,6208)`, so it builds a fresh speculative route back toward that
adjacent tile with no remaining visual leg. It holds there until **08.473**, then
recovers west to the real endpoint, arriving at **08.773**.

### 20:35:16.934 — Take, click 1291

Origin `(8896,6080)`, clicked scene `(66,45)`, old Walk flag `(8512,5952)`;
the newly published pickup flag is `(8512,5824)` at **16.937**.

Again the direct route's final diagonal is blocked, and the generic adjacent search
shortens the forecast to `(8640,5824)`. The first real run pair confirms that point
at **17.193**. The display arrives there at **17.532**, stops for approximately
**259 ms**, then resumes when the next native endpoint confirms `(8512,5824)` at
**17.791**. A checked alternate route can forecast the complete native destination
and avoid that intermediate idle publication.

## Narrow correction

`InteractionTarget.groundItem()` classifies the five ordinary ground-item options.
`MovementController.adjacentApproach()` now excludes those captures from adjacent
object-style route search in all three entry paths: idle, moving fractional handoff,
and queued continuation behind checked confirmed debt.

The exact goal is still the **newly observed native publication**, never the click's
identity coordinates. MovementPath still owns all route construction, checked legs,
fractional position, clock, bounded detour search, confirmation and recovery. No
MovementPath, combat, animation/model, camera, input timing or config change is needed.
Object/NPC and item/spell-targeted actions retain their existing policies. A blocked
exact ground-item tile does not justify a speculative trip to an adjacent substitute;
normal native authority/fallback remains available.

## Verification

`GroundItemDestinationTest` has **7 tests**; six fail before the correction and all
seven pass afterward:

1. The 20:33 pickup/repeat timeline, no adjacent hold or timeout return.
2. The 20:35 pickup timeline, no intermediate staging stop/restart.
3. All five ordinary item options versus unchanged object and targeted-input scope.
4. An inaccessible exact item tile cannot turn into an adjacent preview; subsequent
   checked authority still works.
5. Missing/unchanged native flags cannot be replaced by clicked coordinates.
6. Moving post-combat pickup uses the existing checked fractional join and exact goal.
7. Queued pickup preserves confirmed eastbound debt and forecasts the full onward goal.

Both capture replays run at **8.333/20/33.333 ms** with single position/turn budgets,
continuous checked travel and stable exact arrival. The fixture copies raw collision
flags for scene X=63..72/Y=43..51 from each reported click; the crops match. Unknown
cells are blocked. Logged local geometry/timings are retained while API-double world
coordinates use the fixture base. Native fractions between retained sample changes
are held, and no native renderer/hidden NPC state is simulated.

The focused pickup/object/continuity run passes. Full
`.\gradlew.bat build --offline --console=plain --no-daemon` passes **464 tests**,
zero failures/errors/skips, including all 457 prior regressions. Production changes
are limited to MovementController and InteractionTarget. Diffs were reviewed against
captured Git blobs; no staging/commits or game-input automation occurred.

## User check

Launch `.\gradlew.bat run` with GPU or 117 HD and the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide. Start with speed **5.6**, smoothing **0**, Original player
when aligned **off**, and tracing enabled. Repeat the final pickups after combat,
then idle/moving/repeated Take from nearby tiles around the same obstruction. Check
continuous travel to the item without an adjacent stop, timeout wait or return.
Turn tracing off to flush and report any remaining local timestamp/UTC offset.
Only the user performs gameplay and confirms visual behavior.
