# GE counter route continuity — 2026-10-03

Status: **implemented, regression-tested and user-confirmed working in-game**.
README and the current-status section of VALIDATION remain authoritative.

## Evidence

Session `1790946856726`, 2026-10-02 **23:14:22.552–23:22:14.872 (+10:00)**,
spans `movement.previous.2.log`, `movement.previous.log` and `movement.log`.
There are **57 Bank clicks**, all to NPC definition 1633/index 25398, at world
`3163,3489,0`, local `7616,7360`, with world-view 0/base `3104,3432`.
This capture contains no Exchange clicks. Its effective movement multiplier is
1.1, with run enabled. Every click has a complete 104-by-104 collision snapshot;
the relevant captured region is identical across all 57.

The NPC stands on a movement-blocked tile of the bank island. Only its western
cardinal neighbour is traversable. Every published native destination is that
neighbour: world `3162,3489`, local `7488,7360`. Native flags can appear only with
the first server endpoint, or never appear for a one-update approach. The server
often stops before that adjacent flag, so simply forecasting all the way to the
flag would reintroduce overshoot.

Representative failures in the old trace:

- **23:21:44.870**, click 22443: starts `(8256,6720)` and previews diagonally toward
  `(7872,7104)`. Authority instead goes `(8000,6720)` → `(7744,6848)` →
  `(7488,7104)`. Native refinement constructs a forecast along Y=6976; at
  **23:21:46.074** its timeout queues a reverse connection to `(7744,6848)`.
- **23:20:23.054**, click 18342: starts `(7872,6976)` and eases north toward
  `(7872,7104)`. Authority instead goes west to `(7616,6976)`. The late flag
  therefore makes the displayed body retrace the startup segment before joining.
- **23:19:20.089**, click 15190: approach from `(8896,7104)` follows the correct
  row, but the old square staging limit stops at `(7872,7104)` around
  **23:19:22.432**. The final authority `(7616,7104)` arrives at **23:19:23.043**,
  producing an approximately 611 ms idle/restart gap.
- **23:14:22.552**, click 292: the short-start exception chooses a straight pair
  from `(7232,7104)`; authority actually completes a diagonal pair to
  `(7488,7360)`. Thus the narrow three-tile exception is also insufficient for
  this captured counter geometry.

## Correction and scope

For **Bank/Exchange**, a single-tile, movement-blocked NPC footprint with exactly
one unblocked cardinal side and no full projectile block on the target tile can
provide stronger route evidence than the old footprint-directed prefix.
`NpcApproach` captures those five collision cells once at the click. The existing
bounded reversible `MovementRoute` search finds the route to that accessible side.
No NPC definitions, world coordinates or per-origin learned routes are embedded
in production code.

Along that route, consider **whole two-step running endpoints**. The first endpoint
inside a rounded two-tile envelope matches the observed final stop in every
capture: `max(abs(dx),abs(dy)) <= 2` and `abs(dx)+abs(dy) <= 3`, in tiles relative
to the NPC. This excludes the square's `(2,2)` corners. Every intermediate
two-step endpoint on the planned prefix also matches the observed authority.
This is an empirical Bank-counter forecast, not proof of the server's underlying
distance formula or a universal NPC interaction radius. Other options, walking,
multi-tile NPCs, open/ambiguous sides and projectile-blocked targets retain their
existing policies. Already being in the supported envelope does not create a start.

`MovementPath.anticipateCounterRun` seeds the **exact ordered prefix**, not a new
straight route to its shortened endpoint. Queued continuations retain the existing
confirmed prefix and fractional position. `counterNativeGoal` keeps the adjacent
flag separate from `npcArrivalGoal`, the predicted stopping endpoint. Matching
late flags do not reconstruct the route or reset its clock. Full normal running
pace applies, replacing generic startup easing/staging stops only in this policy.

The shared 900 ms response/1.8 s chain bounds and speed-derived distance reserve
still apply. Matching authority can replenish; unchanged authority/native flags
cannot. Both directions of every predicted edge and both knight orderings remain
checked. A changed goal, target, run mode, action/effect, unsupported one-step or
off-route authority retires the assumption and reconciles through MovementPath.
Early flag withdrawal retires extension without returning to the old native
origin before a new endpoint. Collision invalidation, scene/lifecycle changes,
replacement and timeout keep the existing checked recovery/native fallback.

## Verification

Portable fixtures under `src/test/resources/com/responsivemovement/` retain:

- `ge-2314-collision.txt`: exact raw flags for scene X=50..71/Y=48..63. Tests mark
  all space outside that captured patch blocked, rather than assuming open ground.
- `ge-2314-approaches.txt`: all 57 click origins, authoritative endpoint sequences
  and native-flag publication/withdrawal ages in microseconds.

`GeApproachReplayTest` derives expected presentation geometry from those recorded
authoritative pairs using RuneLite collision semantics, independently of the new
NPC route choice. It asserts normal constant-speed positions through arrival at
8.333/20/33.333 ms frame cadences, for Bank and Exchange. **53 of 57 tests failed
before the correction; all 57 pass afterward** (342 replay combinations).
The original captures are Bank evidence; Exchange is a synthetic option-parity check.

Ten `CounterRunTest` cases cover translated/rotated/mirrored collision geometry,
different NPC IDs, unsupported options/footprints/sides, already-in-range and
unavailable/one-way paths, start gates, bounded timeout, early flag withdrawal,
target/run/action/destination invalidation, unexpected one-step authority,
knight-corridor proof and unfinished confirmed-prefix preservation.

`.\gradlew.bat build --offline --console=plain --no-daemon` passes **310 tests**,
zero failures/ignored tests, including all 243 earlier regressions.

The user subsequently confirmed that the correction works in-game for their
tested situations. The supplied log capture contains Bank clicks only; it does
not separately confirm every Exchange or collision layout.

## User validation

Launch `.\gradlew.bat run` with GPU or 117 HD and the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide. Repeat the reported Bank approaches at the same speed,
then Exchange and other sides of the GE counter. Check the side chosen, continuous
normal run pace, correct final tile and absence of an extra reversal/zig-zag or
stop/restart. Also check short/in-range clicks, moving interaction clicks, switching
back to Walk, and walking. Keep tracing enabled during reproduction and switch it
off to flush. Only the user performs gameplay and confirms visual behavior.
