# Responsive Movement development history

This preserves the pre-publication README and its dated investigation notes.
Use the [user README](../README.md) for setup and current settings, and
[VALIDATION.md](VALIDATION.md) for the latest confirmations. Dated build counts and
dependency pins below describe their historical checkpoints.

A standalone Java 11 RuneLite plugin by **GimmitheBong**. It makes the **local
player's displayed movement** respond to observed clicks while keeping the
server-visible route and native game actions authoritative. One `MovementPath`
handles anticipated starts, confirmed walking/running, interaction approaches,
replacements and correction. The body uses RuneScape's native player-model
builder; the plugin does not select custom leaps or animate a posed model again.

## Start here for a new chat

Read this README for current behavior and status, then
[ARCHITECTURE.md](ARCHITECTURE.md) for component and continuity details.
[VALIDATION.md](VALIDATION.md) has current user-confirmed results, remaining
questions and in-game checks. Dated files under `docs/agent-work/` are
**historical investigation checkpoints**: an older “pending” statement may have
been superseded by a later user confirmation. For the latest model API changes,
see [RUNELITE-1.13.md](RUNELITE-1.13.md); for the earlier route investigation,
see [route-stability-2145/CHECKPOINT.md](agent-work/route-stability-2145/CHECKPOINT.md).

### How it works now

- **Scene and minimap Walk clicks**, including right-click Walk here, observe the native
  destination and can start or redirect the displayed player from its exact
  fractional position. The default **Walk-click smoothing (ms)** waits **50 ms**
  from click observation before releasing a preview (range **0–60**; **0** disables the
  wait). Existing movement and server confirmations continue during that window.
  The first new destination for a click is retained so an older server update
  cannot replace it. Native destination availability and render timing also
  affect visible response.
- **Follow on players or NPCs** now has a scoped native-presentation handoff. Once
  the selected target has native engagement and the displayed body is aligned in
  position/facing, native rendering owns that Follow until replacement or invalidation,
  including when **Original player when aligned** is off. An already aligned start
  can hand off before the first native step; a moving click first retires obsolete
  speculation through the shared checked path, preserving confirmed fractional movement.
  This avoids the captured alternating run/idle pulses without guessing a moving
  target's route. Walk and other world actions immediately resume their existing
  handling. Twelve regressions and the full **529-test** build pass; the user reports
  this correction **worked well** for the tested player Follow situation. Quest-NPC
  Follow and other variants remain useful checks.
- **Door/gate Open and Close** now use the existing object-perimeter staging when
  the clicked tile contains a matching native wall object. A hinge-tile destination
  cannot manufacture a trip through the door or past the actual stopping side.
  Confirmed crossings retain their checked prefix; stale Walk previews beside the
  wall retire through normal recovery, and late native refinements keep that boundary.
  The 12:42–12:45 gate capture informs nine new regressions; the full **507-test**
  build passes. This focused correction **awaits in-game confirmation**.
- **Bank-counter corner arrivals** now reserve onward prediction when a checked
  whole run pair reaches the accessible-side `(2,2)` corner without a native
  destination. The 2026-10-05 **19:25** single Bank click actually stops there;
  the previous rounded-counter forecast added an unsupported pair and timeout return.
  The original checked counter route remains available: a matching native flag can
  release its forecast at normal pace without restarting the clock or extending
  deadlines, and further real server steps still move normally. The earlier
  counter routes and diagonal start boundary are retained. Ten new checks and the
  full **517-test** build pass. The user confirms this change is **working well**
  for the tested situation; other counter layouts remain useful checks.
- **Repeated pickups and moving object approaches** now reuse the existing
  continuity handling. Repeating the same ordinary scene action/ID/tile retains
  its checked forecast and original deadlines. A newly observed object/item
  destination, its native refinement, or checked forward authority can join from
  the displayed fraction instead of completing an obsolete Walk preview. A
  partly confirmed knight retains only its checked confirmed prefix.
  The 2026-10-04 00:04 pickup/yew regressions pass; this correction awaits
  in-game confirmation.
