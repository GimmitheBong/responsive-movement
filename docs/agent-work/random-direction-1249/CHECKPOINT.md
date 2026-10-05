# Arbitrary-direction response checkpoint

Status: **scene-click retargeting confirmed working by the user**. Minimap
retargeting remains pending; the user explicitly deferred changes to it.
User explicitly requested Astra-only work and no further DeepSeek Flash use.
No worker ran on this task. No game input was automated and no commit was made.

## Actual evidence

- 2026-09-28 session `1790563797102` begins 12:49:57.102 +10:00, movement.log
  line 10016. At cycle 118423 the server confirms `(6592,7872)` while the visible
  player is midway on the knight chord from `(6464,7616)`. The new click toward
  `(6336,7488)` is observed at 118424 but logged `queued-continuation`; the
  visible model keeps travelling toward the obsolete endpoint.
- Later mixed-direction clicks repeatedly show queued continuations, including
  118472, 118520, 118613 and 118672. At 119075 the click toward `(6464,7744)`
  is followed by `waiting-for-confirmed-tail`, not a fresh visual replan.
- Session `1790563869690`, starting 12:51:09.690, shows the same pattern, e.g.
  cycles 122308, 122358, 122408 and 122556. These are arbitrary-direction
  changes, not only cardinal back-and-forth reversals.
- The trace has no collision map. Fixtures use recorded coordinates with
  explicitly synthetic open/blocked collision predicates, not a claimed scene
  reconstruction.

## Correction

Fresh yellow-click handling now uses `MovementPath.retargetWalk` for every
direction. It is not gated on the old path being speculative or on completing
the confirmed visual queue. It starts from the exact existing fractional x/y,
chooses a collision-proven connection through the occupied edge or fully checked
knight corridor, then follows the new checked route. A knight corridor can use
its existing straight/diagonal middle tiles as anchors, not only its endpoints.
This avoids requiring travel to the old final waypoint before responding.

Only a genuinely observed fresh walk destination invokes this policy. Native
interaction waypoint refinements and ordinary server reconciliation retain the
existing confirmed-prefix checks. The server position is never rewritten.
Object footprints, tree/herb approaches, native pose handling, gait presentation,
camera, config and recording implementation are unchanged.

Replans preserve the existing hard deadline, bound current/anchor distance from
authority, verify a short reversible route back to authority, keep the original
movement time budget and reject closed edges/corridors. If no bounded legal
connection exists, normal continuation/recovery still applies. A collision-safe
connector can require reaching a nearby tile before changing to the next leg;
this is not waiting for the entire obsolete visual queue or a server tick.

New decision marker: `startDecision=retargeted-walk`. Existing idle starts and
red-click approach markers remain applicable.

## Changed files and baseline

- src/main/java/com/responsivemovement/MovementPath.java
- src/main/java/com/responsivemovement/MovementController.java
- new src/test/java/com/responsivemovement/RandomDirectionTest.java
- docs/agent-work/random-direction-1249/{PLAN,CHECKPOINT}.md

Exact baseline content is retained in local Git blobs without staging/commits:
- MovementPath before: `0fc3b30f0c52c5725d0aa6d7092b734a6d9c9c14`
- MovementPath after: `b9ef9686110cd2e9646fe236c6c87909596986f5`
- MovementController before: `c41d6045288d831ef02d16e267e6aa1c59acc260`
- MovementController after: `f2196ba7e0d97a6e474e199b22c37638ae3f114c`
Review with `git diff <before> <after>`. Repository still has no HEAD and all
project files are untracked; normal git diff alone is not a review baseline.

## Verification

Initial desired-behavior tests ran against a retargetWalk wrapper using the old
handlers: 3 of 4 failed (recorded knight re-click, arbitrary-direction matrix,
fresh replacement of confirmed presentation). The closed-corridor test passed.

After implementation, all eight RandomDirectionTest methods pass, including:
- first-update response for the recorded confirmed knight fixture;
- 240 combinations: 12 active directions x 10 target directions x 2 path states;
- a 32-click mixed-direction sequence at 400 ms intervals with delayed 600 ms
  authority updates, asserting same-update response when already moving;
- an endpoint click with no spurious onward leg;
- hard-deadline retention and timeout recovery;
- rebase/recovery continuity and blocked corridor rejection;
- the old path remains untouched when merely constructing a candidate.

`.\gradlew.bat build --offline --console=plain --no-daemon` succeeded (5s).
Fresh XML 2026-09-28T05:25:18–19Z: **111 tests, zero failures/errors/skips**.
The existing 103 tests, including current object/herb and gait suites, pass.
Reviewed both production diffs against captured baseline blobs.

## User validation / resume

The user tested the latest change and reports it works for scene clicks. They
separately report minimap movement does not yet respond the same way, and
explicitly asked to defer minimap changes to a later task. Do not change minimap
handling or describe it as validated. Scene-click retargeting is user-confirmed;
the 12:49 recording and automated tests supplied supporting diagnostic evidence.
Red-click diagnosis remains separate.
