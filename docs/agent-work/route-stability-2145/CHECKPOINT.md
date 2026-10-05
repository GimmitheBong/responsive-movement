# Evening route stability — 2026-09-28

The user confirmed the turning and scene-click smoothing feels much smoother.
**Later user update:** the subsequent route corrections “worked really well”
in-game for the reported stalls/backtracking. This supersedes the pending
validation status from the original investigation; it confirms the user's tested cases,
not every possible blocked route. Production changes were confined to
`MovementInput` and `MovementPath`.

## Evidence and reported windows

Read-only analysis of `.runelite/responsive-movement/movement*.log` identified
session **1790595935156**, 2026-09-28 **21:45:35.156–22:01:24.104 (+10:00)**.
It spans `movement.previous.3.log`, `movement.previous.2.log`,
`movement.previous.log` and `movement.log` at investigation time. Sequence numbers
are stable identifiers if files subsequently rotate. `draw` is presentation,
`true` is the authoritative tile, and `native` is the interpolated native actor.

### 21:45:55 — stalled walk/run

The causal sequence begins shortly before the reported second:

- seq 929, 21:45:53.724: new WALK, click cycle 57212, while travelling west.
- seq 930, 21:45:53.744: new **east** destination `6976,6464` observed during
  the smoothing window; displayed point `6446,6464`.
- seq 931, 21:45:53.767: old authoritative westward step to `6464,6464`
  republishes the previous **west** destination `6336,6464`.
- seq 932, 21:45:53.784: `retargeted-walk` now uses that old west destination.
- seq 942, 21:45:53.985: display reaches `6336,6464` and stops.
- seq 961, 21:45:54.365: authority travels east to `6720,6464` and publishes
  the intended `6976,6464` destination. The plugin still retains its wrong
  westbound forecast as a pending reversal.
- seq 977, 21:45:54.684: forecast times out; recovery begins east, after roughly
  700 ms stationary. The display finally reaches `6976,6464` at seq 1044,
  21:45:56.025.

**Cause/fix:** The smoothing implementation reread and overwrote the pending
destination until release. Latch the first newly observed destination for each
click instead. Only another real click rearms observation. A repeated pending
same-target walk can carry its observed destination and still observe a new
target for that new click. Red-click native waypoint refinement remains the
controller's separate responsibility. No observation/response deadline is extended.

### 21:47:10–15 — retracing a diagonal

- seq 4846, 21:47:12.067: click cycle 61129 while the display is `6039,7447`
  on the checked diagonal `5952,7360` → `6080,7488`.
- seq 4847: native destination `6080,7232` is observed.
- seq 4849, 21:47:12.127: replan turns southwest, back toward `5952,7360`.
- seq 4861, 21:47:12.367: it turns southeast toward `6080,7232`.

**Cause/fix:** Endpoint-only connections on the occupied diagonal omit its near
side corner. A fresh walk replan may use that side corner only if both diagonals
and all four perimeter edges of the one-tile square are clear in both directions.
The checked square remains part of the leg's corridor for subsequent validation
and recovery; a blocked square retains the original checked-edge handling.

### About 21:48:00 — two unnecessary detours

1. seq 7254–7262, 21:48:00.223–.387: while travelling west along `y=7232`,
   click cycle 63537 requests `5824,6976`. At seq 7257 the display turns **east**
   toward `5952,7232`, then southwest along a new knight chord at seq 7261.
   The score favored stepping back to begin a chord instead of using the legal
   forward corner.
2. seq 7334–7382, 21:48:01.826–02.785: during a knight from `6080,6848` to
   `6208,7104`, click cycle 63617 requests `6208,6720`. The display retraces
   southwest through the old origin before turning southeast. The old origin
   and the nearer checked side anchor have equal max-axis travel scores.

**Fixes:** When a cardinal click remains ahead, prefer the legal forward corner
over an initial backstep if it costs at most one extra tile and its onward route
does not return through the occupied edge. Separately, break equal travel scores
using geometric distance rather than anchor enumeration order. This selects the
nearer knight-side connection. A fractional point already on a new route can
also omit its redundant construction-anchor connector, but a new knight join
must be inside the actually checked parallelogram, not merely its bounding box.