- **Ground-item destinations** now require the exact newly observed native tile
  when searching a blocked route, including moving handoffs and queued continuations.
  A pickup cannot use an object-style adjacent endpoint, stop there and restart,
  or rejoin that obsolete endpoint after authority reaches the item. Collision,
  observation and prediction bounds remain in the shared MovementPath pipeline.
  The 20:33/20:35 capture replays and full **464-test** build pass; this latest
  correction is now **user-confirmed good** for the tested pickups.
- **Combat visual continuity** now shares effect-aware Walk release across melee,
  ranged and magic attacks on any weapon, including unknown profiles and recent
  manual Cast input. It retains only the observed effects within bounded lifetimes;
  new/unrelated effects keep their gates. Native walk/run/idle poses follow displayed
  movement even during real attacks, casts and blocks. The native builder keeps the
  original primary action/frame and effect timing; no posed mesh is animated again.
  The **491-test** build passes; the user subsequently confirms this change is
  **working well**. The newer yellow-click confirmation correction below is also
  user-confirmed working well for the tested situations.
- **Yellow-click confirmation** now keeps a newly retargeted visual itinerary pending
  when its destination already equals the old true tile. A delayed opposite update
  from the preceding click cannot append an extra return trip behind it. Checked
  one-tile/knight endpoint connectors share that rule, with the original collision,
  response/chain/gap bounds and exact fractional position. Both latest recorded
  endings pass replay checks; the full **498-test** build passes. The user subsequently
  confirms this correction **works well** for the tested situations.
- **Area crossings** retain an eligible checked route and fractional position
  across ordinary overworld coordinate rebases, rather than automatically
  returning a preview to the confirmed tile. The new scene must validate the
  route and destination; prediction deadlines remain bounded. This correction
  passes regression tests but awaits in-game confirmation. It targets added
  backtracking/stopping, not the native client's loading stall.
- **Routes** are straight-first and collision-checked. Blocked routes use a
  bounded detour search; two-tile running chords require checked alternatives.
  Fresh-click connectors can use checked diagonal sides/knight corridor anchors,
  prefer shorter visible travel on equal-time routes, and avoid unnecessary
  backsteps. Predictions have finite time/distance bounds and reconcile to
  authority; they never change what the server makes the character do.
  Moving Walk forecasts can now reconnect to a newly confirmed forward
  true-tile step when their construction route is on a different row or column.
  The checked join retains the fractional position and movement clock instead
  of waiting for the mismatched preview to expire. The user confirms this
  correction worked really well for scene clicks; its minimap extension awaits
  in-game confirmation.
- **Turning speed** (default **30**) caps custom facing, even on short legs,
  reversals and arrivals; **Movement speed** (default **5.0**) controls the whole
  path, with **5.0** matching the former **1.0** pace and each **0.1** step changing
  speed by 2%. The available scale is **0.5–10.0**. If an older config has a
  saved value, set it to **5.0** to retain the former default pace. Position and facing have separate
  pacing. Short arrivals can complete their turn without a one-frame snap.
- **Confirmed continuous running** can keep a small exponential reserve in the
  last 100-ms portion of its checked final leg while native movement/destination
  evidence remains ahead. This reuses the existing final-leg pacing mechanism to
  avoid finishing a run pair early and waiting motionless for the next server
  update. It adds no predicted tile, and normal arrival/action/replacement input
  releases the reserve. The 00:05 running replay passes; in-game confirmation is pending.
