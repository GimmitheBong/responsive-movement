# Door/gate boundary continuity — 2026-10-05

Status: **implemented and regression-tested; awaiting user in-game confirmation**.
README and current VALIDATION remain authoritative. The user requested a small
smoothing correction to door/gate movement forced by native game updates.

## Newest recording

Session `1791164523185` spans **12:42:03.185–12:45:26.699 (+11:00)** in
`movement.previous.log` / `movement.log` at investigation. Gate Open/Close IDs are
1558/1559, view 0, base `(3168,3208)`, speed 5.5, smoothing 0, aligned native
handoff disabled. Relevant samples have no action/effect; ID/coordinates are
capture evidence only and are not embedded in production code.

- Close 4835 / 5655: flags name local `(3904,9408)`, authority stops at
  `(3904,9536)`. The old preview continues to the hinge tile and selects native
  rendering at **12:43:40.319 / 12:43:57.121**, jumping about 200/195 local units.
- Close 7844 at **12:44:39.519**: confirmed display is already beside the wall at
  Y=9536. Hidden native catch-up is still at Y=9483. The old handler manufactures
  another approach to 9408, interrupted by native fallback at 39.742.
- Close 9630 at **12:45:15.081**: display is around Y=9460 on the preceding
  northbound Walk, authority 9408. A hinge publication equal to that old tile
  starts a backward recovery; real authority advances to 9536 60 ms later.
- Close 9893 at **12:45:20.281**: old Walk flag to 9408 never changes, authority
  remains 9536. The old preview still finishes at 9408, then jumps 128 units to
  native position at 20.539.

These show unsupported presentation predictions and fallback handoffs, not
plugin-written server routes. The per-click collision snapshots prove open/closed
gate layouts, not the exact per-frame collision-change time or hidden game routing.

## Focused correction

`ObjectApproach.capture` now accepts ordinary Open/Close on a matching native
`WallObject` with shape 0–3 and the same clicked ID/view/plane. It captures a
singleton tile and reuses the existing perimeter staging/checked route. It reads
only that clicked tile once; no scene enumeration, object ID/name table or second
movement engine is added. Existing solid rectangular object footprints keep their
earlier handling. Unsupported walls/options and item/spell targeting remain native.

`MovementController` retires a stale preceding-Walk preview when its unchanged
hinge flag belongs to a wall whose perimeter already contains actual authority.
It uses `MovementPath.cancel`, preserving unfinished confirmed debt and fractional
position. Native hinge refinements also use the captured perimeter. A player
already on the wall's boundary tile can retain an in-flight crossing until its
actual endpoint arrives, instead of returning to its construction anchor.
Destinations outside the captured wall remain exact.

MovementPath, native model/pose/effect handling, facing, camera, input timing,
configuration and prediction limits are untouched. Existing collision checks
remain decisive: if the gate closes under an already occupied unconfirmed edge,
the plugin cannot invent a currently blocked connector to disguise it.

## Verification and limits

Four initial capture-based regressions failed against the original implementation.
The final `DoorGateContinuityTest` has nine tests covering the principal cases,
native refinement, conservative scope/collision checks and cleanup. Key cases run
at 8/20/33-ms cadences. The known open-gate cells for X=28..32/Y=71..77 match the
Close snapshots inspected; unknown cells remain blocked in the fixtures.

Wall-object type/shape and closing-time changes are explicit API doubles because
the recording does not retain those live details. Some input/native timings and
fractions are simplified from the captured windows; these are capture-based
regressions, not complete recorded-renderer replays. The final already-offset Walk
test permits at most a 24-unit native handoff when collision closes too early for
recovery, replacing the recorded whole-tile excursion/snap. No unchecked wall
crossing is allowed and no claim of eliminating every native forced-movement
handoff is made.

The focused door/object/pickup suites pass. Full
`.\gradlew.bat build --offline --console=plain --no-daemon` succeeds:
**507 tests, zero failures/errors/skips**, XML timestamps
2026-10-05T02:19:58.484Z–02:20:02.378Z. All 498 prior regressions remain green.
Tests use API doubles and do not run RuneScape's renderer or automate gameplay.

Repository has no tracked HEAD baseline. Original production Git blobs (no commit
or staging) are:

- MovementController: `04adbb2173dc47461d3dbaefecdbdee4fbedb4d7`
- ObjectApproach: `1466f924d098502825be2a25c53bf2a9cc236983`
- MovementPath, unchanged: `9e8f29ddc6f87c789cfe95ff1b6aae820fbc1f69`

Production diffs are confined to ObjectApproach and MovementController. Test API
doubles in NpcApproachControllerTest/ObjectApproachCaptureTest support the new
read-only wall lookup. No game run, capture edits, staging or commits occurred.

## User check

Launch `.\gradlew.bat run` with GPU or 117 HD and the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide. Start with speed 5.5, smoothing 0, Original player when
aligned off and tracing. Repeat Open/Walk/Close from both sides, especially Close
just before/after a native crossing update and from the longer northern approach.
Check holding at the true near-side stop, preserved confirmed crossing, and no
extra far-side trip or large snap-back. Include a different door, walking,
repeated options and Walk replacement. Disable tracing to flush; report any
remaining case's local timestamp/UTC offset. Only the user confirms gameplay.
