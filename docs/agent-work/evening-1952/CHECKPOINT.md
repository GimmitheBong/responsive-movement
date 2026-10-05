# Evening movement investigation checkpoint — 2026-09-22

**Historical checkpoint:** for current scene-click behavior and user-confirmed
route fixes, start with [README.md](../../../README.md) and
[VALIDATION.md](../../VALIDATION.md). The pending yellow-click status below
records the state at this earlier investigation, not the current plugin status.

Status: **Astra code review accepted; yellow-click redirect awaiting user in-game
validation**. Two 2026-09-27 captures contrast immediate object-interaction
previews with NPC approaches that wait for server movement; the reason for the
native movement delay and the 19:57:45 report remain unresolved.

Latest follow-up: the user confirmed both the 18:36 tree correction and the
broader object-use footprint extension for the herb-run loop. Planting/composting
had exposed an omitted object-use action type; the same footprint lookup now
handles those actions too. See [HERB-PATCHES.md](HERB-PATCHES.md) for all four
21:39–21:43 capture outcomes, the direction-dependent 21:40 exception, the
**103-test successful build**, and the user's confirmation.

Routing was verified using OpenCode message metadata for worker session
`ses_f37672003ffeJdA7ObXBeTSmUN`: provider `deepseek`, model `deepseek-flash`,
agent `flash-builder`. The final review-correction dispatch returned empty and
metadata reported `finish=length`. Astra explicitly took over the identified
runtime-risk corrections; no silent model substitution occurred.

Astra acceptance pass 1 (see `PLAN.md`) rejected the first patch. This checkpoint
supersedes it: the 600 ms red-click window is reverted, the misleading "client
tick" language is removed (client ticks are 20 ms; the 100 ms window was five
client ticks; a game tick is ~600 ms), and the demonstrated stale-preview
overshoot now has a bounded redirect fix. Red-click findings are reported as
unresolved.

## Session coverage

Session `1790070725637`, one sample per ~20 ms client cycle (`BeforeRender`):

| File | Lines | seq | Local time |
| --- | --- | --- | --- |
| `movement.previous.log` | 21036–24568 | 0–3532 | 19:52:05.637 – 19:53:16.38 |
| `movement.log` | 1–17205 | 3533–20737 | 19:53:16.4 – 19:59:00.6 |

Fields: `draw` = custom rendered point, `true` = server/authoritative tile,
`native` = `player.getLocalLocation()` interpolation, `spot` = active spot
animation, `inputPending`/`inputAgeUs` = click observation state.

## Timestamp outcomes

### 19:52:25 — "movement not fluid" — likely cause, pending in-game confirmation

`movement.previous.log`:
- seq 921 (19:52:24.043): perpendicular click `6848,7104` while running north;
  `startDecision=waiting-for-confirmed-tail`.
- seq 929 (19:52:24.198): `continuation-unavailable`; `true` already `6848,7104`.
- seq 965 (19:52:24.92): `draw=true=6848,7104`, `native=7047,7047`,
  `moving=false`, `nativeRender=false`.
- seq 1023 (19:52:26.081): native converges.

Two things are separated here. The **stale preview** continuing after the click
is the reported fluidity/route defect; the **endpoint stop while the hidden
native player catches up is expected** because the visible position is already at
the server-confirmed destination, so it is not claimed as the bug. Collision maps
are not in the trace, so no collision-free claim is made.

### 19:53:20 onwards — "red clicks not instant" — **UNRESOLVED**

Broad review across the window (62 `interaction=true` arm-frames):
- Red clicks at 19:53:10.439 (target 25398), 19:53:15.442, 19:53:18.003,
  19:53:29.859, 19:53:34.381, 19:53:46.062 and 19:53:51.343 (target 25399), plus
  19:54:20.007–19:54:26.345 (target 25399), all `clickAction=NPC_THIRD_OPTION`.