- **Red-click interactions** prefer a newly published native approach
  destination. Ordinary non-Attack NPC options, including Bank and Exchange,
  can now preview a clear initial approach from the clicked NPC's footprint
  when that destination is absent. Bank/Exchange forecasts reserve the final two-tile
  approach for server confirmation, even when a native flag names a closer tile;
  this avoids predicting past a banker/clerk's extended interaction range.
  That extended stopping reserve is restricted to **Bank/Exchange** options,
  plus **Talk-to at a qualifying one-sided bank-service counter**. The Talk-to
  exception requires the clicked NPC's native cache actions to include Bank or
  Exchange and reuses the existing counter route/stopping rule. Ordinary Talk-to
  and Trade approaches retain an adjacent boundary.
  Moving NPC clicks can now replace an obsolete Walk preview from its exact
  fractional position through an authority-anchored checked join. Ordinary NPC
  native refinements use that join too, avoiding a return to a construction anchor.
  The user confirms the 18:20–18:22 moving Bank/Emblem Trader/Perdu correction was
  good. The subsequent 19:01–19:02 and 19:05 captures add ordinary-NPC continuity
  handling: checked cardinal-side staging, short diagonal-adjacent starts, rejection
  of distant preceding-Walk flags, bounded retention through stale Walk ticks,
  and checked joins to new forward NPC authority. All 32 new approaches pass
  replay checks at three frame cadences; the user subsequently reports that
  continuity follow-up was good. The latest 19:45–19:46 banker Talk-to extension
  passes all twelve capture replays; the user subsequently confirms it worked well.
  Collision checks and prediction bounds still apply. **Adjacent melee Attack**
  now retains the clicked NPC through checked target steps and native combat poses.
  It approaches a checked adjacent side at the selected run/walk pace, holds there
  while the NPC stays still, and spends a bounded follow reserve only on observed
  NPC motion or genuine player progress. Native engagement and weapon/style remain
  authoritative. `MovementPath` owns every join, clock and prediction budget; the
  native builder supplies the real hit animation at the displayed transform.
  The user reports the initial combat continuity is working well. The newer
  **arrival gaze lock / slower pursuit** refinement keeps facing the NPC's drawn
  position after arrival, including while following and through native combat poses.
  Post-arrival follow uses 65% of configured walking pace with an exponential final
  leg. A real client-marked owned hit on the selected NPC releases the checked
  confirmed remainder at normal pace while its native pose plays. This 409-test
   refinement is now **user-confirmed working well**. The newer **combat input / bow
   targeting** correction recognizes native cache `Ranging` styles, and known ranged
   weapons can preview a checked approach to a line-of-sight firing boundary, retaining
   whole run pairs at normal pace. Attack clicks retain stationary target facing before,
   during and between native attacks; existing melee pursuit gaze is retained. Walk
   releases the target immediately and can start during a native combat pose; remaining
   shots/blocks do not retire its checked forecast or reclaim facing. Fresh native
   auto-retaliation can restore native facing and retire an obsolete Walk preview.
   The full **457-test** build passes; the user subsequently reports these combat
   changes are **working really well** for the tested situations.
   Halberd, casting and unknown profiles retain their conservative positional policies
   with stationary Attack-target facing. Item/spell
  targeting retains native-destination handling. The user reports the early
  starts and stopping refinement work well. A further close-range adjustment
  eases a one-tile idle preview toward its staging point until authority arrives,
  avoiding an early stop/restart. The user reports this works well too, while
  leaving close Bank/Exchange clicks as a possible later follow-up.
  The five-tile straight-run case now releases the final checked run pair to
  the adjacent native destination after matching two-tile authority. It uses
  normal run pacing all the way to that goal, replacing the rejected arrival
  easing. The user confirms this worked for the tested case.
  The subsequent short Bank/Exchange correction covers idle running starts
  three tiles away, with zero/one/two-tile offsets. It previews a complete
  checked two-step pair at normal speed, using an accessible adjacent side or
  the checked straight approach for counter/extended-reach cases. This latest
  extension awaits in-game confirmation.
  The newer **one-sided counter approach** correction uses the captured collision
  geometry to route Bank/Exchange toward the NPC's sole accessible adjacent side.
  It retains that route through late native flags and forecasts complete run pairs
  to the supported stopping boundary at normal pace. This supersedes the generic
  staging/short-start policies for eligible counter runs. All **57** approaches
  in the 23:14–23:22 Bank capture pass replay checks; the full **310-test** build
  passes. The user has now confirmed the correction works in-game. No per-tile
  GE route table or NPC-ID list is used.
  A newer **two-tile diagonal counter-start boundary** correction covers the
  reported 2026-10-04 13:10 corner clicks. At a qualifying bank-service counter,
  a fresh click already within two tiles on each axis waits for actual server
  movement, avoiding an invented trip and timeout return. This is a start gate;
  longer runs retain their confirmed whole-pair stopping rule. Bank/Exchange and
  cache-qualified bank-service Talk-to are the only affected options. The full
  **430-test** build passes; this latest correction awaits in-game confirmation.
  Solid multi-tile object options and item/widget-on-object actions
  can use a clicked-object footprint to avoid an unnecessary trip toward its
  anchor. Native refinements and server movement still decide the actual approach.
