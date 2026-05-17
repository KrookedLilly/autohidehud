# HUD Position Offsets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add global + per-element HUD `(x, y)` position offsets to all 11 mod versions (1.21.0–1.21.11), plus extend AppleSkin layer grouping (already in 1.21.10) to every other version.

**Architecture:** Per `docs/superpowers/specs/2026-05-17-hud-position-offsets-design.md` — translate `GuiGraphics.pose()` in `RenderGuiLayerEvent.Pre` (before existing logic) and pop it in `Post`. New config entries default to 0, so behavior is unchanged for users who don't touch the UI. AppleSkin overlays piggyback on their parent vanilla layer (existing 1.21.10 pattern, extended).

**Tech Stack:** Java 21, NeoForge 21.x, Gradle composite build (per-version subprojects), no automated test suite — verification is manual via `./gradlew runClient`.

**Spec:** `docs/superpowers/specs/2026-05-17-hud-position-offsets-design.md`

---

## Pre-flight findings (verified in spec + plan-review sessions)

| MC versions | `pose()` returns | Push/pop methods | `translate` args | `CONTEXTUAL_INFO_BAR` exists | `EXPERIENCE_BAR` + `JUMP_METER` exist | `event.getName()` returns | `onRenderHUDPost` exists |
|---|---|---|---|---|---|---|---|
| 1.21.0, 1.21.1 | `PoseStack` (Mojang) | `pushPose()` / `popPose()` | `translate(double, double, double)` (use `(dx, dy, 0)`) | No | Yes | `ResourceLocation` | **Yes** |
| 1.21.2–1.21.5 | `PoseStack` (Mojang) | `pushPose()` / `popPose()` | `translate(double, double, double)` (use `(dx, dy, 0)`) | No | Yes | `ResourceLocation` | **No — must be created** |
| 1.21.6–1.21.9 | `Matrix3x2fStack` (joml) | `pushMatrix()` / `popMatrix()` | `translate(float, float)` | Yes | No | `ResourceLocation` | **No — must be created** |
| 1.21.10 | `Matrix3x2fStack` (joml) | `pushMatrix()` / `popMatrix()` | `translate(float, float)` | Yes | No | `ResourceLocation` | **Yes** |
| 1.21.11 | `Matrix3x2fStack` (joml) | `pushMatrix()` / `popMatrix()` | `translate(float, float)` | Yes | No | `Identifier` (package: `net.minecraft.resources.Identifier`, already imported) | **Yes** |