- Every one has `destination=-1,-1` and `startDecision=awaiting-destination`
  (then `destination-not-observed`): the native client never published an
  approach tile. Some occurred mid-run (4 samples show `moving=true`), others
  from idle. Astra spot-check of seq 3662–3666 shows confirmed/native movement
  occurring with no published destination; do not infer no approach occurred.
- The destination-based predictor had no observed approach tile. The
  earlier "600 ms window" theory is reverted as unevidenced. New `spot`,
  `inputPending`, `inputAgeUs` fields are retained so a future capture can show
  whether an approach destination or spot animation ever follows.
- A later banker/Bank-action capture with the new diagnostic fields is analyzed
  below. It confirms an approach destination can appear after the initial
  observation window, but does not establish that extending that window would
  make the approach start earlier. Unsupported interaction target routing needs
  a separate evidence-based design, not an assumed timeout change.

### 2026-09-27 18:04 — banker Bank action — captured delay, cause partly unresolved

User reports a one-tile ground click followed by a left-click Bank action on a
banker; the character did not move immediately. Local `+10:00` trace file
`C:\Users\AdamG\.runelite\responsive-movement\movement.log`, session
`1790496242718`, scene 82:

- seq 133, cycle 1043777, 18:04:05.397: ground WALK armed at
  `5824,8256`. seq 134, 18:04:05.421: native destination `5824,8384` was
  observed and the preview started (~24 ms after the armed sample). seq 135,
  18:04:05.438: rendered position began changing. The server confirmed the
  destination at seq 143, 18:04:05.598.
- After settling at `5824,8384`, seq 276, cycle 1043920, 18:04:08.259:
  `interaction=true`, `clickAction=NPC_THIRD_OPTION`, `clickTarget=25398`,
  `startDecision=awaiting-destination`, `inputPending=true`, `spot=false`,
  `action=-1`. The user's description identifies this action as Bank on a
  banker; the trace itself records only the action type and target ID. The
  same ID appears in the 2026-09-22 capture, but that alone does not identify
  every earlier target/action.
- seq 277–280: `destination=-1,-1`, position unchanged, pending age grows to
  81.5 ms. seq 281, 18:04:08.362 (~103 ms after click):
  `destination-not-observed`, `inputPending=false`. Through seq 292,
  18:04:08.582, `draw=true=native=5824,8384`; no action or spot effect is
  recorded. The click was eligible and armed; neither effect nor Ctrl/ineligible
  gating explains the absence of a preview in this sample.
- seq 293, 18:04:08.598 (~339 ms after click): the first published destination
  `6464,8384` appears together with authoritative progress to `6080,8384`;
  rendered movement starts at `5827,8384`, and the phase is `confirmed`, not
  `preview`. Native interpolation begins changing on seq 294. Later confirmed
  progress reaches `6336,8384` at seq 324, 18:04:09.261; the captured trace
  ends at seq 417, 18:04:11.098. There is no evidence of a predicted red-click
  start or a redirect for this click.

The ground click shows the destination-based prediction working promptly; for
Bank, the destination arrived only when server movement was already published,
well after the 100 ms initial observation expired. This explains why the plugin
did not show an early approach, and verifies the user's perceived pause. The
trace does **not** prove why the native client/server first showed movement
~339 ms after the click, nor that a longer observation window would remove
that pause. Do not infer a safe interaction approach tile solely from NPC
coordinates or change the deadline without further supported evidence.

### 2026-09-27 18:17–18:18 — object versus NPC follow-up

User reports object interactions (blue hover text, such as trees/doors) felt
instant, whereas NPC interactions (yellow hover text) varied; the final NPC
click had an obvious pause. Hover colors and object/NPC names are **not**
recorded. Session `1790497053287` runs from 18:17:33.287 (seq 0) through
18:18:10.163 (seq 1829) local `+10:00`, split between
`movement.previous.log` (seq 0–1099) and `movement.log` (seq 1100–1829)
in `C:\Users\AdamG\.runelite\responsive-movement`:

