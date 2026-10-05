# RuneLite 1.13.0 model-provider migration

Publication preparation (2026-10-05) also builds against the Hub's **1.13.1**.
The default Gradle target now follows `latest.release`, with an optional
`-PruneLiteVersion=1.13.1` override. The model lifetime contract below is retained;
the pinned 1.13.0 references describe the original migration checkpoint.

Status: **the user reports that the RuneLite 1.13.0 fix worked when running the
plugin**; the build and **139 tests** also pass against 1.13.0. This supersedes
the short-lived 1.12.39 dependency-pin workaround. The user's report confirms
the reported run failure was resolved, not that every rendering edge case listed
below was separately exercised. The previously confirmed movement, turning and
routing behavior remains the baseline.

## Why the previous build failed

RuneLite 1.13.0 removed `Client.mergeModels(Model...)` and its array/length
overload. `mergeModels(ModelData...)` still exists, but a native player's posed,
lit `Model` is not `ModelData`. Pinning the old client only hid that incompatibility.
The Gradle target at that migration checkpoint was explicitly 1.13.0, including
the test/development client and jshell dependencies.

## Supported replacement and lifetime

[RuneLiteObjectController.getModel() in 1.13.0](https://github.com/runelite/runelite/blob/runelite-parent-1.13.0/runelite-api/src/main/java/net/runelite/api/RuneLiteObjectController.java)
explicitly permits returning a shared model for drawing. The corresponding
[GPU temporary-object draw pass](https://github.com/runelite/runelite/blob/runelite-parent-1.13.0/runelite-client/src/main/java/net/runelite/client/plugins/gpu/GpuPlugin.java)
uploads temporary model geometry in `drawTemp`.

`NativeModelObject` implements that model-provider contract:

- During preparation, probe the native renderable for availability and discard
  the returned mesh. Publish only the selected source and its visibility.
- During the client-thread temporary-object model request, call that source's
  native builder and immediately return the posed model. A body and a spot effect
  may reuse scratch geometry without the plugin retaining one builder's result
  for the other's later draw.
- Do not apply another animation, transform or relighting operation to that mesh.
  Native equipment, action, pose and appearance handling remain in the player builder.
- Reject off-thread model requests before accessing the source. The plugin's
  separate renderer filtering callbacks continue to use `RenderState` snapshots.
- Hide source references during native handoff; clear and unregister them at
  scene invalidation/shutdown. World-view relocation re-registers the object.
- The camera marker is a static cache model loaded through `Client.loadModel`,
  assigned directly to an unanimated `RuneLiteObject` and never mesh-transformed.

There is one availability probe per preparation and a fresh native model request
each time the visible custom object is drawn. A missing preparation model or
unusable pose frame selects native presentation. The former retained detached
last-mesh fallback is no longer available: a shared actor mesh cannot safely be
kept for a later frame. A source that disappears after a successful probe can
return null at draw time, which is an explicit runtime validation case.

### Primary actions and displayed gait — 21:17 continuity follow-up

PlayerPresentation now keeps secondary native locomotion selectors aligned with
displayed gait during primary attack/cast/block actions and spot effects. It does
not set/restart the primary action or its frame, and no posed model is animated again.
For anticipated/catch-up motion while the native actor is stationary, its tick idle
selector matches gait to preserve native pose-clock progression. The native builder
omits an idle secondary sequence during a primary action, so `NativeModelObject.build`
temporarily presents the saved real idle selector at the **availability probe and
each client-thread draw-time native getModel call**, restoring the tick selector in
`finally`. Only that supported selector is scoped; no mesh is retained or transformed.
Hide/unavailable preparation/shutdown clear the builder selector alongside the source.
Worker requests return null before reading or modifying any actor selector.

Three provider tests cover draw/probe restoration, worker rejection, builder failure,
draw-time disappearance and clearing before later unscoped sources. The full
**491-test** build passes with the movement and primary-frame/secondary-clock guards.
These API-double checks do not exercise the native renderer's bone masks; this newest
continuity change is now user-confirmed working well for the tested situations.
The report does not separately establish every weapon mask, Animation Smoothing or
renderer combination. The earlier 1.13 compatibility confirmation remains the baseline.

## Verification and ongoing visual checks

`NativeModelObjectTest` covers interleaved body/effect scratch-model reuse,
unavailable sources, draw-time disappearance, worker-thread rejection, aligned
handoff, shutdown clearing and world-view re-registration. Those API-double tests
do not execute the game's native renderer. Dependency resolution must show
`client`, `runelite-api`, `injected-client` and `jshell` at the same selected
RuneLite version on the development runtime classpath.

Verification on 2026-09-29:

- `dependencyInsight --dependency net.runelite --configuration testRuntimeClasspath`
  confirmed those four artifacts at **1.13.0**.
- `.\gradlew.bat build --offline --console=plain --no-daemon` succeeded; fresh
  XML at 08:58:12Z reports **139 tests, zero failures/errors/skips**, including
  the six model-provider tests and all 133 existing regressions.
- The initial offline test attempt needed the uncached 1.13.0 jshell dependency;
  the online targeted run fetched it and passed, then the complete offline build
  above passed. No old-version dependency workaround remains.
- After the build fix, the user reported **“that fixed it”** when running the
  plugin. This is runtime confirmation of the issue they reported; no separate
  confirmation was supplied for every spot-effect, camera-marker, lifecycle or
  renderer scenario below.

Launch `.\gradlew.bat run` with GPU or 117 HD. Use RuneLite's
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
development-login guide. Check:

1. Idle, walking, running and rapid direction changes: correct body, equipment
   and smooth native pose; no body/effect mesh mix-up or intermittent blank frame.
2. An action with a spot effect, ideally near other actors/effects: each model
   remains correct at the displayed player's location.
3. Toggle Animation Smoothing and Original player when aligned; watch short
   stop/start arrivals for flicker or unexpected native-position handoff.
4. Enable the optional camera marker; verify its model and following behavior.
5. Cross a region boundary, teleport, and disable/re-enable the plugin: no stale
   objects, duplicate body or selectors left overridden.

Only the user performs gameplay. If a visual problem occurs, record which
renderer was used and what happened. The successful build plus the user's run
confirmation establish the resolved compatibility issue; the checklist above
remains useful for spotting any presentation regressions.
