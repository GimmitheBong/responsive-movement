# All-attack effects and native gait continuity — 2026-10-04

Status: **implemented, regression-tested and user-confirmed working well**.
The preceding exact-pickup correction is user-confirmed good. README/current
VALIDATION remain authoritative. The user explicitly requires melee/ranged/magic
and any weapon, not a bow-specific exception.
The user subsequently reports **"that change is working well"**. Its effect/gait
behavior is retained while the newer 00:41/00:46 fresh-Walk confirmation edge case
is corrected separately. The user subsequently confirms that latter correction
also **works well** for their tested situations.

## Newest capture and audit

Session `1791109040484`, **21:17:20.484–21:18:24.531 (+11:00)**, has **71 clicks /
3,204 samples** in `movement.log`. World-view 0, base `(3168,3416)`, speed **5.5**
(effective **1.1**), smoothing **0**, Original player when aligned **off**.

- **22** Walk clicks during native bow action `426` have `spot=true` and are marked
  `ineligible-click`. Examples 313 / 2581 at **21:17:26.689 / 21:18:10.832** observe
  flags at approximately **22/17 ms**, yet display starts at approximately **362/640 ms**.
- Contrast click 2972 at **21:18:18.570** has the same action with `spot=false`,
  is eligible, and departs at about **39 ms**. The pose permission from the preceding
  combat correction is being defeated by the separate spot-effect gate.
- Click 1751 at **21:17:54.649** is initially eligible, then the shot/effect arrives
  around **24 ms** afterward and the pending Walk is retired. This needs the same
  input-boundary evidence rather than waiting for another server update.
- Stationary display with native run pose `824` lasts approximately **300 ms** at
  21:17:26.533, **537 ms** at 21:17:40.853, and **601 ms** at 21:17:54.709. These
  have primary action `426`, not the bounded ordinary gait bridge.
- Custom running with native walking/turning poses occurs throughout action `426`
  and block `424`; representative spans are **360 ms** at 21:17:27.050 and **622 ms**
  at 21:18:11.471. Restoring native locomotion selectors during actions/effects lets
  the hidden actor's different movement state choose the displayed secondary pose.

The audit found no position-budget outliers and no sustained no-action gait mismatch
over 40 ms. Four small sampled turn-budget differences of a few units appear around
input/preparation boundaries; the sample timestamp is taken after preparation and
does not prove an actual uncapped turn. The native renderer/bone masks are not recorded.

## Shared correction and contracts

`CombatEffectCarry` uses no weapon/attack animation whitelist. Explicit Attack/Cast
input and native combat ownership supply escape evidence for any profile, including
unknown weapons, halberds, autocast and manual spell input. Manual spell approach/range
still needs native evidence. Recent input expires after **1.8 s**. Existing graphics
are captured at explicit Walk or once in the **100-ms** publication window; up to eight
identities are matched by source, ID, hash and start cycle for **900 ms**. Re-clicking
cannot renew the same capture, and a newly replaced effect cannot inherit it. Scene,
profile/input/native-location invalidation clears it. Effects are still built/drawn
using their native clocks; this metadata has no models, positions or route clock.

`MovementController` uses that permission consistently for observation, smoothing,
Walk release, authority alignment and ordinary confirmed-run reserve. MovementPath's
single time budget, checked corridor/confirmed-prefix handling, response/chain/gap
limits and native/server authority remain intact.

`PlayerPresentation` publishes native secondary walk/run/idle from displayed gait
through every real primary action/effect. It never sets the primary action or frame.
When native idle ticks need idle == gait for an anticipated/catch-up pose clock,
`NativeModelObject` temporarily supplies the real saved idle selector to each native
availability/draw-time build so that primary + non-idle gait can be blended. A `finally`
restores the tick selector immediately. Off-thread requests reject before actor access;
hide/fallback/close clear source and builder metadata. There is no mesh cache/transform,
posed-model reanimation, extra animation controller or selected attack/cast sequence.

Diagnostics add immutable primary-frame and effect-identity scalars/arrays. All actor
reads/copies are client-thread-only; formatting/I/O remains asynchronous and bounded.

## Verification

- Five of eight initial core tests fail before the correction.
- `CombatVisualContinuityTest`: **19** tests including a seven-profile matrix (melee,
  halberd, known/unknown range, magic, unknown combat, unarmed) with deliberately different
  native primary IDs. Manual Cast, lingering effects, walking/run, scene/minimap,
  smoothing, timeout/repeats, unrelated/replaced effects, profile/scene retirement,
  primary-frame preservation, native pose-clock progression and immutable diagnostics
  are covered. An old effect guard now exercises an unrelated Talk-to effect rather
  than forbidding valid combat-owned block/shot effects.
- `NativeModelObjectTest`: **3** new tests cover probe/draw selector restoration,
  worker rejection, failure restoration, model disappearance and default-source clearing.
- `BowVisualReplayTest`: **5** captured multi-click windows, each at
  **8.333/20/33.333 ms**, including pending-shot publication, mixed direction changes,
  a pickup, retained incoming block and no-effect contrast. `bow-2117.txt` preserves
  actual authority/flag/action/spot times and matching click collision crops for
  X=37..60/Y=40..53; unknown cells are blocked.
- Replay engagement, effect identity and primary-frame progression are explicitly
  synthetic API doubles because the original log has no such fields. Native ticks
  read the prepared selectors in the doubles; old pose/frame values are retained as
  diagnostic evidence rather than imposed as a second clock. Native fractions between
  retained changes are held. These checks do not execute the native renderer or prove
  every weapon's actual skeletal mask.
- `.\gradlew.bat build --offline --console=plain --no-daemon` passes **491 tests**,
  zero failures/errors/skips, retaining all 464 prior regressions. Source diffs were
  reviewed against captured Git blobs. No commits/staging or game input occurred.

## User validation

Launch `.\gradlew.bat run` using the
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide, with GPU or 117 HD. Start at speed **5.5**, smoothing **0**,
Original player when aligned **off**, with tracing enabled.

1. Attack/cast, then scene/minimap Walk at early/middle/late primary frames and while
   the effect is active or lingering. Check consistent prompt release and no extra
   pauses caused by the graphic, preserving actual attack/cast/block timing.
2. Check running/walking while the native actor is still behind or already idle,
   followed by a clean displayed stop; no borrowed run-in-place or turning/walk gait.
3. Repeat melee/ranged/magic, autocast/manual spells, known/new weapons, blocks,
   target death, repeated/mixed clicks and the confirmed ground-item pickups.
4. Toggle Animation Smoothing and Original player when aligned; disable/re-enable
   and cross a region. Verify native selectors/effects clean up and presentation
   remains correct. Only the user performs gameplay and confirms visual behavior.
5. Disable tracing to flush and report local timestamp/UTC offset and renderer for
   any remaining discontinuity.
