# Spam-click circle visual continuity — 2026-10-08

Status: **user-confirmed working well for the tested situations**. The user subsequently
reports **"that change seems to be working well"**, before requesting the separate
22:29/22:37 repeated-knight/facing follow-up. README/current VALIDATION supersede dated
notes. The preceding refinement/smoothing
and interaction arrival-facing changes remain user-confirmed for their tested situations.

## Evidence

Managed `movement.log`, session **1791456214521**, **21:43:34.521–21:43:55.575 +11:00**:
1,054 position samples, 48 scene Walk clicks; speed **5.4**, turn **25**, smoothing **0**.
Input begins at **21:43:40.816** and ends at **21:43:52.056**. The final authoritative
endpoint is local `(6592,7616)`. Relevant movement has no primary action or spot effect.

The sampled displayed travel direction changes abruptly at several short/fractional
joins, e.g. **21:43:45.555**, **46.137**, **47.393**, **48.653**, **51.314** and **52.094**.
Nine sampled changes exceed 35 degrees, with two near 72 degrees. The inspected sequence
has no large positional budget outlier. Small startup orientation/sample-time differences
do not establish an uncapped turn; trace timestamps are after preparation, not exact draw
boundaries. Logs cannot establish rendered bone blending or final camera perception.

## Correction

- `MovementPath.retargetWalk` scores eligible forward connectors using current travel
  tangent as well as travel length, within the existing one-tile allowance. All candidate
  route, occupied-edge/corridor, authority-gap and timeout checks remain decisive.
- Fresh fractional Walk joins and eligible forward Walk-authority joins retain that
  tangent for one preparation. A local cubic can blend its departure into the new leg
  only inside a proven reversible knight parallelogram or fully checked diagonal square.
  Its four controls/endpoint lie in the convex region and existing forecast gap. Control
  reach shrinks near boundaries; unsupported/blocked geometry uses the checked connector.
- The blend rejoins the same leg after about 120 ms of ordinary travel, rather than
  bowing the whole route. Exact max-axis arc-length integration uses derivative roots
  and polynomial integration, then bounded inversion. It spends the same frame budget
  and rate; leftover time continues normally. It owns no independent clock/deadline.
- Active geometry shifts on rebase; replacement/cancellation uses the existing checked
  corridor recovery. Native actions, primary frames/models, camera and input timing are
  retained. Ordinary NPC/combat/object positional policies are not assigned these Walk
  blends. No setting or persisted key is introduced.
- `MovementFacing` eases the final 32 orientation units under the existing capped budget,
  retaining fractional angles and held-target frame-splitting independence. This covers
  custom travel, interaction and combat facing through their existing target owners.
- Immutable route snapshots add eight `joins` coordinates per leg and pending flag 8192.
  Progress is not included in route comparison, so a curve does not force route output
  every frame. No worker reads live path/client state.

## Verification and limits

`CircleContinuityTest` adds seven methods. The full recorded click/authority/destination
timeline runs at **8.333/20/33.333-ms** cadences, enforcing one movement/turn budget and
eventual final arrival. The 20-ms aggregate sum of squared velocity changes falls from
**6.0925 to 4.83** (about **21%**); the comparison fails with the new positional policies
disabled. This is an aggregate metric, not a guarantee about every frame/turn or peak.

Additional guards exercise active fractional curves, their convex control bounds,
repeated-click horizons, closing collision, unproved knight orderings, curve pacing,
active-curve scene translation/recovery and eased facing settlement. Existing route,
reversal, confirmed-prefix, interaction, combat, Follow, model and lifecycle tests pass.
The old small-turn arrival test allows the new bounded eased settlement time.

`circle-2143.txt` retains all 48 click times and changes to actual authority/destination,
with native points at those events. Its collision crop copies click 315 columns
**49..60/Y=56..64**; unknown cells are blocked. Native fractions between retained events
are held by API doubles. Model/pose/facing state is synthetic, and the fixture base is
translated. This does not execute native rendering or reproduce every original frame.

`.\gradlew.bat build --offline --console=plain --no-daemon` passes **579 tests**, zero
failures/ignored tests, including all 572 earlier checks. No game input was automated.

## User check

Launch `.\gradlew.bat run` with 117 HD or GPU, following
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).
Start at the recorded **speed 5.4 / turn 25 / smoothing 0** and repeat the circle.
Include short/long circles, sharp reversals, stopping after the last click, mixed
scene/minimap input, nearby walls, and different turning speeds. Recheck arrival-facing,
object/NPC approaches, combat escape/gaze, explicit Follow, region crossings and plugin
disable/re-enable. Only the user establishes visual smoothness in-game. Record a trace,
stop it to flush, and report remaining symptoms with local timestamp/UTC offset.
