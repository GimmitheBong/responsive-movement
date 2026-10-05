# Bank-counter diagonal start boundary — 2026-10-04

Status: **implemented and regression-tested; awaiting in-game confirmation**.
The preceding GE route and bank-service Talk-to corrections remain user-confirmed
for their tested situations. README/current VALIDATION supersede dated notes.

## Capture

The report says just after 1:09 pm. The newest recorded session is
`1791079804024`, **13:10:04.024–13:10:34 (+11:00)** in `movement.log`, with
world-view 0/base `(3112,3424)` and banker definition 1633/index 25398 at world
`(3163,3489,0)`, local `(6592,8384)`.

Three repeated examples from world **(3161,3487)** / local **(6336,8128)**:

| Click | Action | Time (+11:00) | Old preview endpoint | Return begins |
| --- | --- | --- | --- | --- |
| 391 | Talk-to | 13:10:11.853 | `(6464,8384)` at 12.444 | 12.763 |
| 519 | Talk-to | 13:10:14.382 | `(6464,8384)` at 14.982 | 15.284 |
| 703 | Bank | 13:10:18.042 | `(6464,8384)` at 18.652 | 18.962 |

All three keep the native location and authoritative tile at `(6336,8128)` and
publish no destination. The preview's trip and timeout return are unsupported
displayed movement. The clicked tile has a `(2,2)` offset from the banker, outside
the old rounded `max axis<=2 && sum axes<=3` start check. That check was inferred
from prior **running endpoints**, not evidence that a fresh stationary corner
click must approach.

Nearby comparison clicks make the boundary distinction useful:

- Click 968, Bank at **13:10:23.322**, world `(3161,3488)` / offset `(2,1)`, stays
  stationary under the existing boundary.
- Click 1116, Bank at **13:10:26.262**, world `(3160,3487)` / offset `(3,2)`, really
  moves to world `(3162,3489)` / local `(6464,8384)`. First authority appears
  **242031 us** after observation. The native position at observation is fractional
  `(6296,8216)` after the preceding Walk; this case must retain normal run pacing.
- Click 1345, Bank at **13:10:30.823**, offset `(1,2)`, already retains position.

## Narrow correction

For fresh clicks on the existing qualifying **one-sided bank-service counter**,
`atCounterStartBoundary` conservatively includes the entire two-tile square. This
requires the same captured geometry/option evidence as `counterBank`:

- Bank/Exchange options, or Talk-to with native Bank/Exchange cache service actions;
- single-tile movement-blocked target, exactly one unblocked cardinal side and
  no full projectile block at the target tile.

It is not applied to ordinary NPC Talk-to/Trade, Attack, unrelated service options,
multi-tile/ambiguous geometry or an unproven bank-service Talk-to role. There is
no NPC-ID, name or world-tile table. Other NPC interaction distances are unchanged.

The controller clears that fresh observation and cancels only remaining speculative
movement through the established MovementPath cancellation/reconciliation. Any
unfinished confirmed prefix still completes on its checked route, without a snap.
A closer native flag alone does not require movement from this boundary. Actual
new authoritative steps still move through the ordinary shared queue and clock.
The trace records `at-counter-boundary` instead of accepting an unsupported start.

`counterRun` uses this new **start** gate. The existing rounded whole-run-pair
**arrival** rule is not expanded: earlier captures can run through a `(2,2)`
intermediate tile or endpoint and continue to their established stopping point.
Those confirmed route/pacing replays still pass. No prediction deadlines, distance
reserve, collision proof, body pose or input actions are changed by the gate.

## Verification

`banker-corner-1310.txt` retains the three no-movement click intervals, the positive
neighbouring approach's authority timing, and exact RLE collision columns from
click 391 (scene X=46..54, all Y cells). Tests treat all other columns as blocked.
World coordinates are translated to the API double's base while local geometry
is retained. Bank-service cache actions are explicit fixture evidence: the trace
records target identity/option, not its action array.

`BankCounterBoundaryTest` has eight tests:

1. Three Bank/Talk-to stationary replays at 8.333/20/33.333 ms cadences. This test
   fails before the correction as the display departs the unchanged authority.
2. The outside neighbouring Bank start retains its captured two-step normal pace.
3. Bank/Exchange/service Talk-to corners across rotated/translated counter geometry
   and different NPC IDs remain stationary without a per-tile lookup.
4. Ordinary Talk-to/Trade/Attack still approach at the same corner; missing Talk-to
   cache evidence, multi-tile targets and unrelated banker options cannot qualify.
5. A closer native flag alone cannot invent movement; subsequent real authority
   follows the checked pair at normal confirmed pace.
6. An unfinished confirmed prefix survives a fresh in-boundary click; its old
   speculative tail cannot carry on.
7. An ahead-of-authority Walk preview recovers within the existing checked edge
   instead of starting another bank trip.
8. A continuing counter run passes a `(2,2)` corner to its original pair-aligned
   goal without clipping the last pair or adding a stop.

The full `.\gradlew.bat build --offline --console=plain --no-daemon` passes
**430 tests**, zero failures/ignored tests. All previous 422 regressions remain
green, including earlier counter, ordinary-NPC, combat, object, model and camera
coverage. These checks do not execute the game's native renderer.

## User check

Launch `.\gradlew.bat run` with GPU or 117 HD, using the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide. Start with the captured movement speed **5.7**, smoothing
**0**, and Original player when aligned **off**. Repeat Bank and banker Talk-to
from world `(3161,3487)`, then Exchange at the corresponding clerk corner. The
already-in-range character should perform the interaction without an extra trip,
wait and return. Recheck `(3160,3487)`, a longer bank approach and a moving click,
plus ordinary NPC interaction continuity. Only the user performs gameplay; record
the check and disable tracing afterward to flush. This correction needs its own
confirmation.
