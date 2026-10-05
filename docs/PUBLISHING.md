# Plugin Hub preparation review

Reviewed on **2026-10-05**. This is a local source/build review, not RuneLite
approval or a publication. The Plugin Hub's `runelite.version` was **1.13.1**.

## Official requirements checked

- [Plugin Hub contribution and submission instructions](https://github.com/runelite/plugin-hub#readme):
  public source repository, Java 11-compatible build, plugin metadata, permissive
  license, and submission of a repository URL plus a full 40-character commit hash.
  `build=standard` allows RuneLite to replace this project's build/settings files.
  An optional root `icon.png` must be no larger than 48×72 px; no icon is required.
- [Plugin Hub review scope](https://github.com/runelite/runelite/wiki/Plugin-Hub-Review):
  review focuses on security and game rules. Acceptance does not establish
  correct behavior, performance, or compatibility with every other plugin.
- [Rejected or rolled-back features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features):
  Java only; no production reflection, native code, external programs, or runtime
  downloaded/generated code. Also checked the prohibited input, combat, PvP,
  menu, interface, camera, and data-sharing features.
- [Jagex third-party client guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1):
  no automated gameplay, prohibited combat advice, additional server-action
  menu entries, resized click zones, or world interaction in detached camera mode.
- [RuneLite's current plugin development guidance](https://github.com/runelite/plugin-hub-tooling/blob/master/templateplugin/AGENTS.md)
  and [disallowed API list](https://github.com/runelite/plugin-hub-tooling/blob/master/package/src/main/resources/net/runelite/pluginhub/packager/disallowed-apis.txt):
  client-thread rules, asynchronous I/O, scoped Filepath access, cleanup, config
  stability, supported API constants, and packaging.

## Findings and preparation changes

| Area | Finding / result |
| --- | --- |
| Build | Java 11 remains the target. Default dependencies now follow `latest.release`; `-PruneLiteVersion=1.13.1` reproduces this audit. Added the official Gradle distribution SHA-256. |
| Metadata | Added `build=standard`, a stable descriptor `internalName`, space-free tags, and descriptions that explicitly identify visual-only movement. |
| Dependencies | No additional production dependency is needed beyond RuneLite client. JUnit and jshell are development/test dependencies; they are not bundled in the plugin JAR. |
| Licensing | Retained both existing BSD copyright notices. Put the full license in `src/main/resources/META-INF/LICENSE`, so it survives the Hub's replacement standard build. Removed the redundant local custom JAR-copy step. Keep the root and bundled licenses synchronized. |
| Arbitrary IDs | Removed hidden `markerMovingModel` / `markerStationaryModel` config accessors. The optional marker now uses only its original fixed orb (3351) and hides at alignment. Old stored model-ID values are unused; other persisted keys/groups are unchanged. |
| Cleanup | Corrected GPU-loss cleanup ordering; renderer fallback closes body/effect/camera objects and clears overhead state. Shutdown captures retiring controllers, avoiding closing a later startup's controllers. The draw listener ignores inactive state. Expired effects and disabled/aligned markers unregister rather than remaining invisible registered objects. |
| Diagnostics | Recording remains opt-in, asynchronous, bounded, and local. Converted filesystem operations to RuneLite Filepath. No network feature or third-party-server config was found. |
| Documentation | Replaced the investigation-heavy root README with user setup, features, defaults, limitations, and diagnostics instructions. Preserved the previous development history and regression fixtures. |

### Filepath and the existing diagnostic folder

Repository guidance explicitly requires retaining
`RuneLite.RUNELITE_DIR/responsive-movement`. The writer therefore obtains a
constrained `Filepath` through `Filepath.Unchecked.getLegacyPluginDirectory`,
only for that fixed directory, on the asynchronous writer. It does not use raw
`Files` operations or accept a user-supplied path.

RuneLite notes that `Filepath.Unchecked` access prevents automatic review; disclose
this single legacy-directory access to reviewers. If they require the preferred
`getPluginDirectory()` location, update repository guidance and migrate with
`legacyDataDirectory="responsive-movement"`. That would move diagnostics to
`.runelite/plugin-data/responsive-movement/`; it is not a silent folder rename.

### Behavior reviewers should understand

- `MovementPath` changes only custom presentation coordinates. No player route,
  destination setter, menu injection, or game-input generation is present.
- Mouse/menu handlers observe normal input without consuming it or generating
  server actions. Local diagnostic chat messages are not outgoing player chat.
- Combat handling retains a manually clicked target and observed native movement,
  engagement, poses, effects, and hits. It does not provide attack timers,
  projectile landing markers, prayer advice, or boss standing recommendations.
- The replacement body uses the native player builder at draw time; posed meshes
  are not retained or animated again. Renderer filters use a published snapshot;
  model providers reject worker-thread access.
- Camera mode 1 is used during drawing only, and restored by the draw listener
  and input boundaries. Native menu/destination processing remains authoritative.
  This is the important distinction reviewers must assess against Jagex's
  detached-camera interaction rule. In-game menu/minimap accuracy needs checking.
- Test sources use reflective API doubles. They stay under `src/test` and are
  excluded from the production JAR.

No definite prohibited gameplay feature was identified in this review. RuneLite
must still judge the custom presentation and camera behavior; a successful local
build is not an approval guarantee.

## Verification

Results on 2026-10-05:

- `build` with the default `latest.release` target succeeded: **532 tests, zero
  failures/errors/skips**, including three new effect/marker/GPU-fallback checks.
- Dependency inspection confirmed `client`, `runelite-api`, `injected-client`,
  and `jshell` at **1.13.1**; the fixed-version build also succeeded.
- An isolated temporary build using the Hub's standard build dependencies and
  Java settings compiled this project's production sources successfully. JAR
  assertions verified Java 11 bytecode, **35 PNGs**, the complete root-matching
  BSD license, and absence of launcher/test/fixture/dependency artifacts or a
  plugin service-provider file. This was a local compatibility simulation, not
  the Hub's full remote CI/security review.

The generated test report is `build/reports/tests/test/index.html`.

Run from the repository root:

```powershell
.\gradlew.bat build "-PruneLiteVersion=1.13.1" --console=plain --no-daemon
.\gradlew.bat dependencyInsight --dependency net.runelite --configuration testRuntimeClasspath "-PruneLiteVersion=1.13.1" --console=plain
```

The `jar` task should include production classes, overhead PNGs, and
`META-INF/LICENSE`; it must exclude the development launcher, test doubles,
capture fixtures, and dependency JARs. A standard-build compatibility check
should compile `src/main` against the Hub's RuneLite version without relying on
this repository's custom build tasks.

## In-game confirmation before submission

The user subsequently reports **"everything seems to be working how it should"**
for their in-game testing, and supplied another successful
`.\gradlew.bat build --console=plain --no-daemon` result with all tasks up-to-date.
The user identifies **117 HD** as the renderer used. This is the latest general
runtime confirmation; settings and scenario-by-scenario coverage were not specified,
and GPU has not been separately confirmed in this report. The following checklist
remains useful for regression testing and additional renderer checks.

Launch `./gradlew run` (Windows: `.\gradlew.bat run`) with GPU or 117 HD, following
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).
Only the user performs gameplay. Check:

1. Walking/running, scene/minimap replacements, turning, ordinary NPC/object/item
   interactions, explicit Follow, and combat escape still look correct.
2. Switch GPU off/on; native body/overheads return without stale custom objects.
   Disable/re-enable Responsive Movement and log out/in with the same expectation.
3. Toggle the camera marker, reach native/displayed alignment, teleport, and cross
   a scene boundary: the fixed orb disappears cleanly and never duplicates.
4. Native spot effects and overheads render correctly after repeated combat/effects
   and plugin toggles; check Animation Smoothing on/off and both supported renderers.
5. Adaptive camera, right-click menus, scene clicks, minimap conversion, and zoom
   retain correct native input behavior, including with the camera option disabled.
6. Record/stop a trace and confirm new lines flush in the existing diagnostic folder.
7. Complete the still-pending door/gate, region-crossing, minimap, and interaction
   checks in [VALIDATION.md](VALIDATION.md).

## Later submission steps (not performed)

At the initial review, the local repository had no configured Git remote and the
project files were untracked. A submission needs a public GitHub source
repository with the intended files committed. Keep `.gradle/`, `build/`, local
logs, and login/account data out of the source repository.

Once runtime checks are confirmed, recheck the current Hub version and rules,
then use RuneLite's submission instructions to add a marker under
`plugin-hub/plugins/responsive-movement` with the public HTTPS repository URL
and exact full commit hash. Include a concise visual-only behavior description,
retained source attribution, renderer requirements, and the legacy Filepath
access in the eventual review description. The icon is optional.

The initial preparation created no commit, push, release, Plugin Hub marker, or
pull request. The user has now authorized recording validation, creating a public
source repository, and committing/pushing the prepared project. Plugin Hub submission
is a separate later step.