### 21:50:40 — knight retrace plus arrival timeout

The movement occurs at 21:50:38–39, just before the reported second:

- seq 15160, 21:50:38.355: click cycle 71443 on the knight
  `5952,6976` → `6080,6720`; new target `6080,7104`.
- seq 15163–15187, 21:50:38.406–.884: display retraces toward the old
  `5952,6976` origin, then turns northeast.
- seq 15183, 21:50:38.805: authority reaches `6080,6976`, absent from that
  detour's clicked path; it therefore does not confirm the expected route progress.
- seq 15201, 21:50:39.165: preview reaches `6080,7104`.
- seq 15208, 21:50:39.307: its response deadline expires and recovery turns south.
- seq 15213, 21:50:39.405: authority reaches `6080,7104`; the display turns
  north again and arrives at seq 15218, 21:50:39.505.

**Fix:** The same equal-score tie-break selects the nearer side anchor
`6080,6848`. Its northbound route includes the actual intermediate authority
`6080,6976`, so existing confirmation handling recognizes progress and the
premature recovery disappears in the replay. No longer timeout is introduced.

### 21:51:40–55 — repeated knight retrace

The active part of this window is 21:51:45–47:

- seq 18556–18559, 21:51:46.265–.323: click cycle 74839 on the knight
  `5824,7104` → `5952,7360`; new destination `5952,6976`.
- The display travels southwest through `5824,7104` before turning southeast
  at seq 18582, 21:51:46.786. It reaches the preview endpoint at seq 18595,
  21:51:47.046, confirmed at seq 18604, 21:51:47.224.

**Fix:** Same equal-score origin-versus-side-anchor issue. The shorter geometric
connection uses `5952,7232` and proceeds south instead of retracing the whole chord.

## Regression checks and review

- Initial `CapturedRouteStabilityTest`: eight tests, five failed on the baseline
  (input overwrite, diagonal connection, cardinal backstep, equal-score knight
  connections, and premature arrival recovery).
- Expanded checks include one-way blocked square edges, a partly blocked new
  knight, necessary obstacle backtracking, a corridor closing after construction,
  deadline retention, timeout recovery, frame-budget continuity and scene rebasing.
- The closing-corridor check exposed an overly permissive first attempt at new
  knight joins. The final join guard uses the proven parallelogram. The cardinal
  case uses its legal forward corner instead of assuming the whole new knight's
  bounding rectangle is clear.
- Test coordinates come from the capture, with explicitly synthetic open/blocked
  collision predicates and representative sub-tile progress. The trace has no
  collision maps and does not establish the exact availability of every tested
  connector in the original scene.
- Final `.\gradlew.bat build --offline --console=plain --no-daemon`: **BUILD
  SUCCESSFUL**, exit 0; fresh XML 2026-09-28T12:18:35–36Z reports **133 tests,
  zero failures/errors/skips**. All 10 captured-route/guard cases and the existing
  turning, input, object/herb, gait, corner, reversal and arbitrary-direction
  suites pass. Production blob diffs were reviewed and `git diff --check` passed.

Baseline production content is retained in local Git blobs without staging or
commits (the repository has no tracked HEAD baseline):

- MovementPath: `b9ef9686110cd2e9646fe236c6c87909596986f5`
- MovementInput: `8bf651d5dea822703cb4a4b538cd4a99ea3829ab`

Final production blobs:

- MovementPath: `8ab792f8f9f67435e3c74ede60e1940db3751f66`
- MovementInput: `ef62d14cb685a56e026e090e8734b9ee936b752f`

## User validation and follow-up

The user has since reported that these changes worked really well. The
following original retest instructions remain useful if a similar issue recurs:

Launch `.\gradlew.bat run`, using RuneLite's
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide. Keep the smoothing/turning settings that felt good and
repeat the short mixed-direction routes, including near-arrival knight reversals
and wall-adjacent cases. Record a new 30–60 second capture and report the time of
any remaining detour. Only the user validates actual gameplay and visual feel.
