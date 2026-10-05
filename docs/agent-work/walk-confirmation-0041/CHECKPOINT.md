# Fresh Walk confirmation and queued return debt — 2026-10-05

Status: **implemented, regression-tested and user-confirmed working well**.
The user confirms the preceding all-attack effect/gait correction is working well;
those changes remain intact. README/current VALIDATION remain authoritative.
The user subsequently reports **"that change works well"** for this fresh-Walk
confirmation/oscillation correction. This confirms their tested situations; the
separate delayed-click symptom was not clearly reproduced and remains unconfirmed.

## Last two recordings

- `1791121217442`, **00:40:17.442–00:41:21.166 (+11:00)**, 79 clicks / 3,188 samples.
- `1791121454072`, **00:44:14.072–00:46:27.928 (+11:00)**, 127 clicks / 6,693 samples.

The final yellow-click windows are ordinary movement: no action, spot effect or
combat owner. Speed **5.5** (effective **1.1**), smoothing **0**, Original player
when aligned **off**. Collision snapshots and full route/queue records are available.

### 00:41:16 — final click 3023

The short sequence starts at local `(6592,6720)`, Walks east to `(6848,6720)`, then
west toward `(6336,6720)`, then east again at **16.327**. Display is `(6564,6720)`,
while authority already equals the new east goal `(6848,6720)`. Retarget construction
uses `(6464,6720)` as its origin and confirms every logical point immediately:
`confirmedClickIndex=3`, `stateFlags=296`, so the new queue is not speculative.

At **16.386**, an older westward server endpoint `(6592,6720)` is appended behind
the newly built east itinerary. At **16.989**, authority returns to `(6848,6720)`
and another forward queue is appended. Display traverses the extra out/back/out
itinerary and finishes at **18.168**, with no new click after 3023.

### 00:46:22 — final click 6569

The second sequence makes the same mistake mirrored. At **22.945**, display is
`(7283,6720)` and the new west goal `(7104,6720)` already equals authority. It builds
from `(7360,6720)` with every logical point marked confirmed. The delayed old east
endpoint `(7360,6720)` at **23.028** appends a return, and the new west endpoint
`(7104,6720)` at **23.626** appends another pair. The final redundant trip ends
at **24.567** after input has stopped.

## Root cause and retained feature

Fresh-click replacement correctly preserves the visible fraction and collision-
checked occupied edge/corridor. But a goal already on the old authoritative tile
is not confirmation of that fresh visual itinerary. `walkFromAnchor` inherited the
old confirmation indices, made the forecast fully confirmed, and therefore bypassed
the existing pending-reversal stale-tick handling when the older update arrived.

Only MovementPath changes. A non-empty fresh route back toward an already-true goal
now stays speculative/reversal-pending until genuine matching evidence. A singleton
logical goal can also represent a checked fractional endpoint connector, including
a knight: it gets the same pending treatment without assuming an onward route.
Old endpoints update real authority but do not append obsolete debt or renew the
original deadline/gap. Matching new authority, expiry, collision and ordinary real
confirmed queues retain their handling. No new clock, collision shortcut, coordinate/
direction table, input automation or presentation rollback is introduced.

The existing arbitrary-direction responsiveness, combat effect carry, native primary
animation/frame preservation and displayed-gait/native-builder behavior are kept.
MovementController/PlayerPresentation/NativeModelObject are unchanged by this fix.

## Verification and scope

`WalkConfirmationRegressionTest` has **7 tests**. Both captured replays and the
confirmation guard fail before the correction. The replays run at
**8.333/20/33.333 ms**, asserting a single movement/turn budget, no old-tick detour
after the final click and stable arrival. Five guards cover straight directions,
tile diagonals, a one-step endpoint, knight connectors, finite timeout, repeated
replan horizon retention, ordinary real authority debt and closed connectors.

`yellow-0041-0046.txt` copies actual collision crops X=45..62/Y=50..54 from the
first click in each ending window. Local geometry and recorded click/flag/authority
timing are retained; outside space is blocked. Native fractions between retained
changes are held by API doubles. These checks do not execute the native renderer.

Full `.\gradlew.bat build --offline --console=plain --no-daemon` passes **498 tests**,
zero failures/errors/skips, retaining all 491 prior regressions including all-attack
effects/gait, model lifetime, object/NPC and camera suites. Source diffs are reviewed
against captured Git blobs. No staging/commits or game input occurred.

No first displayed Walk departure over **100 ms** before the next click was found
in these two recordings. That is not a guarantee of instant response or a diagnosis
of an unrecorded delay. A legal fractional connector and render/native-publication
timing still matter. A further delay report should include its local timestamp.

## User check

Launch `.\gradlew.bat run` with GPU or 117 HD and the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide. Start at speed **5.5**, smoothing **0**, Original player
when aligned **off**, with tracing enabled. Repeat the short alternating yellow
clicks and stop at the final reversal; check a single direct arrival/hold without
an extra return trip. Include single/diagonal/knight near-arrivals, mixed/minimap
clicks, idle/long runs and the confirmed melee/range/magic effect/gait behavior.
Disable tracing to flush and report any remaining local timestamp/UTC offset.
Only the user performs gameplay and confirms visual behavior.
