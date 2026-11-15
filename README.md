# Lithium (Litematica Auto Printer)

Client-side Fabric mod for **Minecraft 26.1.2**. It builds the schematic you loaded with
**Litematica** by placing blocks through normal, vanilla block interactions (the same
right-click a player makes). It never fakes positions, rotations, inventory or results.

## Requirements (install in your `mods` folder)
- Fabric Loader 0.19.3 or newer
- Fabric API 0.155.0+26.1.2 or newer
- MaLiLib 0.28.8 or newer (26.1.2 build)
- Litematica 0.27.9 or newer (26.1.2 build)
- Java 25

## Keys (rebind in Options > Controls > Key Binds)
| Key | Action |
|---|---|
| P | Toggle printer |
| O | Pause / resume |
| K | Open settings |
| L | Stop printing |

## How to use
1. Load a schematic in Litematica and place it in the world.
2. Put the blocks you need into your **hotbar** (the printer never moves items).
3. Stand within reach and press **P**. Walk along the build as it progresses.

## Notes
- Settings are saved in `config/litematica_auto_printer.json`.
- Orientation-sensitive blocks (stairs, slabs, furnaces...) are only placed when vanilla's own
  placement logic gives the exact state from a reachable click; others are skipped until you move.
- Servers with strict anti-cheat may still flag any automated building. Check server rules.

## Building
Push to GitHub. The **Build** workflow compiles with Java 25 and uploads the JAR as an artifact
(`lithium-*.jar`). Pushing a tag like `v0.1.0` also attaches the JAR to a GitHub Release.