**AppleSkin layer-grouping wiring is already present in `shouldHideLayer()` of ALL 12 versions** (verified by `grep -c "appleskin:"` returning 6 in each version's `AutoHideHUD.java`). No `shouldHideLayer()` edits are needed for AppleSkin parity — only `getLayerOffset()` needs the AppleSkin routing. The work labelled "extend AppleSkin to all versions" reduces to a CLAUDE.md doc fix.

---

## File structure

**Per-version files modified (no new files):**
- `autohidehud-<version>/src/main/java/com/krookedlilly/autohidehud/AutoHideHUDConfig.java` — add `hudPositionGroup` config section
- `autohidehud-<version>/src/main/java/com/krookedlilly/autohidehud/AutoHideHUD.java` — add `posePushed` field + `getLayerOffset()` helper, modify (or create) `onRenderHUDPre`/`onRenderHUDPost`. No `shouldHideLayer()` edits needed (AppleSkin grouping is already present in every version per plan-review verification).

**Repo-wide files modified:**
- `CLAUDE.md` — fix stale `autohidehotbar` package-name note + update AppleSkin section to say it's supported on all versions

No new source files, no mixin changes, no gradle build changes.

---

## Phase 1 — Reference implementation on 1.21.10

This phase establishes the pattern. Later phases adapt this code to other versions with the deltas noted.

### Task 1.1: Add `hudPositionGroup` config block to 1.21.10

**Files:**
- Modify: `autohidehud-1.21.10/src/main/java/com/krookedlilly/autohidehud/AutoHideHUDConfig.java`

- [ ] **Step 1: Add static field declarations**

Find the existing `// Data Server Settings` block (the section starting near line 50). Immediately above it, add:

```java
    // HUD Position Settings
    public static final ModConfigSpec.IntValue globalOffsetX;
    public static final ModConfigSpec.IntValue globalOffsetY;
    public static final ModConfigSpec.IntValue hotbarOffsetX;
    public static final ModConfigSpec.IntValue hotbarOffsetY;
    public static final ModConfigSpec.IntValue healthBarOffsetX;
    public static final ModConfigSpec.IntValue healthBarOffsetY;
    public static final ModConfigSpec.IntValue armorOffsetX;
    public static final ModConfigSpec.IntValue armorOffsetY;
    public static final ModConfigSpec.IntValue foodOffsetX;
    public static final ModConfigSpec.IntValue foodOffsetY;
    public static final ModConfigSpec.IntValue airOffsetX;
    public static final ModConfigSpec.IntValue airOffsetY;
    public static final ModConfigSpec.IntValue vehicleHealthOffsetX;
    public static final ModConfigSpec.IntValue vehicleHealthOffsetY;
    public static final ModConfigSpec.IntValue experienceLevelOffsetX;
    public static final ModConfigSpec.IntValue experienceLevelOffsetY;
    public static final ModConfigSpec.IntValue contextualInfoBarOffsetX;
    public static final ModConfigSpec.IntValue contextualInfoBarOffsetY;
    public static final ModConfigSpec.IntValue selectedItemNameOffsetX;
    public static final ModConfigSpec.IntValue selectedItemNameOffsetY;
    public static final ModConfigSpec.IntValue statusEffectsOffsetX;
    public static final ModConfigSpec.IntValue statusEffectsOffsetY;
    public static final ModConfigSpec.IntValue chatOffsetX;
    public static final ModConfigSpec.IntValue chatOffsetY;
    public static final ModConfigSpec.IntValue crosshairOffsetX;
    public static final ModConfigSpec.IntValue crosshairOffsetY;
```

- [ ] **Step 2: Add the `hudPositionGroup` definitions inside the `static {}` initializer**

Find the line `BUILDER.pop();` that closes the "revealConditionsGroup" (just before `// Companion App Section`). Insert this *between* that `BUILDER.pop()` and the `// Companion App Section` line:

```java
        // HUD Position Section
        BUILDER.comment("HUD Position - Shift HUD elements by X/Y pixels (positive Y moves up, positive X moves right)").push("hudPositionGroup");

        globalOffsetX = BUILDER
                .comment("Global X offset applied to all core HUD elements (hotbar, health, armor, food, air, vehicle health, xp level, contextual info bar, selected item name)")
                .defineInRange("globalOffsetX", 0, -1000, 1000);

        globalOffsetY = BUILDER
                .comment("Global Y offset applied to all core HUD elements (positive moves up)")
                .defineInRange("globalOffsetY", 0, -1000, 1000);

        hotbarOffsetX = BUILDER.comment("Per-element X offset added on top of global").defineInRange("hotbarOffsetX", 0, -1000, 1000);
        hotbarOffsetY = BUILDER.comment("Per-element Y offset added on top of global").defineInRange("hotbarOffsetY", 0, -1000, 1000);
        healthBarOffsetX = BUILDER.comment("Per-element X offset added on top of global; also applied to AppleSkin health overlays").defineInRange("healthBarOffsetX", 0, -1000, 1000);
        healthBarOffsetY = BUILDER.comment("Per-element Y offset added on top of global; also applied to AppleSkin health overlays").defineInRange("healthBarOffsetY", 0, -1000, 1000);
        armorOffsetX = BUILDER.comment("Per-element X offset added on top of global").defineInRange("armorOffsetX", 0, -1000, 1000);
        armorOffsetY = BUILDER.comment("Per-element Y offset added on top of global").defineInRange("armorOffsetY", 0, -1000, 1000);
        foodOffsetX = BUILDER.comment("Per-element X offset added on top of global; also applied to AppleSkin food/saturation overlays").defineInRange("foodOffsetX", 0, -1000, 1000);
        foodOffsetY = BUILDER.comment("Per-element Y offset added on top of global; also applied to AppleSkin food/saturation overlays").defineInRange("foodOffsetY", 0, -1000, 1000);
        airOffsetX = BUILDER.comment("Per-element X offset added on top of global").defineInRange("airOffsetX", 0, -1000, 1000);
        airOffsetY = BUILDER.comment("Per-element Y offset added on top of global").defineInRange("airOffsetY", 0, -1000, 1000);
        vehicleHealthOffsetX = BUILDER.comment("Per-element X offset added on top of global").defineInRange("vehicleHealthOffsetX", 0, -1000, 1000);
        vehicleHealthOffsetY = BUILDER.comment("Per-element Y offset added on top of global").defineInRange("vehicleHealthOffsetY", 0, -1000, 1000);
        experienceLevelOffsetX = BUILDER.comment("Per-element X offset added on top of global").defineInRange("experienceLevelOffsetX", 0, -1000, 1000);
        experienceLevelOffsetY = BUILDER.comment("Per-element Y offset added on top of global").defineInRange("experienceLevelOffsetY", 0, -1000, 1000);
        contextualInfoBarOffsetX = BUILDER.comment("Per-element X offset added on top of global; also applied to the contextual info bar background").defineInRange("contextualInfoBarOffsetX", 0, -1000, 1000);
        contextualInfoBarOffsetY = BUILDER.comment("Per-element Y offset added on top of global; also applied to the contextual info bar background").defineInRange("contextualInfoBarOffsetY", 0, -1000, 1000);
        selectedItemNameOffsetX = BUILDER.comment("Per-element X offset added on top of global").defineInRange("selectedItemNameOffsetX", 0, -1000, 1000);
        selectedItemNameOffsetY = BUILDER.comment("Per-element Y offset added on top of global").defineInRange("selectedItemNameOffsetY", 0, -1000, 1000);
        statusEffectsOffsetX = BUILDER.comment("X offset for status effects (top-right cluster); global offset does NOT apply").defineInRange("statusEffectsOffsetX", 0, -1000, 1000);
        statusEffectsOffsetY = BUILDER.comment("Y offset for status effects (top-right cluster); global offset does NOT apply").defineInRange("statusEffectsOffsetY", 0, -1000, 1000);
        chatOffsetX = BUILDER.comment("X offset for chat; global offset does NOT apply").defineInRange("chatOffsetX", 0, -1000, 1000);
        chatOffsetY = BUILDER.comment("Y offset for chat; global offset does NOT apply").defineInRange("chatOffsetY", 0, -1000, 1000);
        crosshairOffsetX = BUILDER.comment("X offset for crosshair; global offset does NOT apply").defineInRange("crosshairOffsetX", 0, -1000, 1000);
        crosshairOffsetY = BUILDER.comment("Y offset for crosshair; global offset does NOT apply").defineInRange("crosshairOffsetY", 0, -1000, 1000);

        BUILDER.pop();

```

- [ ] **Step 3: Build to verify config compiles**

```bash
cd /Users/krookedmac/Documents/Development/KrookedLilly/autohidehud/autohidehud-1.21.10
./gradlew build
```

Expected: `BUILD SUCCESSFUL`. If you get compilation errors, check brace/semicolon placement.

- [ ] **Step 4: Commit**

```bash
cd /Users/krookedmac/Documents/Development/KrookedLilly/autohidehud
git add autohidehud-1.21.10/src/main/java/com/krookedlilly/autohidehud/AutoHideHUDConfig.java
git commit -m "feat(1.21.10): add hudPositionGroup config (global + per-element offsets)"
```

### Task 1.2: Add render-time pose translation to 1.21.10

**Files:**
- Modify: `autohidehud-1.21.10/src/main/java/com/krookedlilly/autohidehud/AutoHideHUD.java`

- [ ] **Step 1: Add `posePushed` instance field**

Find the existing instance/static field block near the top of the class (around line 42–56, where `dataServer`, `currentTick`, `hotbarAlpha` etc. live). Add this after `public static boolean wasSleeping = false;`:

```java
    // posePushed is set in onRenderHUDPre when we push the pose matrix for a
    // HUD-position offset, and consumed in onRenderHUDPost to pop it. The
    // single-boolean approach is only safe because this mod never cancels
    // RenderGuiLayerEvent.Pre (so Post is guaranteed to fire) and layers do
    // not nest. If a future change ever cancels Pre, this needs to become a
    // ResourceLocation-keyed map so the right pushes get popped.
    private boolean posePushed = false;
```

- [ ] **Step 2: Add `getLayerOffset()` helper method**

Append this method to the `AutoHideHUD` class, near `shouldHideLayer()` (it follows the same per-layer dispatch pattern):

```java
    // Returns the effective (x, y) offset for a given HUD layer as an int[2].
    // effectiveOffset = globalOffset (when layer is "core HUD") + per-element override.
    // Crosshair, chat, status effects are not in "core HUD" — they get per-element only.
    private int[] getLayerOffset(ResourceLocation layerName) {
        int gx = AutoHideHUDConfig.globalOffsetX.get();
        int gy = AutoHideHUDConfig.globalOffsetY.get();

        if (layerName.equals(VanillaGuiLayers.HOTBAR))
            return new int[]{gx + AutoHideHUDConfig.hotbarOffsetX.get(), gy + AutoHideHUDConfig.hotbarOffsetY.get()};
        if (layerName.equals(VanillaGuiLayers.PLAYER_HEALTH)
                || layerName.toString().equals("appleskin:health_offset")
                || layerName.toString().equals("appleskin:health_restored"))
            return new int[]{gx + AutoHideHUDConfig.healthBarOffsetX.get(), gy + AutoHideHUDConfig.healthBarOffsetY.get()};
        if (layerName.equals(VanillaGuiLayers.ARMOR_LEVEL))
            return new int[]{gx + AutoHideHUDConfig.armorOffsetX.get(), gy + AutoHideHUDConfig.armorOffsetY.get()};
        if (layerName.equals(VanillaGuiLayers.FOOD_LEVEL)
                || layerName.toString().equals("appleskin:hunger_restored")
                || layerName.toString().equals("appleskin:food_offset")
                || layerName.toString().equals("appleskin:saturation_level")
                || layerName.toString().equals("appleskin:exhaustion_level"))
            return new int[]{gx + AutoHideHUDConfig.foodOffsetX.get(), gy + AutoHideHUDConfig.foodOffsetY.get()};
        if (layerName.equals(VanillaGuiLayers.AIR_LEVEL))
            return new int[]{gx + AutoHideHUDConfig.airOffsetX.get(), gy + AutoHideHUDConfig.airOffsetY.get()};
        if (layerName.equals(VanillaGuiLayers.VEHICLE_HEALTH))
            return new int[]{gx + AutoHideHUDConfig.vehicleHealthOffsetX.get(), gy + AutoHideHUDConfig.vehicleHealthOffsetY.get()};
        if (layerName.equals(VanillaGuiLayers.EXPERIENCE_LEVEL))
            return new int[]{gx + AutoHideHUDConfig.experienceLevelOffsetX.get(), gy + AutoHideHUDConfig.experienceLevelOffsetY.get()};
        if (layerName.equals(VanillaGuiLayers.CONTEXTUAL_INFO_BAR) || layerName.equals(VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND))
            return new int[]{gx + AutoHideHUDConfig.contextualInfoBarOffsetX.get(), gy + AutoHideHUDConfig.contextualInfoBarOffsetY.get()};
        if (layerName.equals(VanillaGuiLayers.SELECTED_ITEM_NAME))
            return new int[]{gx + AutoHideHUDConfig.selectedItemNameOffsetX.get(), gy + AutoHideHUDConfig.selectedItemNameOffsetY.get()};

        // Not "core HUD" — no global, only per-element
        if (layerName.equals(VanillaGuiLayers.EFFECTS))
            return new int[]{AutoHideHUDConfig.statusEffectsOffsetX.get(), AutoHideHUDConfig.statusEffectsOffsetY.get()};
        if (layerName.equals(VanillaGuiLayers.CHAT))
            return new int[]{AutoHideHUDConfig.chatOffsetX.get(), AutoHideHUDConfig.chatOffsetY.get()};
        if (layerName.equals(VanillaGuiLayers.CROSSHAIR))
            return new int[]{AutoHideHUDConfig.crosshairOffsetX.get(), AutoHideHUDConfig.crosshairOffsetY.get()};

        // additionalLayerIds get global only
        if (AutoHideHUDConfig.additionalLayerIds.get().contains(layerName.toString()))
            return new int[]{gx, gy};

        return new int[]{0, 0};
    }
```

- [ ] **Step 3: Push pose at the top of `onRenderHUDPre`**

Find the start of `onRenderHUDPre` (the `@SubscribeEvent` method handling `RenderGuiLayerEvent.Pre`). Insert this as the **very first** statements of the method body, BEFORE the `if (!AutoHideHUDConfig.enableAutoHiding.get()) return;` line:

```java
        int[] off = getLayerOffset(event.getName());
        if (off[0] != 0 || off[1] != 0) {
            var pose = event.getGuiGraphics().pose();
            pose.pushMatrix();
            pose.translate(off[0], off[1]);
            posePushed = true;
        }

```

- [ ] **Step 4: Pop pose at the top of `onRenderHUDPost`**

Find `onRenderHUDPost` (currently just sets `alpha = 1f;`). Insert this as the very first statements of the method body:

```java
        if (posePushed) {
            event.getGuiGraphics().pose().popMatrix();
            posePushed = false;
        }
```

- [ ] **Step 5: Build to verify**

```bash
cd /Users/krookedmac/Documents/Development/KrookedLilly/autohidehud/autohidehud-1.21.10
./gradlew build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Manual verification in runClient**

```bash
./gradlew runClient
```

In Minecraft:
1. Open mod config (Mods → Auto Hide HUD → Config), navigate to `hudPositionGroup`.
2. Set `globalOffsetY = 30`. Save. Confirm hotbar, hearts, hunger, xp bar all shift UP by 30 pixels.
3. Confirm crosshair, chat, status-effects icons did NOT move.
4. Add `hotbarOffsetY = 15` (total +45). Confirm hotbar moved an additional 15px relative to siblings.
5. Set `crosshairOffsetX = 80`. Confirm crosshair shifts right (and global Y still doesn't apply to it).
6. Reset all to 0. Confirm HUD returns to vanilla positions.
7. Idle until HUD fades out. Confirm fade works at the offset position.
8. Press F1 to hide HUD and F1 again. Confirm no crash / no visible glitch.

If any of these fail, debug before proceeding to other versions.

- [ ] **Step 7: Commit**

```bash
cd /Users/krookedmac/Documents/Development/KrookedLilly/autohidehud
git add autohidehud-1.21.10/src/main/java/com/krookedlilly/autohidehud/AutoHideHUD.java
git commit -m "feat(1.21.10): apply pose offsets to HUD layers from new config"
```

---

## Phase 2 — Apply to other `Matrix3x2fStack`-API versions

These versions (1.21.11, 1.21.9, 1.21.8, 1.21.7, 1.21.6) use the same `pushMatrix()`/`popMatrix()`/`translate(x, y)` API as 1.21.10. The pattern from Phase 1 is copy-paste with one delta per version (noted below).

### Task 2.1: 1.21.11

**Files:**
- Modify: `autohidehud-1.21.11/src/main/java/com/krookedlilly/autohidehud/AutoHideHUDConfig.java`
- Modify: `autohidehud-1.21.11/src/main/java/com/krookedlilly/autohidehud/AutoHideHUD.java`

**Version-specific deltas:**
- `event.getName()` returns `Identifier` (already imported as `net.minecraft.resources.Identifier` in this file — do NOT replace any import; the rename is class-name only, package is unchanged from 1.21.10's `ResourceLocation`). Change the `getLayerOffset` parameter type to `Identifier` to match the existing `shouldHideLayer(Identifier layerName)` signature.
- `onRenderHUDPost` **already exists** (just the `alpha = 1f` reset). Modify it per Phase 1 Task 1.2 Step 4.
- `shouldHideLayer()` already has AppleSkin checks. No change needed there.

- [ ] **Step 1: Copy Phase 1 Task 1.1's config block verbatim into 1.21.11's `AutoHideHUDConfig.java`** (config is pure Java, no Identifier/ResourceLocation references)

- [ ] **Step 2: Add Phase 1 Task 1.2's `posePushed` field + `getLayerOffset()` helper into 1.21.11's `AutoHideHUD.java`**, with these changes to `getLayerOffset()`:
  - Change parameter type from `ResourceLocation` to `Identifier`
  - The body works as-is — `.equals(VanillaGuiLayers.X)` returns true via Identifier's equals, and `.toString()` is identical

- [ ] **Step 3: Modify `onRenderHUDPre` and `onRenderHUDPost` per Phase 1 Task 1.2 Steps 3 and 4** (no API differences)

- [ ] **Step 4: Build**

```bash
cd /Users/krookedmac/Documents/Development/KrookedLilly/autohidehud/autohidehud-1.21.11
./gradlew build
```

- [ ] **Step 5: Smoke test in runClient**

```bash
./gradlew runClient
```

Run the abbreviated test: set `globalOffsetY = 30`, confirm hotbar/hearts/hunger/xp shift up; set back to 0; confirm restoration.

- [ ] **Step 6: Commit**

```bash
cd /Users/krookedmac/Documents/Development/KrookedLilly/autohidehud
git add autohidehud-1.21.11/src/main/java/com/krookedlilly/autohidehud/
git commit -m "feat(1.21.11): add HUD position offsets"
```

### Task 2.2: 1.21.9

**Files:**
- Modify: `autohidehud-1.21.9/src/main/java/com/krookedlilly/autohidehud/AutoHideHUDConfig.java`
- Modify: `autohidehud-1.21.9/src/main/java/com/krookedlilly/autohidehud/AutoHideHUD.java`

**Version-specific deltas:**
- Uses `ResourceLocation`, has `CONTEXTUAL_INFO_BAR`.
- `onRenderHUDPost` **does NOT exist** — must be **created** (see Step 4).
- `shouldHideLayer()` already has AppleSkin checks. No change needed there.

- [ ] **Step 1: Copy Phase 1 Task 1.1's config block verbatim into 1.21.9's `AutoHideHUDConfig.java`**

- [ ] **Step 2: Copy Phase 1 Task 1.2's `posePushed` field + `getLayerOffset()` helper verbatim into 1.21.9's `AutoHideHUD.java`** (no API differences)

- [ ] **Step 3: Add the pose-push block as the very first statements of `onRenderHUDPre` per Phase 1 Task 1.2 Step 3**

- [ ] **Step 4: Create the `onRenderHUDPost` handler** (1.21.9 doesn't have one)

Add this method anywhere inside the `AutoHideHUD` class — placing it directly after `onRenderHUDPre` is natural:

```java
    @SubscribeEvent
    public void onRenderHUDPost(RenderGuiLayerEvent.Post event) {
        if (posePushed) {
            event.getGuiGraphics().pose().popMatrix();
            posePushed = false;
        }
    }
```

Verify `import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;` is already present (it is, since `onRenderHUDPre` uses it).

- [ ] **Step 5: Build:** `cd autohidehud-1.21.9 && ./gradlew build`
- [ ] **Step 6: Smoke test as in Task 2.1 Step 5**
- [ ] **Step 7: Commit:** `git add autohidehud-1.21.9/... && git commit -m "feat(1.21.9): add HUD position offsets"`

### Task 2.3: 1.21.8

Identical to Task 2.2 (same deltas: missing `onRenderHUDPost`, has CONTEXTUAL_INFO_BAR, AppleSkin already present). Repeat steps 1–7 against `autohidehud-1.21.8/`.

### Task 2.4: 1.21.7

Identical to Task 2.2 against `autohidehud-1.21.7/`. Repeat steps 1–7.

### Task 2.5: 1.21.6

Identical to Task 2.2 against `autohidehud-1.21.6/`. Repeat steps 1–7.

---

## Phase 3 — Apply to `PoseStack`-API versions

These versions (1.21.5, 1.21.4, 1.21.3, 1.21.2, 1.21.1, 1.21.0) use Mojang's `PoseStack` instead of joml's `Matrix3x2fStack`. The pose-translation code from Phase 1 needs adjustment.

**Common deltas from Phase 1 for ALL Phase-3 tasks:**
- `pose.pushMatrix()` → `pose.pushPose()`
- `pose.popMatrix()` → `pose.popPose()`
- `pose.translate(off[0], off[1])` → `pose.translate(off[0], off[1], 0)` (third arg is Z; must be 0)
- The `var pose = event.getGuiGraphics().pose();` line can stay as `var` — type inference picks up `PoseStack`.
- `CONTEXTUAL_INFO_BAR` and `CONTEXTUAL_INFO_BAR_BACKGROUND` do NOT exist in these MC versions. Omit `contextualInfoBarOffsetX/Y` from the config block (skip those two field declarations and two builder definitions). Omit the corresponding branch in `getLayerOffset()`.
- **`EXPERIENCE_BAR` and `JUMP_METER` DO exist in these versions** and are grouped under `hideExperienceLevel` in `shouldHideLayer()`. The `getLayerOffset()` branch for experience must include all three: `EXPERIENCE_LEVEL`, `EXPERIENCE_BAR`, `JUMP_METER`. See Task 3.1 Step 2.
- AppleSkin checks already exist in `shouldHideLayer()` (verified for every version) — **no `shouldHideLayer()` edits needed**.

**Per-task variance:** 1.21.0 and 1.21.1 already have `onRenderHUDPost`. 1.21.2, 1.21.3, 1.21.4, 1.21.5 do NOT — for those, create it (same pattern as Phase 2 Task 2.2 Step 4, but using `popPose()` instead of `popMatrix()`).

### Task 3.1: 1.21.5

**Files:**
- Modify: `autohidehud-1.21.5/src/main/java/com/krookedlilly/autohidehud/AutoHideHUDConfig.java`
- Modify: `autohidehud-1.21.5/src/main/java/com/krookedlilly/autohidehud/AutoHideHUD.java`

**Version-specific:** `onRenderHUDPost` does NOT exist — must be created. No AppleSkin edits to `shouldHideLayer()`.

- [ ] **Step 1: Copy Phase 1 Task 1.1's config block into 1.21.5's config**, but OMIT the `contextualInfoBarOffsetX/Y` static field declarations (lines under "// HUD Position Settings") AND omit the matching `contextualInfoBarOffsetX = BUILDER...` and `contextualInfoBarOffsetY = BUILDER...` lines inside the `static {}` initializer.

- [ ] **Step 2: Copy Phase 1 Task 1.2's `posePushed` field + `getLayerOffset()` helper into 1.21.5's `AutoHideHUD.java`**, with these changes:
  - Omit the `if (layerName.equals(VanillaGuiLayers.CONTEXTUAL_INFO_BAR) ...)` branch from `getLayerOffset()` entirely.
  - Replace the `EXPERIENCE_LEVEL` branch with one that covers all three legacy XP layers:

    ```java
        if (layerName.equals(VanillaGuiLayers.EXPERIENCE_LEVEL)
                || layerName.equals(VanillaGuiLayers.EXPERIENCE_BAR)
                || layerName.equals(VanillaGuiLayers.JUMP_METER))
            return new int[]{gx + AutoHideHUDConfig.experienceLevelOffsetX.get(), gy + AutoHideHUDConfig.experienceLevelOffsetY.get()};
    ```

- [ ] **Step 3: Add the pose-push block as the very first statements of `onRenderHUDPre`** (PoseStack API):

```java
        int[] off = getLayerOffset(event.getName());
        if (off[0] != 0 || off[1] != 0) {
            var pose = event.getGuiGraphics().pose();
            pose.pushPose();
            pose.translate(off[0], off[1], 0);
            posePushed = true;
        }
```

- [ ] **Step 4: Create the `onRenderHUDPost` handler** (1.21.5 doesn't have one)

Add this method after `onRenderHUDPre`:

```java
    @SubscribeEvent
    public void onRenderHUDPost(RenderGuiLayerEvent.Post event) {
        if (posePushed) {
            event.getGuiGraphics().pose().popPose();
            posePushed = false;
        }
    }
```

Verify `import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;` is already present.

- [ ] **Step 5: Build:** `cd autohidehud-1.21.5 && ./gradlew build`

- [ ] **Step 6: Smoke test as in Task 2.1 Step 5** (skip the contextual-info-bar specific check since the layer doesn't exist). Also verify XP bar moves with global offset since it's now routed via `EXPERIENCE_BAR`.

- [ ] **Step 7: Commit:** `git add autohidehud-1.21.5/... && git commit -m "feat(1.21.5): add HUD position offsets"`

### Task 3.2: 1.21.4

Identical to Task 3.1 against `autohidehud-1.21.4/`. Same PoseStack API, same omissions, same Post-creation requirement.

### Task 3.3: 1.21.3

Identical to Task 3.1 against `autohidehud-1.21.3/`.

### Task 3.4: 1.21.2

Identical to Task 3.1 against `autohidehud-1.21.2/`.

### Task 3.5: 1.21.1

Same as Task 3.1 against `autohidehud-1.21.1/` **EXCEPT** `onRenderHUDPost` **already exists** — modify it (per Step 4's body) instead of creating a new method. Steps 1, 2, 3, 5, 6, 7 are unchanged.

**Extra note for 1.21.0/1.21.1:** per CLAUDE.md, the `Function<ResourceLocation, RenderType>`-based blit overloads don't exist in 1.21.0/1.21.1 (so the mixin file is shorter). This doesn't affect position offsets — pose translation is independent of the blit-color mixin. No extra adjustment needed.

### Task 3.6: 1.21.0

Same as Task 3.5 (modify existing `onRenderHUDPost`) against `autohidehud-1.21.0/`.

---

## Phase 4 — Documentation fixes

### Task 4.1: Fix CLAUDE.md package-name note

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Find and update the stale `autohidehotbar` package note**

Open `CLAUDE.md`, search for the "Conventions worth knowing" section. Replace this bullet:

```
- **Package name differs across MC versions**: 1.21.10 uses `com.krookedlilly.autohidehud`; 1.21.8 and 1.21.4 use `com.krookedlilly.autohidehotbar` (historical name — the mod originally only hid the hotbar). Don't rename to unify — it would break existing installed configs.
```

With:

```
- **Package name is uniform**: all 12 mod folders (1.21.0–1.21.11) use `com.krookedlilly.autohidehud`. The `com.krookedlilly.autohidehotbar` name only survives in the superseded `autohidehudcompanion/` legacy project, which is marked for deletion.
```

### Task 4.2: Update CLAUDE.md AppleSkin section

- [ ] **Step 1: Find the "AppleSkin integration (1.21.10 only)" heading and update it**

Replace the section heading and the first sentence:

```
### AppleSkin integration (1.21.10 only)
`AutoHideHUDConfig` has dedicated booleans for AppleSkin's custom GUI layer IDs (`appleskin:health_offset`, `appleskin:health_restored`, `appleskin:hunger_restored`, `appleskin:food_offset`, `appleskin:saturation_level`, `appleskin:exhaustion_level`).
```

With:

```
### AppleSkin integration (all versions)
AppleSkin's custom GUI layer IDs (`appleskin:health_offset`, `appleskin:health_restored`, `appleskin:hunger_restored`, `appleskin:food_offset`, `appleskin:saturation_level`, `appleskin:exhaustion_level`) piggyback on the parent vanilla layer's hide and offset config. Enabling `hideHealthBar` also hides `appleskin:health_*` overlays; setting `healthBarOffsetY` also moves them. Implemented in every version's `shouldHideLayer()` and `getLayerOffset()`.
```

### Task 4.3: Commit doc fixes

- [ ] ```bash
cd /Users/krookedmac/Documents/Development/KrookedLilly/autohidehud
git add CLAUDE.md
git commit -m "docs: CLAUDE.md — package name uniform across versions; AppleSkin on all"
```

---

## Phase 5 — Final cross-version sanity check

This phase confirms the 11 builds still produce valid jars and catches any leftover per-version typos before declaring done.

### Task 5.1: Build every version

- [ ] **Step 1: Loop-build all 11 versions**

```bash
cd /Users/krookedmac/Documents/Development/KrookedLilly/autohidehud
for v in 1.21.0 1.21.1 1.21.2 1.21.3 1.21.4 1.21.5 1.21.6 1.21.7 1.21.8 1.21.9 1.21.10 1.21.11; do
  echo "=== Building autohidehud-$v ==="
  (cd "autohidehud-$v" && ./gradlew build) || { echo "FAILED: $v"; break; }
done
echo "All builds succeeded."
```

Expected: every version builds. If any version fails, fix that version's deltas and rerun.

### Task 5.2: Spot-check 1.21.4 (PoseStack pipeline) and 1.21.8 (hybrid pipeline)

These two non-reference versions exercise the two main API code paths we adapted.

- [ ] **Step 1: Smoke test 1.21.4**

```bash
cd autohidehud-1.21.4
./gradlew runClient
```

Set `globalOffsetY = 30`, confirm core HUD shifts up. Reset. Quit.

- [ ] **Step 2: Smoke test 1.21.8**

```bash
cd ../autohidehud-1.21.8
./gradlew runClient
```

Same smoke test.

### Task 5.3: Done

- [ ] **Step 1: Verify clean git status**

```bash
cd /Users/krookedmac/Documents/Development/KrookedLilly/autohidehud
git status
git log --oneline -20
```

Expected: working tree clean (except the pre-existing dirty `.DS_Store` / `build.gradle` / gradle.properties from before this work), and 14ish new commits — 11 per-version feature commits + 1 CLAUDE.md fix commit + the 2 prior spec commits.

The previously-tracked dirty files (`.DS_Store`, gradle locks, etc.) are unrelated to this work and should NOT be committed as part of it.
