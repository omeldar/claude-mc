<div align="center">

<img src="src/main/resources/assets/claudemc/icon.png" width="96" alt="ClaudeMC">

# ClaudeMC

**Claude Code, inside Minecraft.**

Ask in chat. Get an answer with the mark, not a player head.
Click the commands you want to run.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-62B47A?style=flat-square)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0B4?style=flat-square)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-21+-F89820?style=flat-square)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-D97757?style=flat-square)](LICENSE)

</div>

---

## What it is

ClaudeMC runs the **Claude Code CLI you already have installed** and pipes it into the game.
There is no API key, no account to connect and no per-prompt billing on top of what Claude Code
already costs you — the mod simply drives `claude` as a subprocess and keeps the session alive
between prompts, so it remembers your build.

```
/claude a dark floor for a 56-radius pit, gold inlay, nothing that burns my eyes
```

```
✳ Deepslate tiles with smooth basalt, gold only in the inlay lines. Oxidized
✳ copper bulbs are light level 4 — the dimmest glow block in the game.
✳
✳ 4 commands — dark radial floor, r=56
✳   1. //limit -1                                        [Run]  [Copy]
✳        lift the block cap first
✳   2. //cyl deepslate_tiles,smooth_basalt 56 1          [Run]  [Copy]
✳        base disc at your feet
✳   3. //hcyl gilded_blackstone 48 1                     [Run]  [Copy]
✳   4. //hcyl gilded_blackstone 32 1                     [Run]  [Copy]
✳ [Run all]  [Open menu]
```

Nothing runs until you click.

## Features

| | |
|---|---|
| **Chat-native** | `/claude <prompt>` or `/ai <prompt>`. Replies are tagged with the Claude mark via a bitmap font, so they never look like another player talking. |
| **Click to run** | Every proposed command is a clickable `[Run]` in chat, with `[Copy]` beside it and a full approval menu one click away. |
| **Sessions that remember** | The conversation persists across prompts *and across game restarts*. `/claude new` starts fresh. |
| **Live progress** | Replies stream, so you see each step — *"using scan_area"*, *"checking the floor"* — above the hotbar instead of a frozen wait. |
| **Editable commands** | Every command in the approval menu is a text field. Nudge a radius or swap a block before you run it. |
| **It can see** | `/claude shot <prompt>` attaches your latest screenshot. `/claude scan <r> <prompt>` attaches a real block scan of the area — histogram plus per-layer ASCII maps. |
| **It knows where you are** | Position, facing, dimension, biome, light level, the block you're looking at and your installed mods are refreshed before every single message. |
| **Build memory** | Claude keeps `notes.md` in its workspace: coordinates, palettes, what each room is for. It survives sessions. |
| **Mod-aware** | Sees which build tools you actually have — WorldEdit, Litematica, Axiom — and only suggests what's installed. |
| **Direct world access** | Claude gets real MCP tools into the running client: read any block, scan a region, find where a block type occurs — and, in creative with cheats on, build straight from your description. |
| **Safe by construction** | On survival worlds and servers without permission it is told *not to propose commands at all*, and refuses to run them if it does. |

## Safety model

This is the part worth reading twice.

- **Every command runs as you.** It goes out as a normal chat command under your own name and
  your own permissions. There is no elevation, no server-side component, nothing that can do
  something you couldn't have typed yourself.
- **Nothing runs unprompted.** Commands are queued only when you click.
- **Permission is checked before Claude is even asked.** The mod reads the command tree the
  server sent you. A survival world without cheats gets a minimal tree, so ClaudeMC knows it
  is read-only, tells Claude *not to propose commands*, and answers with advice instead.
- **Singleplayer survival still works** — pause → **Open to LAN** → *Allow Cheats: ON* →
  **Start LAN World**, and commands become available exactly as they would for you.
- **Hard deny list.** `/op`, `/ban`, `/kick`, `/whitelist`, `/stop` and friends are refused in
  the queue no matter what comes back. Editable in the config.
- **Rate limited.** Commands go out a few ticks apart so server anti-spam never kicks you.

## Install