- **Minimap input** uses the same smoothing, idle starts, arbitrary-direction
  retargeting and true-tile alignment as scene Walk clicks. The native client
  converts the minimap press to a destination; the plugin captures that result
  after interface processing and retains it during smoothing. Repeated and mixed
  scene/minimap clicks share the same bounded movement pipeline.
- **Presentation** includes an optional adaptive camera (native zoom/follow
  height; native camera restored outside drawing), custom local-player overheads
  and hitsplats, and an optional marker of the hidden native position. Requires
  GPU or 117 HD. It conflicts with **True Tile Movement**.

### Current validation status

The **2026-10-05 20:43:18 (+11:00)** player Follow capture shows repeated one-tile
authority reversals displayed at the retained running pace, followed by idle waits
while native interpolation continues. Explicit player/NPC Follow now permits an
aligned native handoff and retains native presentation for the ongoing Follow.
Moving-click confirmed debt remains in MovementPath until alignment; replacement
input and target/scene invalidation release ownership. The full **529-test** build
passes; the user subsequently confirmed it worked well for the tested situation.
See [VALIDATION.md](VALIDATION.md).

The **2026-10-05 19:25:13 (+11:00)** recording has one Bank click from
world **(3161,3485)**. Authority stops at **(3161,3487)** without a destination flag,
but the old forecast continues to **(3162,3489)** and returns on timeout. An
accessible-side corner reserve now reuses the checked counter route and shared
MovementPath boundary/confirmation handling. Matching native destination evidence
can release the retained route without an extra time budget. The captured replay,
nine continuity/geometry/budget guards, all earlier counter captures and the full
**517-test** build pass. The user reports this change is **working well** for the
tested situation; this does not confirm every counter geometry. See
[VALIDATION.md](VALIDATION.md) and the
[corner-arrival checkpoint](agent-work/counter-corner-1925/CHECKPOINT.md).

The newest **2026-10-05 12:42–12:45 (+11:00)** recording contains repeated gate
Open/Walk/Close sequences. Close forecasts can follow the native hinge flag beyond
the real stopping tile, then jump back when closing collision invalidates them.
A matched-wall extension of the existing object boundary handling now passes nine
new guards/capture-based cases and the full **507-test** build. The change is
**awaiting in-game confirmation**. Collision changes under an already occupied
unconfirmed edge still require checked recovery or native fallback; the tests do
not establish every door type or native forced-movement handoff. See
[VALIDATION.md](VALIDATION.md) and the [gate checkpoint](agent-work/door-gate-1245/CHECKPOINT.md).

The latest **2026-10-05 00:40–00:41 / 00:44–00:46 (+11:00)** Walk recordings show
the reported post-click back-and-forth. A fresh yellow target matching the old true
tile could inherit confirmation before a delayed preceding-click endpoint arrived,
which appended obsolete return debt. The correction retains the responsive checked
retarget while keeping it pending through bounded stale updates. Both recorded endings
pass at three cadences, plus five confirmation/collision/deadline/debt guards; the
full **498-test** build passes. The user subsequently confirms the correction
**works well** for the tested situations. No clear displayed
departure over 100 ms was identified in these two sets. The preceding all-attack
effect/gait change is now **user-confirmed working well** and is retained. See
[VALIDATION.md](VALIDATION.md) and the [Walk checkpoint](agent-work/walk-confirmation-0041/CHECKPOINT.md).

