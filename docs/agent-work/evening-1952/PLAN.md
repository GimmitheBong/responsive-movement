# Evening movement investigation — 2026-09-22

## Objective and ownership

Astra owns scope, behavioral contracts, and acceptance. One `flash-builder`
worker owns trace analysis, implementation, regression testing and debugging.
User reports (local +10:00):

- 19:52:25: movement not fluid.
- 19:53:20 onwards: red clicks not instant.
- 19:54:25: strange route/movement.
- Around 19:56: possible corner-click delay.
- 19:57:45: some clicks not instant.
- 19:58:30: some clicks not instant.

Recording session `1790070725637` starts at 19:52:05.637 and spans rotating
files in `C:\Users\AdamG\.runelite\responsive-movement`. Determine actual
coverage and inspect context around each report, not isolated samples.

## Baseline and routing

Project: `C:\Projects\ResponsiveMovementPlugin` (not the harness working tree).
Git has no HEAD; all project files are untracked. `git diff` alone cannot review
changes. Before changing a file, the worker must preserve its original bytes
or exact text with the dedicated file tools/apply_patch in the pre-approved
temporary directory, then report a diff against those snapshots. Do not create
a commit, stage files, or alter user captures.

Primary: `openai/gpt-6-astra`. Worker: `flash-builder`, configured by the loaded
skill as `deepseek/deepseek-flash`. `opencode.cmd models deepseek` lists that
exact slug. Actual worker session metadata must be checked after dispatch.

## Contracts

- Preserve native PlayerPresentation animation/model handling. Prior changes
  there caused serious regressions; it is outside the worker's edit scope.
- One MovementPath continues to own position. Preserve collision checks,
  sub-tile continuity, bounded anticipation/deadlines and eventual reconciliation.
- Retain responsive open-ground, cardinal/diagonal reverse, minimap, and
  obstacle clicks. Preserve repeated-same-destination behavior and stale-tick
  handling unless a failing regression demonstrates a specific necessary change.
- Red clicks are approaches to native interactions, not permission to change
  server routes or trigger actions. Never infer universal interaction reach or
  attack range from target coordinates alone. Prefer native evidence and
  supported RuneLite APIs; retain confirmed fallback when evidence is missing.
- No input automation, reflection, external processes in plugin code, new
  dependencies, broad architecture rewrite, or unrelated config migrations.
- Diagnostics remain opt-in, bounded, asynchronous and within RuneLite's
  plugin data directory. Do not log player names or arbitrary menu text.
- Do not claim in-game validation from unit tests or a client launch.

## Alternatives and decision rule

Prefer the smallest correction to the demonstrated state/input defect. Do not
increase prediction limits, remove collision checks, slow/speed all movement,
or add target-specific magic IDs to make a symptom disappear. If a report is
ambiguous or not supported by available trace fields, report that limitation
and add only targeted diagnostics needed to resolve it.

## Single implementation bundle

1. Map coverage and diagnose all six windows. Record exact session/cycle/seq
   evidence and distinguish proven failure, likely cause, and inconclusive.
2. Reproduce supported failures with meaningful regressions, including timing
   and confirmation order when applicable. Implement focused corrections.
3. Run targeted checks then `./gradlew.bat build --offline --console=plain`.
   Use Windows `.\gradlew.bat` spelling at execution time. A harness process
   termination error can occur after Gradle finishes: inspect XML timestamps
   before concluding the build itself failed.
4. Return before/after diffs, tests/results, mapping per reported time, remaining
   uncertainty, and precise manual retest steps. Update CHECKPOINT.md.

## Allowed file scope

`MovementPath.java`, `MovementInput.java`, `MovementRoute.java`,
`MovementController.java`, `ResponsiveMovementPlugin.java`, `MovementTrace.java`,
focused tests in `src/test/java/com/responsivemovement/`, and relevant docs.
Small Java helpers are allowed only if essential to a scoped correction.
Config descriptions may be clarified, but keep keys/groups and defaults.

Stop and report if the fix requires animation/model changes, unsupported API
access, broad redesign, a missing capture, or unrelated user modifications.

## Acceptance

- Each timestamp has an evidence-based outcome; no invented root causes.
- New fixes have targeted failing-before/passing-after evidence where feasible.
- Existing meaningful movement/input tests remain intact and pass.
- No animation-system edits; no new unsafe shortcuts or unbounded work.
- Full build passes and actual worker routing is verified by Astra metadata.
- Final status remains awaiting the user's in-game confirmation.

## Astra acceptance pass 1 — correction brief

Runtime routing verified through OpenCode message metadata for session
`ses_f37672003ffeJdA7ObXBeTSmUN`: provider `deepseek`, model `deepseek-flash`,
agent `flash-builder`.

First patch is not accepted: it extends a red-click observation deadline without
captured evidence, calls 600 ms a client tick (client ticks are 20 ms; game ticks
are ~600 ms), and leaves demonstrated stale-preview overshoot as a documented
trade-off. Documentation does not make the reported responsiveness defect
acceptable. Review/correction remains within the existing allowed scope.

For an actual newly observed yellow destination which cannot use an existing
instant reversal, permit bounded redirect of an unconfirmed preview using the
currently occupied checked segment: use only a checked endpoint of that segment
as a route anchor, with continuous sub-tile travel to it, then the checked new
route. A corner/chord must not be cut unless the existing corridor check proves
the connector. Prefer a simple one-edge implementation; confirmed-only movement
and interaction behavior keep their existing handling. Retain the original hard
deadline and gap/queue bounds. Do not make arbitrary diagonals or remove waits
where no legal bounded redirect exists. Reproduce a captured non-collinear click
failure before coding; test stale confirmation, cancellation, timeout, repeated
clicks, blocked connectors and existing confirmed-corner regressions.

Revert the unevidenced 600 ms red-click window and its test/docs; diagnostic
age/status additions are allowed. Avoid additional spot-table scans on every
frame when tracing is disabled. Report red-click uncertainty and any additional
evidence needed rather than promise that diagnostics fix it. Distinguish a
normal endpoint stop from a fluidity bug, and replace unsupported claims about
collision-free behavior with the actual limits of the trace. Fix the unresolved
`seq 169?` reference. Return consolidated actual diff and verification evidence.
