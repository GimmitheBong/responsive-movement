# Arbitrary-direction response — 2026-09-28

## Corrected user requirement

Responsiveness applies to new walk clicks in arbitrary directions while already
moving, not just straight reversals. Investigate the 12:49 capture and implement
the smallest coherent correction to stale-route deferral. Astra owns this
behavioral contract and acceptance; one flash-builder owns investigation,
implementation, debugging and focused verification.

## Evidence and baseline

Project: C:\Projects\ResponsiveMovementPlugin. Git still has no HEAD and every
project file is untracked. Capture an exact baseline of every file before editing
with read/apply_patch under C:\Users\AdamG\AppData\Local\Temp\opencode\random-direction-1249-baseline.
Do not overwrite earlier baselines or use them as today's source.

Newer user work EXISTS: object-footprint and herb-patch approach behavior, and
103-test baseline per docs/agent-work/evening-1952/CHECKPOINT.md. Preserve it.
The old conversation's 82-test snapshot is obsolete.

Traces in C:\Users\AdamG\.runelite\responsive-movement\movement.log:
- Session 1790563797102 starts 2026-09-28 12:49:57.102 +10:00, line 10016.
- Adjacent session 1790563869690 starts 12:51:09.690, line 11026.
Inspect both for context and report which supports each finding. Do not silently
attribute other days' recordings to this request.

Current MovementPath still restricts anticipateRedirect to speculative, non-chord
legs and excludes unfinished confirmed prefixes; exact reversals have their own
geometry restrictions. MovementController.tryStart chains these narrow paths
before queued continuation. Inspect actual failed decisions before selecting fixes.

## Design and contracts

- A genuinely new observed yellow destination should promptly replace an obsolete
  remaining visual itinerary, even if it is non-collinear, the old movement was
  confirmed, or the visible point is within a valid knight corridor. Do not
  dismiss this request as a documented queue trade-off.
- This explicitly relaxes the previous *fresh user click* policy of finishing an
  entire confirmed presentation queue before responding. It does NOT permit
  dropping confirmed corners during ordinary server reconciliation, timeout, or
  without a new click. Actual server routes/actor path state remain read-only.
- Start from the exact visible sub-tile position. Choose a collision-proven
  connector using the occupied edge/corridor and checked endpoint anchors, then
  the new collision-checked route. Preserve continuity, check actual connector
  geometry and reverse/recovery validity, and measure distances rather than leg
  counts. Never create a direct unchecked fractional shortcut across a wall.
- Preserve finite hard deadline, response deadline, gap/queue/search bounds and
  eventual reconciliation. Repeated clicks without server progress cannot grant
  unlimited motion. Tests must cover stale old ticks and no-confirmation timeout.
- Same-target repeats must not reset motion; scene/minimap observation remains
  read-only. Route replacement must not steal red-click/native approach state.
- One MovementPath pipeline; no alternative animation/tween system. Absolutely
  no PlayerPresentation, pose clock or model/animation edits.
- Keep newer object footprints, native approach retargeting, herb interactions,
  camera, input processing, config keys and continuous recording intact.
- No reflection, game-input automation, blocking client-thread I/O, new deps,
  hardcoded game IDs, commits or broad reformatting.

## Scope and phased single bundle

1. Map trace intervals and exact click/authority sequences; classify why random
   clicks wait (actual eligible guard vs bounds/collision vs insufficient data).
2. Preserve snapshots, reproduce representative failure BEFORE the fix, implement
   one coherent generalization of fresh yellow-click route replacement. Prefer
   reusing existing route/corridor machinery over stacking direction-specific
   exceptions. Root design allows this within existing MovementPath/Controller.
3. Test all quadrants, acute/obtuse/right-angle changes, active cardinal/diagonal/
   knight segments, confirmed queues and speculative queues, blocked connectors,
   repeated random-direction sequences with realistic 600ms authority timing,
   stale ticks/timeouts/rebases, repeated same destination, and latest red-click
   regressions. Retain meaningful old regression expectations outside fresh-click
   policy; explicitly explain any necessary changed policy test.
4. Run targeted tests then .\gradlew.bat build --offline --console=plain --no-daemon.
   Return actual command/results, diffs vs today's baseline, and a timestamp
   evidence table. Update CHECKPOINT.md and relevant validation docs.

Allowed: MovementPath/Controller/Input/Route, minimal supported helper if needed,
trace scalar diagnostics, focused tests, docs. Animation/model and object approach
implementations are out of edit scope. Stop/report if the fix needs those changes
or unknown API access. Never claim in-game behavior from a passing build.

## Acceptance and routing

Astra reviews actual diff and regression evidence in a batch, with at most one
normal worker correction. New random-click tests must assert prompt response,
not merely eventual destination arrival. Do not require a new capture for a
behavior already demonstrated in this one. Explicitly report unsupported cases.

Primary openai/gpt-6-astra; worker flash-builder pinned deepseek/deepseek-flash.
opencode.cmd models deepseek lists the exact worker model. Prior task verified
actual routing via metadata; this bundle uses a new worker session because it
is a separate investigation and the old session reached its context limit.

Only user confirms gameplay. Final status: ready for user validation, not done.

## Execution update

`flash-builder` dispatch was rejected as an unknown agent; the live agent list
confirmed it was absent. User chose **Continue with Astra**, then explicitly
instructed **stop using DeepSeek Flash altogether**. This bundle is executed and
reviewed directly by Astra; no worker ran or edited files for it.

Baseline source snapshots are retained as Git blobs (no commit or staging):
- MovementPath.java: `0fc3b30f0c52c5725d0aa6d7092b734a6d9c9c14`
- MovementController.java: `c41d6045288d831ef02d16e267e6aa1c59acc260`
This supersedes the worker's temporary-directory snapshot procedure above.
All implementation changes were made with apply_patch.

## User acceptance update

The user reports the latest arbitrary-direction change works for scene clicks.
Treat scene-click retargeting as gameplay-confirmed. Minimap movement still does
not behave equivalently; the user explicitly deferred that work to a later task.
Do not modify minimap code until separately asked.
