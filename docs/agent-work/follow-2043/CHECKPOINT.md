# Explicit Follow native-presentation handoff — 2026-10-05

Status: **implemented, regression-tested and user-confirmed working well for the
tested player Follow situation**. README/current VALIDATION remain authoritative.

## Evidence

The latest session `1791193390639` contains a player Follow at **20:43:18.079
(+11:00)**, click sequence **373**, player index **623**. Settings: speed **5.4**,
smoothing **0**, Original player when aligned **off**. The click is positionally
settled at local `(6208,8000)`. No native destination is published. Authority first
moves to `(5952,8256)` at 18.279, then `(5952,8384)` at 18.878. The subsequent
one-tile endpoints alternate about every 600 ms, while custom running completes
each tile in about 300 ms and waits with an idle pose. Native interpolation is
still moving during those waits; at 19.178, display Y=8384 but native Y=8308.

This is real alternating authoritative movement presented at an unsuitable
retained running pace, not an invented speculative route or a combat effect
failure. The trace does not record this player's target identity/engagement each
frame, the reason for the server's alternating endpoints, or a quest NPC Follow.

## Narrow correction

`FollowPresentation` captures an explicit Follow option on a player/NPC, with
validated view/plane/index/reference and NPC definition. Matching native engagement
permits a handoff only at exact native/displayed positional alignment, a finished
path and facing within ten units. Native ownership is then latched until replacement
or invalidation, including when Original player when aligned is off. Already aligned
starts can transfer before the first step; moving clicks keep checked confirmed debt
and fraction through MovementPath cancellation before handing off. No target-motion
prediction, approach-distance rule or second movement clock is added.

Native body/effects and selectors are restored through the existing native fallback.
Walk/other world input immediately releases ownership; native fractional movement is
adopted by the existing shared path. Lost engagement, target death/disappearance/ID or
view/plane changes, native-location actions and scene/lifecycle invalidation clear it.
Pending engagement expires at 1.8 s; repeated identical Follow preserves its lifetime.
Other options, including combat pursuit, do not acquire this presentation policy.
The trace uses existing decisions `awaiting-native-follow`, `native-follow` and
`native-follow-ended`; no schema/config changes are introduced.

## Verification and limits

Five of the initial six controller tests fail before implementation. The final
`FollowContinuityTest` has **12 tests**, covering both target types, three cadences,
initial and moving handoffs, confirmed-prefix preservation, exact position/capped
facing, delayed engagement, repeat/expiry, moving native-to-Walk adoption, option
isolation, target/lifecycle cleanup and body/effect/selector restoration.

Recorded endpoint timing/geometry and representative native samples inform the
oscillation regression, with explicit open-collision and engagement doubles. NPC
Follow parity and additional target conditions are synthetic checks. Intervening
native fractions are representative held values, not a full live timeline or native
renderer simulation. Tests cannot establish visual feel or quest-script coverage.

`.\gradlew.bat build --offline --console=plain --no-daemon` succeeds: **529 tests,
zero failures/errors/skips**, retaining all 517 prior regressions. Production edits
are limited to MovementController and the new FollowPresentation helper. The original
controller Git blob is `7d5d0b0f73e343fa07418255df134162442df899`; no staging/commit or
automated gameplay occurred. The logs were inspected read-only.

The user subsequently confirms **"that change worked well"** for the reported player
Follow behavior. This confirms the tested situation; the trace did not include a
quest-NPC Follow, so that parity remains useful future validation.

## User check

Launch `.\gradlew.bat run` with GPU or 117 HD using the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
guide. Use speed 5.4, smoothing 0 and Original player when aligned off, with tracing.
Repeat player Follow through alternating short steps/stops and longer movement,
then an NPC's actual quest Follow option. Include a moving Follow click, repeated
Follow, Walk/minimap cancellation, target loss and disable/re-enable. Check native
continuous following without an added stop/run pulse or handoff snap, followed by
the existing responsive Walk/combat/object/NPC behavior after replacement. Only
the user performs gameplay and confirms the result; disable tracing to flush and
report any remaining local timestamp/UTC offset.
