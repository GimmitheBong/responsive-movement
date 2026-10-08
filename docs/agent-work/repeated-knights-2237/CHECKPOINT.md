# Marked circles and repeated knights — 2026-10-08

Status: **user-confirmed improved for the tested situations, with remaining cases**.
The user subsequently reports circles are **"a lot better"** and most knight
back-and-forths work **"really well"**. Their separate 23:33 marked-pause report is
addressed by `marked-knights-2333/CHECKPOINT.md` and still needs its own confirmation.
The user reports the preceding circle change **"seems to be working well"** and asks
for this separate follow-up. README/current VALIDATION remain authoritative.

## Captures

- Second newest: **1791458898008**, **22:28:18.008–22:29:58.956 +11:00**,
  5,049 samples and 226 Walk clicks. Circles have several pauses; the final knight
  sequence begins at **22:29:47.599**. The marked circle segment around **22:29:06–08**
  supplies curve-facing evidence. Right-button menu-opening presses are not click
  records, so no exact right-click timing or hidden native menu state is invented.
- Newest: **1791459413609**, **22:36:53.609–22:37:23.975 +11:00**,
  1,520 samples and 27 Walk clicks. It contains repeated reciprocal/alternating knights.
- Both: speed **5.4**, Turning speed **30**, smoothing **0**, aligned native handoff off.
  Relevant movement has no primary action or active effect. The copied collision crop
  is unchanged across all 226 and 27 click contexts in these two sessions.

## Root causes and corrections

### Delayed preceding-click endpoint becomes invented return debt

At **22:29:53.356**, a fresh reversal predicts from `(5696,5824)` to the already-true
goal `(5568,5568)`. At **53.416**, the preceding click's delayed authority reaches
`(5696,5824)`. The stale guard tests the first logical cardinal step, not the complete
knight direction, so it misses that older endpoint. `accept` appends a return chord
behind the latest route. The same pattern occurs at **22:37:06.657**, **14.455** and
**19.876**; after clicks stop, display can run the unwanted trip and its correction.

`retainPendingReversal` now compares the whole logical origin-to-goal direction.
It retains the latest checked fraction/forecast through opposite stale progress while
updating real authority and clipping the existing gap. The original response and hard
chain bounds remain. A checked forward route step or actual construction-origin arrival
still enters normal confirmation—even if an obstacle requires moving away from the
geometric goal. No ordinary confirmed debt is removed without the existing fresh-click
prediction ownership. Missing confirmation still times out to checked recovery.

### Whole knight becomes an awkward staging split

Clicks near an old endpoint, e.g. **22:29:48.177** and **22:37:06.077**, can select a
construction anchor one tile beyond actual authority. The new logical route then uses
a near-cardinal connector and a diagonal instead of the native-origin whole knight.
This changes intermediate tile travel and apparent geometric speed even though the
max-axis clock/rate remains bounded. Later authority alignment can briefly retrace it.

`retargetCheckedKnight` retains a new authority-anchored whole chord only with both
reversible orderings clear and active run. An already-contained fraction joins it
directly. A fraction just outside may finish a proven occupied connector within
**one quarter tile** of authority, then traverse that same chord. Its short alignment
faces the onward chord rather than the preceding click. Other starts/geometry retain
the shared checked retarget policy. The exact fraction and movement clock are reused.

### A local curve makes facing overshoot and correct back

The circle snapshot near **22:29:06.356**, and the double sweep at **22:29:08.056 / .133**,
show brief facing reversals during one click. A cubic that rejoins its straight leg
can have a derivative direction beyond its terminal tangent before returning. Feeding
that directly to facing makes the body execute an avoidable corrective left/right sweep.

Body-facing intent now progresses monotonically between normalized incoming and terminal
tangents with smoothstep progress. The real derivative still supplies positional tangent
continuity. After the blend, the remaining leg uses its correct residual heading. The
existing single MovementFacing cap controls actual body turning. New clicks and genuinely
different checked travel headings can still legitimately change turn direction.

Production follow-up is confined to MovementPath. Native model/primary-action handling,
input observation, settings, camera and the server route keep their existing owners.

## Verification and evidence limits

`RepeatedWalkContinuityTest` adds **11 tests**:

- Both knight timelines at **8.333/20/33.333-ms** cadences with movement/turn budgets,
  no added return itinerary through the captured stale update and final authority arrival.
- Marked circle replay at the same cadences, including a no-corrective-sweep window.
- General knight reversal directions; near-origin whole-chord preservation; monotonic
  curve-heading bounds; timeout/recovery; genuine forward matching; rebase/collision;
  active-run/both-order isolation; genuine detour confirmation away from geometric goal.

The initial four positional checks fail before the positional fixes; the curve-heading
guard fails before monotonic facing. All pass after correction, alongside all previous
579 regressions. `.\gradlew.bat build --offline --console=plain --no-daemon` passes
**590 tests, zero failures/ignored tests**. The earlier circle replay remains covered.

Fixtures retain actual click/authority/destination timing, with native positions sampled
at retained publications. Native fractions between those events are held by API doubles.
The knight map copies **X=38..50/Y=38..48**, and the circle map **X=39..51/Y=38..49**;
unknown cells are blocked. Model/pose/native-facing state and translated world base are
explicit doubles. These checks do not execute native rendering, reproduce unrecorded
menu presses or establish every possible clicking pattern. No game was launched.

## User check

Launch `.\gradlew.bat run` with 117 HD or GPU using
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).
Use recorded **5.4 / turn 30 / smoothing 0 / Original player when aligned off** first:

1. Circle, pause at an odd turn, and repeat the right-click marking sequence. Check
   reduced corrective left/right sweeps while retaining responsive new directions.
2. Alternate the same knight, then add the second knight and vary click timing around
   server endpoints. Stop after the final click and check no extra out/back.
3. Check steady whole-chord travel without the staging split; include other knight
   directions, cardinal/diagonal reversals, walking, walls and scene/minimap input.
4. Recheck ordinary interactions, combat, Follow, scenes and plugin toggles. Only the
   user confirms visual behavior. Record a trace and report local timestamp/UTC offset
   of anything remaining; stop recording to flush.