1. Install [Claude Code](https://claude.com/claude-code) and sign in — `claude --version` must work.
2. Install [Fabric Loader](https://fabricmc.net/use/) for **1.21.11** and
   [Fabric API](https://modrinth.com/mod/fabric-api).
3. Drop `claudemc-*.jar` from [Releases](../../releases) into your `mods/` folder.
4. Launch, join a world, and type `/claude hello`.

WorldEdit is optional but strongly recommended — most useful answers are WorldEdit commands.

## Commands

| Command | What it does |
|---|---|
| `/claude <prompt>` | Ask, keeping the current session |
| `/claude new <prompt>` | Start a fresh session |
| `/claude shot <prompt>` | Attach your newest screenshot (press <kbd>F2</kbd> first) |
| `/claude scan <r> <prompt>` | Attach a block scan of radius `r` (1–64) |
| `/claude shot scan <r> <prompt>` | Attach **both** — either order works |
| `/claude model <name>` | `opus`, `sonnet`, `haiku`, `fable`, or a full model id |
| `/claude effort <level>` | `low`, `medium`, `high`, `default` |
| `/claude open` | The prompt screen, with attachment toggles |
| `/claude last` | Reopen the approval menu |
| `/claude undo` | Queue `//undo` |
| `/claude cancel` | Drop anything still queued |
| `/claude status` | Session, model, permissions, bridge |

`/ai` is a shorthand for all of them.

## Configuration

`config/claudemc.json`, created on first launch:

```jsonc
{
  "claudePath": "",            // empty = auto-detect
  "model": "opus",             // opus | sonnet | haiku | fable | full model name
  "effort": "",                // low | medium | high
  "timeoutSeconds": 240,
  "streaming": true,           // show progress as the reply arrives
  "commandDelayTicks": 5,      // gap between queued commands
  "bridgeEnabled": true,
  "bridgePort": 25599,
  "defaultScanRadius": 24,
  "maxCommands": 24,
  "blockedPrefixes": ["/stop", "/op", "/deop", "/ban", "/kick", "/whitelist"]
}
```

## How it works

```
  Minecraft client
        │
        │  /claude <prompt>
        ▼
  ┌──────────────┐   writes    ┌──────────────────────────┐
  │  ClaudeMC │ ──────────► │  <gameDir>/claudemc/  │
  │     mod      │             │   CLAUDE.md              │
  └──────┬───────┘             │   context.json           │
         │ spawns              │   notes.md               │
         │                     │   shots/  scans/         │
         ▼                     └──────────────────────────┘
  claude -p --output-format json --resume <session>
         │                              ▲
         │ {"summary":…,"commands":[…]} │ reads
         ▼
  approval menu ──► click ──► command queue ──► chat, as you
```

`CLAUDE.md` is auto-discovered by Claude Code on session start, so every new session already
knows it is driving a Minecraft client before it reads your first word.

### Bridge

A loopback-only HTTP server (default `127.0.0.1:25599`) lets an **already running** Claude Code
session — one you started in a terminal yourself — read the game state and push proposals into
the approval menu. The token lives in `claudemc/bridge.json`.

```bash
curl -s -H "X-ClaudeMC-Token: $TOKEN" http://127.0.0.1:25599/context
curl -s -X POST -H "X-ClaudeMC-Token: $TOKEN" \
  -d '{"summary":"test","commands":[{"cmd":"//limit -1","note":"lift the cap"}]}' \
  http://127.0.0.1:25599/propose
```

## Building

```bash
git clone https://github.com/omeldar/claude-mc.git
cd claude-mc
./gradlew build          # jar lands in build/libs/
python tools/make_icons.py   # regenerate the mark and mod icon
```

Requires JDK 21+ — Gradle provisions a matching toolchain automatically.

## Direct world access

Every session the mod starts is handed an MCP server pointing back at your client, so Claude
can look at the world instead of guessing:

| Tool | What it does |
|---|---|
| `get_context` | Live position, facing, biome, look target, installed mods, `can_build` |
| `get_block` | One exact coordinate with its full block state |
| `scan_area` | Cylinder scan: block histogram plus ASCII maps per layer |
| `find_blocks` | Where a block type occurs nearby, with a bounding box |
| `run_command` · `run_commands` | Build directly — **creative + command permission only** |
| `say` | Narrate progress into your chat during a long build |

Reads always work. Writes are refused outside creative, refused without command permission,
still filtered by the deny list, still spaced out by the queue, and still stoppable with
`/claude cancel`. The refusal tells Claude *why*, so it falls back to proposing commands for
you to approve instead of retrying.

## Keybinds

Rebindable in **Options → Controls → ClaudeMC**:

| Default | Action |
|---|---|
| <kbd>H</kbd> | Open the prompt screen |
| <kbd>U</kbd> | Reopen the last proposal |
| *unbound* | Undo last WorldEdit |

Defaults dodge the popular build mods as well as vanilla: <kbd>C</kbd> is Zoomify's zoom, and
<kbd>V</kbd>, <kbd>M</kbd> and <kbd>I</kbd> belong to Litematica. Anything behind <kbd>F3</kbd>
doesn't count, since those need the modifier held.

## Settings

Everything is adjustable in game, no file editing:

- **Mod Menu** — the cog next to ClaudeMC in the Mods tab
- `/claude settings` — the same screen
- `/claude model <name>` and `/claude effort <level>` — quick switches

Model, effort, live progress, the gap between queued commands, max commands per reply, default
scan radius, the world-tools port and the path to `claude` all live there, plus a
**Key binds...** button straight into vanilla's rebinding screen.

The prompt screen is the comfortable way in: a text box, independent **Screenshot** and
**Scan** toggles that combine freely, a session keep/new switch, and model + effort cyclers.
Your choices stick between opens, and an unsent draft survives closing the screen.

## Roadmap

- [ ] Per-world notes instead of one shared `notes.md`
- [ ] Warn in chat when a ClaudeMC key collides with another mod's
- [ ] Render the proposal as a HUD list without opening a screen
- [ ] Litematica schematic generation from a description

## License

MIT — see [LICENSE](LICENSE).

Not affiliated with Mojang or Microsoft. Claude and Claude Code are products of Anthropic.
