# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

This repo is a **Gradle composite build** of independent per-MC-version mod projects plus one shared `common/` library. Each mod project still has its own `gradlew` and settings.gradle — always `cd` into the project before running Gradle.

- `common/` — **Plain Java library**, not a Minecraft mod. Holds version-independent code: the companion Swing UI (`common/companion/*`), its runtime `Config`, and the `PlayerData` / `InventoryItemData` / `StatusEffectData` DTOs serialized over TCP. Each mod project pulls this in via `includeBuild('../common')` + a `shade` dependency, and shades the result into its jar so standalone `java -jar` still works with no external library.
- `autohidehud-1.21.4/` — Mixin targets the older `GuiGraphics` methods (`fill`, `fillGradient`, `drawString`, `blit`, `blitSprite` — no `RenderPipeline` arg). `hideContextualInfoBar` config dropped (layer added in 1.21.5). Items use threshold-cancel.
- `autohidehud-1.21.6/`, `autohidehud-1.21.7/`, `autohidehud-1.21.8/` — Cloned from 1.21.8 (hybrid API: `RenderPipeline` on draw methods, no `submit*` batch API). Only `GuiGraphicsMixin` + `GuiTextRenderStateMixin` apply; `GuiRendererMixin` is dropped because its target (`submitBlitFromItemAtlas`) doesn't exist yet. Item fade uses threshold-cancel.
- `autohidehud-1.21.9/`, `autohidehud-1.21.10/` — Full newer pipeline: `submit*` batched color API + `GuiRenderer` + `GuiTextRenderState`. Smooth fade including hotbar items via `GuiRendererMixin.submitBlitFromItemAtlas`.
- `autohidehud-1.21.11/` — Same render pipeline as 1.21.10 but `ResourceLocation` class renamed to `Identifier` (Mojang adopted Fabric's naming). Source-level search-and-replace was the only delta.
- `autohidehud-26.1.wip/` — **Blocked**. `GuiGraphics` was removed entirely in 26.x (replaced by `GuiGraphicsExtractor`), the `render.state` package is gone, and Mojang bumped the runtime to Java 25. Supporting this requires a full rewrite of the mixin strategy against the new API, not an iterative port. See "26.x outstanding" section below.
- `autohidehudcompanion/` — **Superseded** standalone companion project. Replaced by `common/companion/`. Safe to delete when you're done cross-referencing.

All projects target **Java 21**.

### The merged jar (1.21.10)
The same `autohidehud-*.jar` produced by `./gradlew build` works two ways:
- **As a Minecraft mod**: NeoForge discovers it via `META-INF/neoforge.mods.toml` and ignores `Main-Class`.
- **As a standalone app**: `java -jar autohidehud-*.jar` (or double-click in Finder/Explorer) invokes `Main-Class` → launches the companion Swing overlay. Gson is shaded into the jar via the `shade` configuration so the standalone path has no external dependencies.

When extending the companion, keep it runnable without Minecraft loaded — no MC/NeoForge APIs in `com.krookedlilly.autohidehud.companion`.

## Common commands

### Mod (any version)
```bash
cd autohidehud-1.21.10    # or autohidehud-1.21.8, autohidehud-1.21.4
./gradlew build            # produces build/libs/autohidehud.jar
./gradlew runClient        # launch MC client with mod loaded
./gradlew runServer        # launch dedicated server
./gradlew clean
./gradlew --refresh-dependencies   # if IDE/libs get wedged
```
Game-test server (1.21.10 only): `./gradlew runGameTestServer`. Data gen: `./gradlew runClientData`.

### Running the companion standalone (from the merged mod jar)
```bash
cd autohidehud-1.21.10
./gradlew build
java -jar build/libs/autohidehud-*.jar
```

### Legacy companion project
```bash
cd autohidehudcompanion
./gradlew fatJar           # only needed to produce the old standalone artifact
```
Prefer the merged jar above — this path is kept as a fallback until the merge is proven in production.

No test suite is wired up in any subproject.

## Architecture

### Mod core loop (1.21.10 — see `AutoHideHUD.java`)
1. `onClientTick` increments `currentTick`; any tracked player-state change (health, armor, food, air, hotbar slot, XP) updates `lastUpdatedTick`.
2. When `currentTick - lastUpdatedTick > ticksBeforeHiding`, alpha ramps down toward `targetOpacity` at `fadeOutSpeed`.
3. `onRenderHUDPre` applies the current alpha to HUD rendering via Mixins (see below).
4. "Reveal conditions" (low health, low durability, creative mode, etc.) force alpha back to 1.0 regardless of inactivity.
5. Keybind `R` (`AutoHideHUDKeyBindings`) force-reveals instantly.

### Rendering interception strategy differs by version
- **1.21.10** uses Mixins declared in `src/main/resources/autohidehud.mixins.json`:
  - `GuiGraphicsMixin` — `@ModifyVariable` on color args in render methods
  - `GuiTextRenderStateMixin` — text alpha blending
  - `GuiRendererMixin` — generic renderer alpha

  Touching HUD rendering in 1.21.10 almost always means editing these mixins, not event handlers.
- **1.21.8** uses `GuiGraphicsMixin` + `GuiTextRenderStateMixin`. Drops `GuiRendererMixin` because its target (`submitBlitFromItemAtlas`) doesn't exist yet. GuiGraphics method signatures include a `RenderPipeline` first arg on `blit`/`fill`/`blitSprite` (unlike 1.21.4) but color is still a plain int, not a packed `submit*` call (unlike 1.21.10). Targets live in [autohidehud-1.21.8/src/main/java/com/krookedlilly/autohidehud/mixin/GuiGraphicsMixin.java](autohidehud-1.21.8/src/main/java/com/krookedlilly/autohidehud/mixin/GuiGraphicsMixin.java).
- **1.21.4** uses `GuiGraphicsMixin` only, targeting the older `GuiGraphics` methods without `RenderPipeline` (the `GuiRenderer` / `GuiTextRenderState` classes don't exist yet). Targets in [autohidehud-1.21.4/src/main/java/com/krookedlilly/autohidehud/mixin/GuiGraphicsMixin.java](autohidehud-1.21.4/src/main/java/com/krookedlilly/autohidehud/mixin/GuiGraphicsMixin.java).
- **Hotbar items** on 1.21.8 and 1.21.4 use threshold-cancel rather than smooth fade (3D item render pipeline ignores `setShaderColor`). Items pop out when alpha drops below ~0.05, after everything else has already faded near-invisible.

### Mod ↔ companion protocol
Even though they ship in one jar, mod and companion still run in **separate JVMs** (the mod runs inside Minecraft's JVM; the companion is launched separately via `java -jar`). They communicate over TCP on localhost.
- Mod starts `PlayerDataServer` (TCP, default port **25922**, configurable) on `EntityJoinLevelEvent`.
- Every 5 ticks (configurable), it Gson-serializes a `PlayerData` DTO and broadcasts to connected clients via `CopyOnWriteArrayList<Socket>`.
- Companion (`com.krookedlilly.autohidehud.common.companion.AutoHideHUDCompanion`) connects to `localhost:25922`, parses JSON with Gson, reconnects with 2s backoff. Works whether launched before or after the MC session starts.
- **`PlayerData` is defined once** in `common/` (`com.krookedlilly.autohidehud.common.model.PlayerData`) — both the mod (producer) and companion (consumer) import from there, eliminating the previous hand-mirrored duplication.
- All three versions (1.21.10, 1.21.8, 1.21.4) now ship the data server + companion. Any version's jar can be used standalone against any MC version's mod since the TCP protocol is MC-version-agnostic — keep the shared `PlayerData` shape stable.

### Config
- **Mod**: NeoForge `ModConfigSpec` in `AutoHideHUDConfig.java` (~50 options in 1.21.10, ~30 in 1.21.8, slightly fewer in 1.21.4). Sections: General / Hide Options / Reveal Conditions / Data Server.
- **Companion**: `autohidehudcompanion.properties`, created in the current working directory on first run by `companion/Config.java`. Port must match the mod's `dataServerPort`.

### Shading Gson into the mod jar
The `shade` Gradle configuration in `autohidehud-1.21.10/build.gradle` bundles Gson's classes into the root of the mod jar so the companion has Gson at standalone runtime. Minecraft already provides Gson at mod runtime, so the shaded copy is harmless there. If you add another runtime dependency needed by the companion standalone path, declare it as `shade` (not `implementation`) and it will be bundled the same way.

### 1.21.4 mod-discovery quirks (FML 6.0.18)
NeoForge 21.4 ships FML 6.0.18, which has two dev-mode gotchas that the 1.21.8/1.21.10 versions don't hit:
1. **Split output dirs reject as "not a valid mod file"**: dev plugins normally pass `build/classes/java/main` and `build/resources/main` as two separate paths, but FML 6.0.18 validates each path independently and fails on whichever one lacks `META-INF/neoforge.mods.toml`. Fix: [autohidehud-1.21.4/build.gradle](autohidehud-1.21.4/build.gradle) sets `sourceSets.main.output.resourcesDir = sourceSets.main.java.destinationDirectory` so classes and resources land in one directory.
2. **`modLoader`/`loaderVersion` required**: older NeoForge versions inferred these; 21.4 does not. The top of [autohidehud-1.21.4/src/main/resources/META-INF/neoforge.mods.toml](autohidehud-1.21.4/src/main/resources/META-INF/neoforge.mods.toml) declares `modLoader="javafml"` and `loaderVersion="[3,)"` explicitly. 1.21.8 and 1.21.10 can load without these — don't "simplify" 1.21.4 by removing them.

### 26.x outstanding
Three changes in Minecraft 26.1 block the existing porting pattern:
1. **`GuiGraphics` removed** — replaced by a `GuiGraphicsExtractor` pattern. Our entire mixin suite targets `GuiGraphics`.
2. **`net.minecraft.client.gui.render.state` package removed** — `GuiTextRenderState` / `BlitRenderState` gone, so `GuiTextRenderStateMixin` and `GuiRendererMixin` can't attach.
3. **Java 25 required** — `net.neoforged.fancymodloader:loader:11.0.5` needs JVM 25. `build.gradle` already sets `toolchain.languageVersion = 25`; Gradle auto-provisions via foojay, but dev-machine `JAVA_HOME` should be bumped if running runClient manually.

Also: userdev plugin must be 7.1.25+ to avoid an NPE in `neoFormDecompile` on 26.x.

The existing stub is at `autohidehud-26.1.wip/` with the version bumps made but compilation broken. Before restarting, we need to investigate what `GuiGraphicsExtractor` does and how HUD rendering works in 26.x.

### AppleSkin integration (1.21.10 only)
`AutoHideHUDConfig` has dedicated booleans for AppleSkin's custom GUI layer IDs (`appleskin:health_offset`, `appleskin:health_restored`, `appleskin:hunger_restored`, `appleskin:food_offset`, `appleskin:saturation_level`, `appleskin:exhaustion_level`). There's also an open-ended `additionalLayerIds` list for users to hide arbitrary modded GUI layers. When adding support for a new compatibility target, follow this pattern rather than special-casing per-mod.

## Conventions worth knowing

- **Package name differs across MC versions**: 1.21.10 uses `com.krookedlilly.autohidehud`; 1.21.8 and 1.21.4 use `com.krookedlilly.autohidehotbar` (historical name — the mod originally only hid the hotbar). Don't rename to unify — it would break existing installed configs.
- **Release tag format** is `<mod-version>;<companion-version>` (e.g. `1.1.3;1.0.3`) — the mod and companion are versioned independently but tagged together.
- **Mod is client-only**: `@Mod(value = "autohidehud", dist = Dist.CLIENT)`. Don't add server-side logic.

## Key file paths

- `autohidehud-1.21.10/src/main/java/com/krookedlilly/autohidehud/AutoHideHUD.java` — main mod class, tick loop, `PlayerData` DTO
- `autohidehud-1.21.10/src/main/java/com/krookedlilly/autohidehud/AutoHideHUDConfig.java` — config spec
- `autohidehud-1.21.10/src/main/java/com/krookedlilly/autohidehud/PlayerDataServer.java` — TCP broadcast server
- `autohidehud-1.21.10/src/main/java/com/krookedlilly/autohidehud/mixin/` — alpha-blend mixins
- `common/src/main/java/com/krookedlilly/autohidehud/common/companion/AutoHideHUDCompanion.java` — Swing UI + socket client (standalone-launchable Main-Class, shaded into every mod jar)
- `common/src/main/java/com/krookedlilly/autohidehud/common/companion/Config.java` — companion runtime config (`autohidehudcompanion.properties`)
- `common/src/main/java/com/krookedlilly/autohidehud/common/model/PlayerData.java` — shared DTO (producer: mod, consumer: companion)
- `autohidehud-1.21.10/src/main/resources/autohidehud.mixins.json` — mixin manifest (newer render pipeline)
- `autohidehud-1.21.4/src/main/resources/autohidehud.mixins.json` — mixin manifest (`GuiGraphicsMixin` only; older GuiGraphics API)
- `autohidehud-1.21.10/build.gradle` / `autohidehud-1.21.4/build.gradle` — each has `shade` config + `Main-Class` manifest + `includeBuild('../common')` wiring
- `autohidehud-<version>/gradle.properties` — MC/NeoForge/mod versions per version
- `autohidehudcompanion/` — legacy standalone project, superseded by `common/`

## Dev environment (Mac / VSCode)

- JDK: **Temurin/OpenJDK 21** (Homebrew: `brew install openjdk@21`). It's keg-only, so `JAVA_HOME` must point at `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`. Already set in `~/.zshrc` and in `.vscode/settings.json`.
- VSCode extensions: install **Extension Pack for Java** (`vscjava.vscode-java-pack`). The Gradle for Java extension (`vscjava.vscode-gradle`) is already present.
- First-time-on-a-new-machine: make the wrapper scripts executable: `chmod +x autohidehud-1.21.10/gradlew autohidehud-1.21.8/gradlew autohidehud-1.21.4/gradlew autohidehudcompanion/gradlew` (git-on-Windows checkouts sometimes drop the x bit).

## Verification

To verify changes end-to-end:
1. `cd autohidehud-1.21.10 && ./gradlew build` — confirms the merged jar compiles.
2. `java -jar build/libs/autohidehud-*.jar` — launches companion standalone. Should show the Swing overlay even with no MC running.
3. `./gradlew runClient` — launches MC; join a world and observe HUD fade after `ticksBeforeHiding` ticks of inactivity. In a second terminal, run the jar standalone again to confirm the companion connects.
4. For 1.21.8 or 1.21.4 changes, repeat step 1 in the relevant subfolder (companion doesn't apply).
