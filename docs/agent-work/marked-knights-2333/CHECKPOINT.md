# Marked knight handoffs and visible-idle confirmation — 2026-10-08

Status: **implemented and regression-tested; awaiting in-game confirmation**.
The user confirms substantially improved circles and most reciprocal knights from
the preceding work, then requests this follow-up. README/current VALIDATION supersede
dated pending notes; no claim is made about every renderer/click pattern.

## Capture and marker audit

Managed trace **1791462766789**, **23:32:46.789–23:33:47.462 +11:00**, has **3,035 samples
and 81 Walk clicks**. Settings: **speed 5.4 / turn 30 / smoothing 0 / aligned native off**.
There are five substantial post-movement pauses (plus initial idle); both sides of
each two-second pause neighborhood were reviewed:

| Pause begins | Relevant preceding evidence |
| --- | --- |
| 23:32:59.062 | 58.483 uses a near-valley staging corner; actual authority is still the previous top endpoint. |
| 23:33:05.002 | 03.362 starts from a stopped preview toward old authority; 03.403 appends an obsolete return chord. 03.884 also shows the staging split. |
| 23:33:11.644 | 10.600 rebases the latest forecast through its newly reported origin, briefly reducing displayed Y before continuing by 10.701. |
| 23:33:19.964 | 18.903 retains the intermediate near-cardinal leg and then diagonal, despite a checked complete knight from the occupied endpoint. |
| 23:33:44.480 | 43.420 repeats the same split-handoff policy. |

Right-button presses/menu openings are not click records. The pauses mark the user's
observations; their exact right-click timing and unrecorded menu state are not synthesized.
Relevant frames have no primary action or effect. The actor's real route remains native.

## Shared corrections

### A complete checked knight from a nearby occupied endpoint

The prior whole-knight policy considered actual authority only. At several new clicks,
authority still names the previous top endpoint while display is within a quarter tile
of its current leg's valley endpoint. Time-based anchor selection prefers the middle
corner and produces a near-cardinal leg followed by a diagonal.

`retargetWalk` now also considers that nearby **forward occupied endpoint**, with active
run, both reversible knight orderings, the original queue/clock/deadlines, authority-gap
limits and a bounded reversible authority connection. It preserves the full straight-first
logical route and its checked whole visual chord. The old occupied start is deliberately
not preferred, retaining the previously confirmed no-backstep cardinal policy.

### A straight crossing between proven corridors

When display is a fraction outside the next corridor, the prior handoff finishes a
tiny return to the old construction origin. The adjacent old/new knight corridors can
instead share a collision-proven crossing along the straight line to the new goal.

`sharedCorridorCrossing` finds the convex region's entry with bounded click-time math.
The rounded crossing must be inside both regions. Convexity then proves the two complete
line subsegments. Each queued leg retains its own original corridor proof, which remains
rechecked until that subsegment finishes. Both-order collision, pacing, cancellation and
scene translation stay in MovementPath. Where overlap/geometry is not proven, the existing
checked alignment/corner remains. This is not an unchecked arbitrary straight-line route.

### The already-true-goal rule also applies to fresh idle Walks

The earlier confirmation fix covered moving replacements. A stopped preview can also
accept a new Walk toward old authority while display remains elsewhere. The ordinary
seed previously inherited confirmation of that goal, letting a delayed preceding click
append extra return debt behind it.

`pendingWalkConfirmation` factors the same rule for moving replacements, whole-knight
retargets and fresh eligible scene/minimap idle Walk starts. New legs remain pending;
matching authority confirms normally, stale progress updates authority within the existing
limits, and missing confirmation times out to checked recovery. It owns no extra input,
clock or prediction credit. Interaction/NPC seeds retain their existing evidence handling.

Production follow-up is confined to MovementPath and one controller call at eligible
idle Walk starts. Native primary actions/model providers, camera and input observation
retain their owners. No config key, synthetic game input or gameplay route setter is added.

## Verification and limits

`MarkedKnightHandoffTest` adds **eight tests**:

- The full 81-click publication/authority timeline at **8.333/20/33.333-ms** cadences,
  one movement/turn budget, both specific faulty windows, final arrival and holding
  every marked stop for its two-second neighborhood.
- Complete near-visible-endpoint knight construction; no tiny shared-origin rollback;
  visible-idle already-true return confirmation; scene/minimap parity; blocked alternate
  ordering; incoming corridor closure; eight rotated/reflected joins and scene rebasing.

The three initial focused checks fail before correction and pass after it. All previous
590 regressions remain green, including captured route/circle/reversal, ordinary confirmed
debt, interactions, combat, Follow, native models, camera, cleanup and managed diagnostics.
`.\gradlew.bat build --offline --console=plain --no-daemon` passes **598 tests**, zero
failures/ignored tests. No game was launched or input automated.

`marked-knights-2333.txt` retains all native authority/destination publications and 81
click times. Native fractions are held between retained events, with final native alignment
explicitly retained. The **X=44..50/Y=50..56** collision crop matches all 81 click contexts;
unknown space is blocked. Native models/pose/facing and translated world base are API
doubles. Tests do not execute the native renderer or replay unrecorded right-button events.

## User check

Launch `.\gradlew.bat run` with 117 HD or GPU, following
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).
Start with **5.4 / turn 30 / smoothing 0 / Original player when aligned off**. Repeat
the alternate knights around arrivals, including just before/after server updates,
visible-idle return clicks, and stops marked by right clicks. Check no brief rubber-band,
straight chord presentation where collision allows it, and stable latest-tile arrivals.
Include other directions, minimap, walking, a blocked corner, scene crossings and existing
interactions/combat/Follow. Only the user confirms visual feel; record a trace and report
any remaining local timestamp/UTC offset, then stop recording to flush.
