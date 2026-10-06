# Responsive Movement development

- Author/owner: GimmitheBong. Preserve the BSD attribution for derived code.
- Java 11, supported RuneLite APIs only. No reflection, injected input, native
  memory access, external processes, runtime-generated/downloaded code, or
  changes to the player's server-visible route.
- One MovementPath pipeline owns all custom movement. Preserve the tested
  straight-first logical route, collision-checked knight chords, bounded
  anticipation, visible-idle starts, and frame-rate-independent pacing.
- Body geometry comes from the native player builder. Do not attach another
  animation controller to an already posed mesh or reintroduce plugin-selected
  leaps, Woox-walk jumps, or tick-perfect animation combos.
- RuneLite 1.13.0 body/effect models use NativeModelObject's client-thread-only
  draw-time provider. Do not cache shared posed meshes or restore removed
  mergeModels(Model...) calls. See docs/RUNELITE-1.13.md for the model lifetime.
- Camera mode 1 is presentation-only. Restore native camera mode after draw and
  before input/menu processing. Respect native zoom/follow-height calculations.
- Render callbacks may execute on the map-loader thread: consume only the
  published render snapshot there, never live controller/model state.
- Do not scan the scene per frame. No blocking I/O on the client thread, sleeps,
  or shutdown waits. Diagnostic logging uses DEBUG and writes asynchronously
  through getPluginDirectory() and RuneLite Filepath inside
  .runelite/plugin-data/responsive-movement. Keep legacyDataDirectory migration
  for the former responsive-movement folder; resolve it only on the writer.
- Keep cleanup symmetric: restore actor selectors/camera and remove objects,
  overlays, mouse listeners, draw listeners, and render callbacks on shutdown.
- New config keys use the responsive-movement group. Never rename persisted
  keys/groups without migration. Third-party-server features must be opt-in and
  carry RuneLite's required IP-address warning.
- Use apply_patch for edits. Do not commit build output or unrelated changes.
- Only the user can verify behavior in RuneScape. Never automate game input.
  Offer ./gradlew run, link the Using Jagex Accounts development-login guide,
  specify the changed behaviors to test, and await in-game confirmation.
