# Accessible-side counter corner arrival — 2026-10-05

Status: **implemented, regression-tested and user-confirmed working well for the tested situation**.
README and current VALIDATION remain authoritative.

## Latest one-click capture

Session `1791188705958`, sole recorded click **375**, Bank at
**19:25:13.446 (+11:00)**. View 0, base `(3112,3424)`, banker definition 1633 at
world `(3163,3489)`, speed 5.4 (effective 1.1), smoothing 0, aligned native handoff
off. These IDs and coordinates are fixture evidence, not production lookup rules.

- Start: world `(3161,3485)`, local `(6336,7872)`.
- First/final authority: **14.180**, local `(6336,8128)` / world `(3161,3487)`,
  about **734305 us** after click observation. No native destination/action/effect
  is published during the approach.
- Old forecast: checked northbound pair followed by an unsupported knight pair to
  `(6464,8384)` / world `(3162,3489)`, reached **14.625**.
- Timeout recovery: **15.064**, returning to the true stop by **15.643**.

The rounded counter stop inferred from earlier run endpoints excludes `(2,2)`.
The newer counter *start* gate already treats this corner as potentially in range,
but running arrival still releases the extra pair without onward evidence. This
is a counter prediction gap, not evidence that the recent gate or native model work
caused it. Simply expanding every counter stopping envelope would break supported
routes around the blocked side and ongoing approaches with native flag evidence.

## Shared correction

`NpcApproach.counterRun` keeps its original checked route to the accessible side
and rounded whole-pair stopping endpoint. It also identifies an earlier whole-pair
`(2,2)` boundary on the accessible side using signed geometry. Bank/Exchange and
cache-qualified service Talk-to are still the only options that capture this geometry.

`MovementPath` retains that boundary and caps prediction there. Confirming the
corner does not replenish into the unsupported next pair. A newly matching native
counter flag releases the retained checked route through existing `extend` handling,
without resetting the fraction/clock or renewing either deadline. Actual onward
server steps are still accepted normally. Idle seeds, queued confirmed prefixes,
moving fractional joins and rebases copy/translate the cap; cancellation/replacement
clears it. `npcArrivalGoal` diagnostics reflect the current boundary, and route
snapshots add `counterBoundaryGoal`. Full logical routing remains available.

## Verification and evidence limits

Two of the first five tests fail on the original code: recorded arrival and the
no-authority cap/timeout guard. Final `CounterCornerArrivalTest` has **10 tests**.
The recorded Bank timeline runs at **8.333/20/33.333 ms** with normal run pace,
one positional/turn budget and stable actual arrival. Exchange/service Talk-to are
explicit option-parity doubles, not additional live captures.

The fixture copies this click's actual RLE collision columns X=46..55 and sampled
native points; other space is blocked. Click-to-first-preparation age (895 us) and
first/final authority timing are retained. Native fractions are held between retained
samples. Rotated geometry, late/immediate flags, extra authority, blocked onward
edges, deadline retention, confirmed queues, moving joins and rebase checks use
explicit API doubles. No test runs RuneScape's renderer or proves universal reach.

Focused checks retain all **57** earlier GE replays, the twelve banker Talk-to
replays, diagonal start boundary and counter guards. Full
`.\gradlew.bat build --offline --console=plain --no-daemon` succeeds:
**517 tests, zero failures/errors/skips**, XML timestamps
**2026-10-05T08:40:29.164Z–08:40:32.650Z**. All 507 preceding regressions pass.

Production diffs are limited to NpcApproach, MovementPath and MovementController.
Original Git blobs were retained without staging/commits (the repo has no tracked
HEAD baseline):

- NpcApproach: `e1953de2aae7fec886bc22e603c03458deb3146d`
- MovementPath: `9e8f29ddc6f87c789cfe95ff1b6aae820fbc1f69`
- MovementController: `698815bdf1b7292f7859ec506761811601a4c138`

## User check

The user subsequently reports **"that change is working well"**. This confirms
the captured accessible-side Bank corner situation; it does not validate every
counter geometry or option combination.

Launch `.\gradlew.bat run` with GPU or 117 HD and the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide. Use speed 5.4, smoothing 0, Original player when aligned
off, and tracing first. Repeat Bank from `(3161,3485)` and check one normal-paced
pair to `(3161,3487)`, holding there without the extra diagonal trip or timeout
return. Check Bank/service Talk-to already at the corner, nearby longer/moving
approaches and Exchange, including a native flag that really requires continuing.
Disable tracing to flush and report any remaining local timestamp/UTC offset.
Only the user performs gameplay and confirms visual behavior.