- Objects: seq 123, 18:17:35.734, `GAME_OBJECT_FIRST_OPTION` target 10832
  already has destination `6336,4288` and `phase=preview`, with visible
  position changing at seq 124, 18:17:35.754 (~20 ms). Seq 334,
  18:17:39.956, target 39546 starts a preview immediately; seq 335,
  18:17:39.976, moves visibly. Seq 773, 18:17:49.029, target 10822
  likewise starts in preview, moving at seq 774, 18:17:49.044 (~15 ms).
  These object routes may later be refined by a native destination (e.g.
  seq 795), after the early preview has already begun.
- NPC: all three recorded approaches are `NPC_FIRST_OPTION` target 17956,
  armed from settled idle with `destination=-1,-1`, `action=-1`, `spot=false`,
  `inputPending=true`. Seq 933, 18:17:52.227, expires at seq 938,
  18:17:52.327; first destination/authoritative movement occurs at seq 945,
  18:17:52.464 (~237 ms after click). Seq 1237, 18:17:58.307, expires
  at seq 1243, 18:17:58.426; first destination/authoritative movement at
  seq 1246, 18:17:58.486 (~179 ms). The first two may feel quicker but
  neither started as an unconfirmed preview.
- Final NPC click: seq 1581, 18:18:05.208, at `6976,6848`; observation
  expires at seq 1587, 18:18:05.324. Through seq 1603, 18:18:05.646,
  `draw=true=native=6976,6848`, `destination=-1,-1`, and no action/spot
  effect. At seq 1604, 18:18:05.669 (~461 ms after click), destination
  `6336,6848` first appears alongside the authoritative step to
  `6720,6848`; visible movement starts in `phase=confirmed`, not preview.

This corroborates the user's object-versus-NPC experience **in this capture**:
native object destinations were available in time to predict, but the NPC
approaches published destinations only with confirmed movement. It does not
show that all objects or all NPCs behave this way, explain varying native
response times, or provide a legal early NPC approach route. Merely extending
the 100 ms observation would not have started any of these three NPC previews
before their first confirmed step.

### 19:54:25 — "strange route/movement" — likely cause

`movement.log`:
- seq 6708 (19:54:20.007) → seq 7025 (19:54:26.345): idle red click.
- seq 7026 (19:54:26.361): WALK to `6720,7360` starts.
- seq 7064 (19:54:27.123): opposite click `7104,7232` observed.
- seq 7065–7096 `waiting-for-confirmed-tail`; seq 7098 (19:54:27.802)
  `continuation-unavailable`; the stale preview had already run west to
  `6799,7439` before reversing.
- seq 7136 (19:54:28.583): endpoint reached; native at `6923,7412` (expected
  catch-up, not claimed as a defect).

### ~19:56 — "possible corner-click delay" — likely cause

- seq 11676 (19:55:59.379): click `207108` → `awaiting-destination`.
- seq 11677 (19:55:59.399): `continuation-unavailable` (running preview could
  not accept the new route); click `207172` starts at seq 11741
  (19:56:00.686) once the confirmed tail drained.

### 19:57:45 — "some clicks not instant" — **inconclusive**

- seq 16872 (19:57:43.302) through seq 17011: completed `started-reversal`
  (`clickCycle=210374`), no pending observation.
- seq 17012 (19:57:46.102): click `212444` `awaiting-destination`;
  seq 17013 (19:57:46.119): `started` (~17 ms).
The nearest action started essentially immediately; nothing is tied to 19:57:45.
The former unresolved `seq 169?` reference is replaced by these exact seqs.

### 19:58:30 — "some clicks not instant" — likely cause

- seq 19157 (19:58:29.001): click `214589` → `6848,7232`, `started-reversal`.
- seq 19182 (19:58:29.502): opposite click `214614` → `7232,7488` observed.
- seq 19183–19188 `waiting-for-confirmed-tail`; seq 19189 (19:58:29.639)
  `continuation-unavailable`; direction changed on server confirmation.
