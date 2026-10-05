# Herb-patch footprint follow-up — 2026-09-27

Status: **implemented and confirmed by the user in-game**. The user reports the
herb-patch change worked. They had already confirmed that the earlier
object-footprint correction solved all the reported 18:36 tree-route issues.

## Evidence

Captured in `C:\Users\AdamG\.runelite\responsive-movement\movement.log`;
times below are local `+10:00`. Each session starts during picking. The picking
samples already contain the actual 2x2 patch footprint. Planting and composting
subsequently arrive as `WIDGET_TARGET_ON_GAME_OBJECT`, and those samples lose the
footprint (`objectMin=-1,-1 objectMax=-1,-1`). Item names are not trace fields;
the user identifies the actions as planting and composting.

| Report | Session | Evidence |
| --- | --- | --- |
| 21:39 did it | `1790509176934` | Picking footprint `7232,5952`–`7360,6080`; player `7104,6080`, beside the west edge but diagonally offset from the anchor. Use clicks seq 434 at 21:39:45.621 and seq 550 at 21:39:47.938 start previews toward anchor `7232,5952`. At seq 453, action 2291 begins and the preview recovers from `7104,6000`, while `true` and `native` are still `7104,6080`. |
| 21:40 did not | `1790509228081` | Picking footprint `7488,8128`–`7616,8256`; player `7488,8000`, directly south of the anchor. Use clicks seq 307 at 21:40:34.210 and seq 424 at 21:40:36.552 both produce `route-or-deadline-unavailable`, with no preview. The old approach search already treats this cardinal adjacency to the single anchor as reached, so it returns an empty route. Geometry explains why the omitted footprint is masked here; this is not evidence that the footprint lookup worked for use actions. |
| 21:41 did it | `1790509267701` | Picking footprint `4800,6336`–`4928,6464`; player `4928,6592`, beside the north edge. Use seq 426 at 21:41:16.214 and seq 543 at 21:41:18.833 starts a four-leg running preview toward `4800,6336`. Seq 447 starts recovery at `4751,6592` with action 2291, while the native and confirmed player remain `4928,6592`. |
| 21:43 extra far | `1790509424899` | Picking footprint `4416,6976`–`4544,7104`; player `4672,6976`, beside the east edge. Use seq 128 at 21:43:47.451 and seq 244 at 21:43:49.770 starts a three-leg running preview toward `4416,6976`. The latter goes south and then west along `y=6848`; seq 267 at 21:43:50.228 recovers from `4602,6848` when action 8197 begins. It returns at seq 290, 21:43:50.690. Native and confirmed positions remain at the original patch edge. |

The native actions begin while the displayed player is still away/recovering;
the trace does not show the server waiting for the displayed detour to finish.
These are unnecessary **visual predictions**, not server-visible route changes.
The trace does not contain full collision maps, so the exact detour choice is
not reconstructed as an actual scene fixture.

## Cause and correction

`ObjectApproach.capture` accepted only the five ordinary object options. The
controller did recognize target-on-object actions, but their footprint capture
returned null before inspecting the scene. That sent them into the older
single-anchor approach prediction. Direction relative to that anchor explains
the working 21:40 case and the differing detours in the other captures.

The extension accepts both `WIDGET_TARGET_ON_GAME_OBJECT` and the legacy
`ITEM_USE_ON_GAME_OBJECT` through the same lookup as ordinary object options.
It uses the object ID, clicked scene tile, world view, plane, rectangular shape,
solid collision flag and multi-tile footprint already required for the tree
correction. It reads only the clicked tile at input, not the scene per frame.

Every object use captures its footprint afresh. A published destination inside
that footprint uses the nearest perimeter staging point; if already there,
`at-object-boundary` prevents a new preview. Distant approaches still go through
the collision-checked MovementPath. Native destinations outside the footprint
remain exact. A perimeter staging point is not a claim about permitted
interaction sides or spell reach: native movement remains authoritative.

There are no patch/item IDs or hover-color rules, no animation changes, and no
new prediction deadlines or movement-speed adjustments. NPC/player/ground-item
targets and inventory-only actions do not enter this object lookup. Walkable,
single-tile, missing or mismatched objects retain their existing fallback.

## Verification

- New `ObjectApproachCaptureTest` exercises actual `MenuOptionClicked` → scene
  lookup using test-only API doubles. Four tests failed on the prior action
  filter; all five passed after the extension.
- Coverage includes all four recorded patch positions, repeated uses, all five
  object options and both object-use action types, all eight edge positions on
  a 2x2 footprint, distant responsive starts, blocked routes, native refinement,
  fresh target lookup, and wrong-ID/view/shape/walkable/single-tile exclusions.
- Existing seven `ObjectApproachTest` cases also pass, including the 18:36 tree
  geometries, no lateral detours on one-tile approaches, timeout and cancellation.
- `.\gradlew.bat build --offline --console=plain --no-daemon` → BUILD SUCCESSFUL,
  exit 0. Fresh XML timestamp 2026-09-27T12:52:00Z: **103 tests, zero
  failures/errors/skips**.
- Automated checks reduce regression risk; they do not verify actual game
  appearance or prove every possible interaction has the same reach rules.

## User confirmation

After this extension, the user reported: **“that worked”** for the herb-run
problem. This is manual confirmation of the observed plant/compost visual loop
for their tested case. The user's report does not claim that every game object,
interaction side, or herb patch was retested.

## Optional broader in-game checks

Launch the updated development client with `.\gradlew.bat run` and use RuneLite's
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide as needed. For broader confidence, test picking,
planting and composting from different sides, distant approaches, and other
solid multi-tile objects. Adjacent uses should stay put; distant clicks should
still approach promptly. Also verify the previously confirmed tree starts and a
normal ground reversal. In a new trace, object-use clicks should carry the patch
bounds and `at-object-boundary` when already beside it.