The newest **2026-10-04 21:17–21:18 (+11:00)** continuity audit identifies 22 Walk
clicks rejected during active shot graphics, plus native locomotion poses mismatching
displayed movement through attack/block actions. Effect-aware combat release and
native secondary-gait alignment now pass **491 tests**: five captured multi-click
replays at three cadences, nineteen continuity/style/lifetime guards and three new
client-thread model-provider checks. The implementation applies across melee,
ranged and magic attacks and any weapon; the actual capture contains bow combat.
The user subsequently confirms it is **working well**. See [VALIDATION.md](VALIDATION.md) and the
[continuity checkpoint](agent-work/combat-visual-2117/CHECKPOINT.md).

The newest requested **2026-10-04 20:33 / 20:35 (+11:00)** pickup follow-up fixes
ordinary ground-item forecasts incorrectly stopping beside their exact native
destination. Both final pickup replays pass at three frame cadences, along with
five scope/evidence/continuity guards; the full **464-test** build passes. The
user subsequently reports the correction was **good**. The separate recording ending at
**20:38:04** was excluded as requested. See [VALIDATION.md](VALIDATION.md)
and the [pickup checkpoint](agent-work/pickup-destination-2033/CHECKPOINT.md).

The **2026-10-04 18:10–18:11 (+11:00)** combat capture exposes Walk clicks
rejected during native attack poses, bow profiles falling back to a ten-tile reserve,
and target-facing ownership ending before/around shots. The user identifies the bow
as **magic shortbow (i), Rapid**, and requests retaining the existing melee pursuit gaze.
The shared combat input/targeting correction passes **457 tests**, including ten
captured replays at three cadences and seventeen targeting/input/geometry guards.
The user subsequently reports those combat changes are **working really well**.
See [VALIDATION.md](VALIDATION.md) and the
[combat checkpoint](agent-work/combat-input-1810/CHECKPOINT.md).

The newest **2026-10-04 13:10 (+11:00)** banker-counter follow-up fixes an unsupported
preview from world **(3161,3487)**, diagonally two tiles from the banker. Native
position/authority do not move on the recorded Bank/Talk-to clicks, but the old
preview runs to the counter and returns on timeout. A bank-service-counter-only
start boundary now passes the full **430-test** build, including eight new replay,
scope and continuity checks. It **awaits in-game confirmation**; the earlier
counter-route confirmation remains the baseline. See
[VALIDATION.md](VALIDATION.md) and the
[corner checkpoint](agent-work/bank-counter-1310/CHECKPOINT.md).

The latest **2026-10-04 00:04–00:05 (+10:00)** repeated-pickup, moving-yew and
confirmed-running corrections pass the full **422-test** build. Four recorded
timing replays run at three frame cadences; nine additional guards cover
identity, observation/deadline bounds, checked joins/prefixes and pacing.
These latest changes are **awaiting the user's in-game confirmation**. See
[VALIDATION.md](VALIDATION.md) for capture/evidence scope and retest steps.

The user reports that turning and scene-click smoothing feel much smoother,
arbitrary-direction **scene** retargeting works, and the 2026-09-28 evening
stall/backtracking corrections worked well in-game. Earlier tree-footprint and
herb planting/composting corrections were also confirmed for the reported cases.
These reports confirm the tested situations, not every possible object, blocked
route, or scene layout. The user subsequently reports that the RuneLite
**1.13.0 compatibility fix worked** when running the plugin. The 1.13.0 build
passed **139 tests**, including the earlier route regressions and new model
provider checks. This confirms the reported startup/compilation issue was fixed;
it does not claim that every spot effect, model handoff or renderer combination
was individually tested. See [VALIDATION.md](VALIDATION.md) for that scope.

