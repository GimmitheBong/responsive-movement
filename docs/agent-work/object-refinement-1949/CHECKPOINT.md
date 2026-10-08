# Object native-refinement continuity and tick-aware smoothing — 2026-10-08

Status: **user-confirmed working well for the tested situations**; full build passes
**572 tests**. The user reports **"that change worked well"** for the refinement and
tick-aware smoothing follow-up. The preceding arrival-facing option is also confirmed.
README and the top of VALIDATION.md supersede this checkpoint if later reports differ.

## Captured failure

Managed `movement.log`, session **1791449319927**, final Use clicks **1042 / 1053 / 1061**,
at **19:49:00.502 / 00.702 / 00.841 (+11:00)**. Speed **6.0**, smoothing **60 ms**,
arrival-facing **on**, aligned native handoff **on**, run active, no primary action/effect.
Object 26815 is clicked at scene `(65,35)` with base `(3096,3464)`; the policy uses
ordinary action classification, not this ID or its coordinates.

The initial blocked anchor flag `(8384,4544)` produces a checked adjacent forecast.
At **00.825**, native destination refines to `(8384,4416)` and authority advances from
`(9280,4416)` to `(9024,4416)`. Display is about `(9126,4416)`, still owing confirmed
travel to the new authority. The next repeated click publishes the old anchor again.
`retargetApproachDestination` rejects a fractional join while a confirmed prefix is
unfinished, and its old fallback cannot find that blocked exact anchor. Cancellation
keeps confirmed movement but drops the forecast. Display reaches `(9024,4416)` at
**01.041**, publishes idle, and waits until authority `(8768,4416)` at **01.442**.
Later confirmed endpoints produce shorter idle/run pulses. Final authority is
`(8384,4416)` at **02.622**. This is a positional tail cancellation, not an arrival-facing,
combat action or model-builder failure.

## Correction

`MovementPath.retargetApproachDestination` can now build a checked forecast from current
authority and queue it behind a preserved unfinished confirmed prefix. It reuses
`replacement`/`queuePrediction`, with no second route engine or movement clock.
The exact visible fraction, confirmed chord/corner, response/chain deadlines, gap/queue
bounds and NPC footprint/reserve remain. A native refinement is not a new click/credit;
its occupied-edge fallback retains both deadlines and rejects an expired forecast too.
The controller passes the existing adjacent-search policy for object-style blocked
anchors; ordinary ground-item options still require the exact published goal. Failed,
expired or collision-invalid refinements retain checked recovery/native fallback.

The user also requests smoothing **0–300 ms**, with any nonzero wait ended by the next
game tick. `ResponsiveMovementPlugin.onGameTick` notifies the controller, which observes
native destination evidence and marks only MovementInput's release flag. Movement still
advances once during BeforeRender. An already latched flag survives stale republication;
later clicks reset release ownership. Native observation/pending-input expiry remains
100/900 ms. No input injection, sleep, primary-action/frame write or config-key rename.
The option description states the tick rule, and the default remains 50 ms.

## Verification and limits

Before implementation, both initial tests in `ApproachRefinementContinuityTest` fail.
Afterward all seven pass:

- Captured final click/refinement/authority timing at **8.333/20/33.333 ms** cadence,
  with movement/turn budgets, no 400-ms wait/retrace and final authoritative arrival.
- Preserved fractional confirmed prefix, continued checked tail and unchanged hard bound.
- Occupied confirmed knight retained before the replacement route.
- Exact-item rejection versus legal object-adjacent search for a blocked native goal.
- Repeated refinements retain the original response timeout.
- NPC reserve and scene translation retain the same path/clock/deadline.
- An expired refinement cannot rearm its response through an occupied-edge fallback.

The fixture copies original collision RLE columns **63..74**, reads only rows **30..40**,
and blocks unknown space. Native player fractions are held between selected logged
samples; clicked object geometry is not reconstructed because this trace had no
captured ObjectApproach footprint. It does not execute native rendering or establish
universal interaction reach. The actual reported final sequence is the capture replay;
other checks are explicitly synthetic API-double guards.

Five new MovementInput tests and four WalkClickController tests cover timer/tick release,
0–300-ms clamping, walking/running scene/minimap parity, native authority, stale flags,
per-click replacement, observation expiry, cleanup and native eligibility gates.
The previous range-clamp test is updated for 300 ms and idle parity includes that limit.

`.\gradlew.bat build --offline --console=plain --no-daemon` succeeds:
**572 tests, zero failures/ignored tests**, retaining all 556 preceding regressions.

## User confirmation

Launch `.\gradlew.bat run` following
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).
Repeat the final Use sequence/settings, nearby repeated/refined object approaches,
NPCs, exact pickups, doors, combat and scene changes. Check continuous motion without
the intermediate idle/run pulse, while arrival-facing still feels smooth. Test scene
and minimap smoothing 0/50/150/300 near tick boundaries, replacement clicks and missing
destinations. Only the user performs gameplay. Turn tracing off afterward and report
local timestamp/UTC offset for any remaining case.
