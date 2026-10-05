# Combat input and ranged target continuity — 2026-10-04

Status: **implemented, regression-tested and user-confirmed working really well**.
README/current VALIDATION remain authoritative. Earlier adjacent-melee continuity
and gaze/slower-pursuit refinements remain user-confirmed for their tested cases.
The user subsequently reports **"those changes are working really well"** for
this combat input/bow targeting work. The later 20:33/20:35 ground-item destination
correction is separate and awaits its own confirmation.

## Capture and user clarification

Newest session `1791097816262` runs **18:10:16.262–18:11:30.016 (+11:00)** in
`movement.log`, 29 clicks and 3,689 rendered samples. It follows the approximately
18:05 report. World-view 0, base `(3160,3416)`, configured speed **5.7** (effective
**1.1**), smoothing **0**, Original player when aligned **off**. The user confirms
**magic shortbow (i), Rapid**, and chooses to retain the current post-arrival melee
pursuit gaze while adding stationary ranged targeting.

| Window | Captured evidence | Correction |
| --- | --- | --- |
| 18:10:23.359, click 356 | Walk during `1658`; flag after 15.8 ms; display waits 718 ms | Observe/release combat Walk during native poses |
| 18:10:32.398, click 818 | Walk during `1658`; flag after 20.9 ms, withdrawn at 58.5 ms; display waits 678 ms | Retain the first native publication instead of dropping the click |
| 18:11:05.698, click 2494 | Walk during `426`; flag after 20.9 ms; display waits 378 ms | Same checked escape policy for bow |
| 18:10:58.674, click 2140 | Bow reserve 10; stops at `(5952,6208)` for about 221 ms, then at `(6208,6208)` before final authority | Correct native Ranging classification; whole-pair normal-pace firing prefix |
| 18:11:22.659, click 3348 | No early preview from ten tiles; first movement at 219 ms, then an intermediate stop | Known shortbow seven-tile boundary with checked run pairs |
| 18:10:49.438, 18:11:05.158, 18:11:08.196 | In-range bow retains no combat facing owner before the native shot | Click-scoped stationary gaze through native engagement/poses |

The capture records weapon **12788** and reserve **10**, but not its cache names
or selected style. The native style contract is independently established by
RuneLite 1.13's supported [Attack Styles implementation](https://github.com/runelite/runelite/blob/runelite-parent-1.13.0/runelite-client/src/main/java/net/runelite/client/plugins/attackstyles/AttackStyle.java):
`ATTACK_STYLE_NAME` uses **Ranging**, not Rapid/Accurate button labels. The user
supplies Rapid evidence. Old ranged handling stops publishing target bounds/
engagement at the shot; it does not record every pre-tick angle or live target step.

## Implementation

- `CombatApproach`: native Ranging recognition; reliable known-ranged eligibility
  separate from unknown/casting/halberd conservative reserves. Captured style/category
  fields are available as immutable diagnostics.
- `NpcApproach`: refresh the one clicked Attack target; bounded identity/size/view/
  plane checks. Known ranged prefixes require reversible movement edges and native
  line-of-sight evidence at a step/whole-run-pair firing endpoint. No NPC-ID/location
  routing table or scene enumeration is used.
- `CombatContinuity`: selected-target lifetime and native engagement/facing for all
  Attack profiles. Pending expiry is still 1.8 s. Ranged action poses stop predictive
  following; real confirmed debt and target facing remain. Melee trail/hit release
  retain their existing profile/ownership gates.
- `MovementPath`: all ranged prefixes/joins/authority/cancellation use the existing
  queue, fractional position, normal clock, finite credit and 900 ms/1.8 s bounds.
  Whole-pair endpoints bypass generic square clipping only for checked known-ranged
  plans. No ranged melee-trailing pace or second movement engine is introduced.
- `MovementController` / `MovementFacing`: explicit combat Walk release and current-
  angle hold; observation/retargeting/facing continue through native poses. A stale
  native target cannot reclaim facing. Fresh native auto-engagement with the native
  setting enabled and Walk destination/observation gone cancels obsolete prediction
  and restores native facing, preserving checked confirmed catch-up. Cleanup clears
  owners. Pose selectors/model-provider/camera code was not part of this correction.
- Trace snapshots add cache-style/profile and combat Walk scalars. Click contexts
  copy category/style/auto-retaliation. Formatting/I/O remain ordered/asynchronous.

## Verification and evidence limits

Five of the original seven focused tests fail before implementation. Final
`CombatTargetContinuityTest` has **17** guards. `CombatInputReplayTest` has **10**
recorded cases, each at **8.333/20/33.333 ms**, using `combat-1810.txt`.

The fixture copies raw click 356 collision flags for X=40..64/Y=35..52; all outside
the crop is blocked. The corresponding crops are identical across all ten replay
clicks. Click/authority/destination/action timing is recorded. Native player points
between retained sample changes are held. Ranged NPC drawn positions, engagement,
cache styles and auto-retaliation use explicitly labelled API doubles: the old
sample fields cannot prove those details. The in-range 1676 case also retains the
later real confirmed movement instead of treating its initial range as permission
to ignore subsequent server updates.

Guards include native cache Accurate/Rapid/Longrange, pre-tick/drawn facing,
normal pair pacing, bounded target motion, click-away during shots/blocks, stale
target and hit isolation, fresh auto-retaliation during confirmed catch-up, input
gates, minimap smoothing/lifecycle, conservative profiles, closing projectile sight
with confirmed-prefix preservation, and asynchronous diagnostic immutability.
An earlier ranged-motion invalidation test now uses a **three-tile discontinuity**:
the formerly rejected two-tile step is within the new observed-target contract.

`.\gradlew.bat build --offline --console=plain --no-daemon` passes **457 tests**,
zero failures/errors/skips, including all 430 prior movement/model/camera cases.
Production diffs were reviewed against captured Git blobs; no staging/commits or
automated gameplay were performed. These tests do not execute the native renderer.

## User validation

Launch `.\gradlew.bat run` with GPU or 117 HD and the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide. Begin with speed **5.7**, smoothing **0**, Original player
when aligned **off**, and tracing enabled.

1. Attack with melee, then Walk away during hit/block poses; include scene/minimap,
   longer paths and mixed re-clicks. Check prompt response and no extra stop/restart
   or turn back toward a lingering target.
2. Use magic shortbow (i) Rapid from inside and outside range, including offsets.
   Check a continuous approach to its real firing stop and stationary gaze before
   the first shot, during the native pose and between shots.
3. Retest moving melee pursuit gaze, NPC movement, target replacement/death, walking,
   Longrange and blocked sight. Check auto-retaliation off, then on with genuine
   native re-engagement, including during catch-up.
4. Disable recording to flush; report local timestamp/UTC offset for any remaining
   case. Only the user performs gameplay and confirms visual behavior.