- seq 19218 (19:58:30.223): click `214650` observed.

### Shared root cause

19:52:25, 19:54:25, ~19:56 and 19:58:30 share one mechanism: a click whose new
destination cannot use the exact-reversal or continuation fast path is deferred
while the unconfirmed preview continues (overshoot). This is the demonstrated
state/input defect; the fix is below. The red-click window remains unresolved.

## Changes in this correction cycle

Baseline (pre-change) snapshots:
`C:\Users\AdamG\AppData\Local\Temp\opencode\evening-1952-baseline\`
(`main__java__com__responsivemovement__*.java`, `test__java__com__responsivemovement__*.java`,
`docs__ARCHITECTURE.md`, `docs__VALIDATION.md`). Review with
`git diff --no-index <baseline> <working>`.

1. **Reverted** the 600 ms approach window and its test. `MovementInputTest.java`
   matches the pre-change baseline. `MovementInput.java` differs only by the
   `clickedNanos()` diagnostic accessor and comment.
2. `MovementPath.java` (+~70): new `anticipateRedirect(destination, run, now)`
   and helpers. Guards: unconfirmed speculative preview, active leg with no
   knight corridor, visible position on the occupied segment, destination not
   an endpoint/current tile. Tries the forward checked endpoint and, only when
   the reverse edge is collision-clear, the backward endpoint; chooses the
   shorter remaining travel. The connector is the occupied checked edge (or its
   checked reverse); the new route uses the existing checked `anticipate`
   (straight-first + bounded BFS), so no fractional diagonal is invented. The
   hard `chainDeadline`, gap (`forecastSteps()+1` tiles) and `MAX_QUEUE` bounds
   are preserved. `reversalPreview` is set so the existing stale-tick handling
   reconciles cleanly instead of building a zig-zag. Added
   `predictionDeadlineNanos()` for focused tests.
3. `MovementController.java` (~+18/−2 from baseline): `tryStart` attempts the
   redirect before `replacement(true)` and before `waiting-for-confirmed-tail`;
   `startDecision=redirected` records it. Interaction approaches are excluded.
   Trace diagnostics retained; the `spot` scan is now gated on
   `MovementTrace.recording()` so no extra spot-table pass happens while tracing
   is disabled.
4. `MovementTrace.java` (+~14): `canContinue`, `inputPending`, `inputAgeUs`,
   `spot` scalars and `recording()` accessor. No writer/bounding changes.
5. `src/test/java/com/responsivemovement/RedirectTest.java` (new, 11 tests):
   the cited non-collinear click (`anticipateReversal` declines) and the stale
   preview continuing away; exact-sub-tile no-snap redirect; reaching the new
    destination on authority; non-reversible occupied edge is rejected; no
   legal connector leaves the preview unchanged; rapid redirects keep the hard
   deadline; stale old-direction confirmation has no snap/collision; timeout
    returns to authority.
    Astra strengthened the recorded target assertion and added tests for a
    confirmed prefix, an occupied edge closing while the onward route remains
    open, and authority-gap rejection. Four of the eleven tests failed on the
    worker patch before hardening and all eleven passed afterward.
    Fixes: preserve confirmed prefixes even with a forecast queued behind them;
    check occupied connectors bidirectionally; bound visible/anchor-to-authority
    gap; compare actual route lengths including merged chords; omit zero-length
    connectors so confirmation cannot cause unnecessary connector traversal.
6. `docs/ARCHITECTURE.md`, `docs/VALIDATION.md`: document the redirect and the
   new scalars, and record resolved/pending outcomes. `PLAN.md` was read only.

`PlayerPresentation`, pose clocks, animation/model composition and
`MovementRoute`'s collision search are untouched. No new dependencies, config
keys/groups, or production config changes.

## Verification

The following worker results preceded Astra hardening. Final authoritative
build/test results are appended below after the final build.

- `.\gradlew.bat test --offline --console=plain --no-daemon --tests
  "com.responsivemovement.RedirectTest"` → `BUILD SUCCESSFUL in 7s`; 8 tests,
  0 failures.
- `.\gradlew.bat test --offline --console=plain --no-daemon` → `BUILD
  SUCCESSFUL in 6s`; 79 tests, 0 failures/0 errors (was 71; +8 redirect tests).
  Fresh XML covers CornerPrediction 13, DiagonalReversal 3, MovementInput 7,
  MovementPath 24, MovementTrace 3, OffsetReversal 3, Presentation 10,
  Redirect 8, ReversalReconciliation 8.
- `.\gradlew.bat build --offline --console=plain --no-daemon` → see completion
  report (run after this checkpoint).
- Note: an earlier first-cycle `gradlew test` returned a harness
  `ChildProcess.kill` with stale XML; retried with `--no-daemon` and re-inspected.

## Limitations / uncertainties

- The 2026-09-22 red-click trace shows no approach destination for any of its
  62 arm-frames. The 2026-09-27 banker follow-up shows a destination alongside
  the first authoritative step after ~339 ms; the later object/NPC comparison
  shows immediate object previews and three NPC destinations alongside first
  confirmed steps after ~179–461 ms. The diagnostics explain the missing early
  NPC previews, not the native movement delay or a fix for it.
- 19:57:45 remains inconclusive.
- The endpoint hold while the hidden native player catches up is expected when
  the visible position is already the confirmed destination and is not counted
  as the defect.
- Collision maps are not recorded, so no collision-free claim is made.
- The redirect's destination geometry in tests is synthetic around recorded
  coordinates, not a full scene reconstruction.
- The 2026-09-22 redirect has no in-game validation yet. The user separately
  confirmed the 2026-09-27 banker pause described above.

## Manual retest checklist (user)

1. Enable **Record movement trace**, test, then turn it off to flush.
2. Stale-preview redirect: while running, click behind/perpendicular to the
   current preview (not a straight reverse). Confirm the turn starts from the
   visible sub-tile position, does not continue to the old destination, and
   reaches the new one on server confirmation. `startDecision=redirected`
   identifies it.
3. Rapid alternating clicks: confirm the turn responds without the hard deadline
   being renewed indefinitely.
4. Red clicks: the 2026-09-27 banker and object/NPC captures demonstrate late
   NPC destinations versus prompt object destinations in these samples. Any
   further investigation should compare input, destination and first
   authoritative step; do not treat a later destination as proof that an
   earlier approach tile was available.
5. Existing regressions: straight/diagonal reversals, minimap repeats, corners,
   knight moves, blocked edges, and confirmed-corner queues.

## Next checkpoint

Await user testing of the bounded yellow-click redirect. For the red-click
report, inspect supported RuneLite evidence for an NPC interaction's actual
approach route and the timing of native destination publication versus first
server movement; do not assume the Bank target coordinate is its legal approach
tile. Preserve all baseline snapshots. No commits or in-game automation were
performed for the original correction cycle.

## Final Astra verification

- Targeted `RedirectTest`: 11 tests, four failures on the worker patch before
  hardening; all 11 pass afterward.
- `.\gradlew.bat build --offline --console=plain --no-daemon`:
  BUILD SUCCESSFUL in 6s, exit 0. Fresh XML 2026-09-22T10:48:07–08Z:
  **82 tests, zero failures/errors/skips**.
- `git diff --no-index --check` for MovementPath against the baseline found no
  whitespace errors (Git emitted only its LF/CRLF advisory).
- Runtime worker route verified from session metadata, not self-report.
- Code acceptance covers eligible unconfirmed yellow-click redirects and
  diagnostics only. It does not assert every timestamp's visual issue is fixed.
