# Responsive Movement

A RuneLite plugin by **PHYSIQUE-sys** that smooths the **local player's displayed
movement** and reacts to observed scene and minimap clicks. Walking, running,
turning, and interaction approaches use native player animations at the displayed
position.

The game still decides your actual location, route, speed, attacks, and actions.
The plugin observes normal input; it does not send actions or move your character
on the server. An early visual preview can be corrected when server movement
differs. **Movement speed** changes presentation pacing, not gameplay speed.

## Features

- Responsive starts and direction changes for scene Walk, right-click Walk here,
  minimap clicks, and supported object, item, and NPC interactions.
- Collision-checked, time- and distance-bounded previews that reconcile to actual
  movement.
- Smooth turning and native equipment, action animations, and spot effects.
- Optional adaptive camera following the displayed player, with native zoom and
  input handling.
- Prayer icons, skulls, text, health bars, and hitsplats at the displayed position.
- Native presentation handoff for explicit player/NPC Follow.

## Requirements and setup

- Use RuneLite with **GPU** or **117 HD** enabled. Unsupported rendering falls
  back to the native player and displays a renderer requirement message.
- Disable **True Tile Movement**: both plugins manage local-player presentation,
  and Responsive Movement declares a conflict with it.
- Enable **Responsive Movement** in the plugin configuration panel.

This repository is being prepared for Plugin Hub submission; it is not published
by this preparation work. Use the development instructions below to try it.

## Main settings

| Setting | Default | What it controls |
| --- | --- | --- |
| Responsive starts | On | Early visual previews from observed clicks. |
| Movement speed multiplier | 5.0 | Displayed movement pace; 5.0 is normal, range 0.5–10.0. |
| Turning speed | 30 | Maximum custom turning rate, scaled by frame time. |
| Walk-click smoothing (ms) | 50 | Wait up to 0–300 ms from click observation; the next game tick ends a nonzero wait early. 0 disables the wait. |
| Face interactions on arrival | Off | Begin smoothly turning toward the clicked NPC/object as soon as displayed movement arrives, using Turning speed. |
| Original player when aligned | On | Native rendering when stationary position, pose, and facing agree. |
| Adaptive camera | On | Follow displayed movement during drawing; native camera mode is restored before input. |
| Custom overhead rendering | On | Draw local-player overheads at the displayed position. |
| Show native-position camera marker | Off | Fixed orb at the hidden native position while it differs from the displayed position. |
| Record movement trace | Off | Local rotating diagnostic logs for investigating movement issues. |

## Limitations and validation

Displayed position can briefly lead or trail the actual tile. Use a true-tile
indicator when you need to see the authoritative location. Unsupported movement,
teleports, unavailable models, and scene changes can use native presentation.
The plugin does not remove the native client's loading stall.

The user confirms the prepared plugin is working as expected in their in-game
testing with **117 HD**. Automated regressions and user reports cover many
movement situations,
but do not establish every interaction or renderer combination. See
[validation notes](docs/VALIDATION.md) for confirmation scope and useful regression
checks, including gates, region crossings, minimap input, and interaction approaches.
The user reports substantially smoother circles and improved repeated knights.
The newest marked-pause handoff follow-up passes regressions and awaits in-game
confirmation; see the current validation status above those checks.

## Development

Requires a JDK capable of compiling Java 11 code. The build follows RuneLite's
`latest.release`; use `-PruneLiteVersion=1.13.1` to reproduce the publication audit
against the Plugin Hub version checked on 2026-10-05.

```sh
./gradlew build
./gradlew run
```

On Windows:

```powershell
.\gradlew.bat build
.\gradlew.bat run
```

For development-client login, follow RuneLite's
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
guide. Only the user performs in-game checks; do not automate gameplay input.

### Reporting a movement issue

Enable **Movement → Record movement trace**, reproduce the issue, then turn it
off to flush the pending samples. Report what happened, renderer, settings, and
local timestamp with UTC offset. Logs stay on your computer in
`%USERPROFILE%\.runelite\plugin-data\responsive-movement\`
(or `~/.runelite/plugin-data/responsive-movement/`). RuneLite migrates the former
`.runelite/responsive-movement` folder on first recorded write when the managed
folder does not yet exist. Directory resolution and migration run on the
asynchronous writer, not the client thread.
The writer retains about 64 MiB across eight rotating files. Logs contain
positions, world/collision context, clicked NPC information, and movement
decisions; nothing is uploaded automatically.

## Maintainer documentation

- [Architecture and code map](docs/ARCHITECTURE.md)
- [Publication review and remaining steps](docs/PUBLISHING.md)
- [In-game validation and confirmation history](docs/VALIDATION.md)
- [Native model lifetime](docs/RUNELITE-1.13.md)
- [Trace format](docs/TRACE-FORMAT.md)
- [Archived development README](docs/DEVELOPMENT-HISTORY.md)

## License and attribution

BSD-2-Clause. Derived in part from the True Tile Movement Animations project by
Jacob Richard Nelson / Posiedien, whose contributors include MK677 and JarateKing.
The original copyright attribution and PHYSIQUE-sys's attribution (formerly GimmitheBong) are retained in
[LICENSE](LICENSE), also bundled as `META-INF/LICENSE` in the plugin JAR.