Known separate questions: UG launcher idle-alert compatibility is unresolved
after restoring native pose-builder behavior.
The earlier guard/Attack reserve attempt made no reported in-game improvement.
Adjacent-melee continuity and its gaze/pursuit refinement are user-confirmed.
The subsequent known-ranged approach/targeting work is also user-confirmed;
unsupported weapon/reach and blocked-layout cases remain conservative.
The 2026-09-30 banker/GE-clerk captures show approximately 583/481 ms between
click and first displayed/server movement, with no early native destination.
The user reports both NPC early starts and the subsequent staging/arrival
correction work well. The latest 15:57/15:58 captures show a close one-tile
preview finishing early and waiting for authority. A narrow idle-start easing
correction passes the full **181-test** build, including six new pacing/handoff
regressions; the user reports it **works well**, but may revisit close
Bank/Exchange clicks later. The reason the
native client delays/omits the destination is not established.

The 19:43 banker/GE-clerk captures led to a five-tile arrival-easing attempt,
which the user rejected: the 2026-10-01 11:12 traces still show slowdown before
the destination. That easing is now replaced by a checked final-run-pair release
for the exact five-tile cardinal case. The full **216-test** build passes,
including normal-speed capture replays and earlier overshoot regressions;
the user subsequently confirms **this replacement worked**.
The 12:48/12:49 captures show remaining three-tile straight and offset Bank
approaches using the older one-tile startup easing. A narrow complete-run-pair
extension passes the full **227-test** build, including eleven new regressions;
this extension is **awaiting in-game confirmation**.

The subsequent area-crossing correction addresses the reported 23:51/23:52
backtracking captures and passes **149 tests** in the full build. Its movement
and camera feel are **not yet user-confirmed**; see the current validation notes.

The latest 18:42/18:43 trace shows scene-click previews drifting from the
`trueTileIndicator` route and then reversing on timeout. A targeted forward
authority-alignment correction passes the full **204-test** build, including
nine new route/controller regressions. The user subsequently reports it
**worked really well** for scene clicks. The minimap parity extension passes the
full **237-test** build, including ten new controller regressions, and awaits
its own in-game confirmation; see [VALIDATION.md](VALIDATION.md).

The latest GE backtracking/zig-zag report supplied **57 Bank approaches** with
complete collision context. A geometry-based one-sided counter correction now
passes their route/constant-pace replays and the **310-test** full build; the user
has confirmed it works in-game for their tested situations. See the [counter checkpoint](agent-work/ge-counter-2314/CHECKPOINT.md)
and [VALIDATION.md](VALIDATION.md). Tracing retains the new click-time collision
and route snapshots described in [TRACE-FORMAT.md](TRACE-FORMAT.md).

The subsequent **2026-10-03 18:20–18:22 (+10:00)** red-click captures expose moving
Walk previews outliving the NPC observation window and ordinary NPCs inheriting
the banker's two-tile staging policy. A fractional, authority-anchored NPC handoff
and Bank/Exchange-only extended reserve now pass all ten new capture cases at
three frame cadences. The full **325-test** build passes, including the earlier
57 counter replays and five policy/deadline/connector guards. This latest moving
handoff and ordinary-NPC correction was subsequently reported **good** by the user.

The two newer **19:01–19:02 / 19:05 (+10:00)** sessions contain 32 ordinary NPC
approaches. Late preceding-Walk flags/ticks, diagonal-ring staging and an already
adjacent authority position explain the remaining stops and arrival retraces.
The ordinary-NPC continuity follow-up passes all 32 replays at three cadences and
six new geometry/timeout/evidence guards. The full **363-test** build passes; the
user subsequently reports this continuity follow-up was **good**. Bank/Exchange
retain their separate extended-range/counter policies.

The latest **19:45–19:46 (+10:00)** capture contains twelve banker Talk-to starts
from world tiles **(3157,3487)** and **(3157,3488)**. Three reproduce a preview
overshoot before the final authoritative stop. Talk-to can now share the existing
one-sided counter policy when native Bank/Exchange service actions and the checked
counter geometry both qualify. The full **381-test** build passes, including
twelve constant-pace capture replays and six scope/lifetime guards. The user
subsequently confirms this extension **worked well**.

The **20:34–20:35 (+10:00)** combat session records repeated Guard Attack starts,
with target invalidation and two-tile melee staging contributing to extra pauses.
The new adjacent-melee target/engagement handoff passes **396 tests**, including
twelve follow/cancellation/budget guards and three recorded timing/collision/action
replays with explicitly synthetic moving-target evidence. The old logs do not
record live NPC movement or native engagement each frame. New samples retain
refreshed combat target bounds plus `combatPhase` / `combatEngaged`. The user
subsequently reports this initial combat change **is working well**.

The recording beginning **22:23:59 (+10:00)** contains five Guard Attack cases
at 22:24. The moving-target cases finish their fast follow legs early and wait
for later steps/hits. The latest persistent gaze and slower pursuit correction
passes all five timeline replays at three cadences and eight ownership/pacing
guards. The full **409-test** build passes; the user subsequently confirms this
refinement **worked well**. New diagnostics include `combatLocked`, the drawn facing target,
accepted owned-hit evidence and normal/trailing pacing.

### Code map and development boundaries

`ResponsiveMovementPlugin` owns RuneLite event/listener lifecycle;
`MovementController` coordinates clicks, authority and presentation;
`MovementInput` observes native destinations; `MovementPath` owns **all** custom
position/route state; `MovementRoute` searches bounded blocked paths;
`ObjectApproach` reads a clicked object's footprint once; `InteractionTarget`
retains ordinary object/ground-item click identity; `NpcApproach` captures
one clicked NPC and supplies a checked provisional prefix goal. `MovementFacing` caps
turning; `CombatApproach` captures the native weapon/style profile;
`FollowPresentation` retains explicit Follow target/native ownership only;
`CombatContinuity` retains only clicked-target and native-engagement evidence;
`PlayerPresentation` manages native pose selectors and
`NativeModelObject` requests native body/effect models at draw time;
`PresentationCamera` follows the body; `MovementOverheads` draws its overheads;
`RenderState` is the immutable renderer-callback snapshot; `MovementTrace`
records asynchronous diagnostics. Tests are in `src/test/java/com/responsivemovement/`.

Keep the route collision-checked and speculative movement bounded; restore
native pose/camera and registered objects/listeners on shutdown. Render callbacks
can run on the map-loader thread and must consume only the published snapshot.
Use supported RuneLite APIs and do not alter or automate server-visible actions.
See [AGENTS.md](../AGENTS.md) for full repo constraints and
[ARCHITECTURE.md](ARCHITECTURE.md) before changing the movement pipeline.

## Development

Requires a JDK capable of building Java 11 code.
`build.gradle` targets RuneLite **1.13.0** for both compilation and the development
client. It uses `RuneLiteObjectController.getModel()` to supply native posed
models when drawn, replacing the removed `mergeModels(Model...)` calls.
Temporary actor meshes are not cached across frames or animated a second time.
If preparation cannot obtain a body model, the native player remains visible.

```powershell
.\gradlew.bat build
.\gradlew.bat run
```

Log into the development client using RuneLite's
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
instructions. Enable **Responsive Movement** and a supported GPU renderer.
The plugin declares a conflict with **True Tile Movement** because both own the
local player's presentation.

Only the user can verify behavior in RuneScape; do not automate gameplay input.
For a new report, enable **Movement → Record movement trace**, reproduce the
behavior, turn recording off to flush, and give the local timestamp with UTC
offset and what happened. Current and rotating archives live in
`%USERPROFILE%\.runelite\responsive-movement\`; logs record displayed,
authoritative, native and `trueTileIndicator` positions, plus click/decision
fields. New recordings also include bounded **click-time collision snapshots**
and planned-route snapshots; older recordings have no collision maps.
`trueTileIndicator` matches the True Tile Player
Indicators plugin's local-player tile calculation. See [VALIDATION.md](VALIDATION.md)
for investigation details.

## Attribution

Derived in part from the BSD-2-Clause True Tile Movement Animations project by
Jacob Richard Nelson / Posiedien, with contributors credited there including
MK677 and JarateKing, and the subsequent responsive-movement development work.
The original copyright notice is retained in [LICENSE](../LICENSE).

This project has its own package, config group, build, and plugin metadata.
