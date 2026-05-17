# HUD position offsets + cross-version AppleSkin parity

**Status:** Approved
**Date:** 2026-05-17
**Author:** Ryan Harrington (via Claude Code brainstorming)

## Context

Java Edition renders HUD elements roughly an inch above the bottom of the screen. Bedrock Edition flushes them to the bottom edge. Players who came from Bedrock — or who otherwise want to reposition the HUD — currently have no in-mod way to shift the hotbar, hearts, hunger, XP bar, etc.

The reference implementation is the **HUD Manager** mod, which the user has decompiled previously. Its approach uses NeoForge's `RenderGuiLayerEvent.Pre/Post` to translate `GuiGraphics.pose()` before each HUD layer renders. This works for every layer we care about, including hotbar items (since `GuiItemRenderState` snapshots the pose at submit time).

A secondary scope item: AppleSkin overlay layers are currently only grouped under their parent vanilla layers in 1.21.10. The same grouping should exist in all 11 mod versions so AppleSkin's overlays auto-hide and auto-offset alongside the vanilla health/food bars on every supported MC version.

## Goals

1. Let users push the HUD by an `(x, y)` offset, with a global setting for "move everything as a cluster" and per-element overrides for fine-tuning.
2. AppleSkin's health/food overlays follow the hide and offset of their parent vanilla layer, on every supported MC version (1.21.0 through 1.21.11).
3. Zero behavior change for users who don't touch the new config (all new offsets default to 0).
4. No new mixin work — the entire feature lives in `AutoHideHUD.java` + `AutoHideHUDConfig.java` for each version.

## Non-goals

- Per-element scale.
- Anchor changes (top-left → top-right, etc.).
- A drag-to-position editor screen.
- Boss bar / scoreboard / sidebar offsets.
- A "Bedrock preset" button — one Y value covers it.
- 26.x port (already blocked for unrelated API reasons; tracked separately).

## Design

### Effective offset rule

For each HUD layer:

```
effectiveOffset = globalOffset (if layer is in "core HUD") + perElementOffset
```

Both default to 0. So existing users see no change until they touch the new config.

"Core HUD" = the bottom-anchored cluster users typically think of as "the HUD":
- hotbar, player health, armor, food, air, vehicle health, experience level, contextual info bar, selected item name.

Not in "core HUD" (so `globalOffset` does NOT apply):
- crosshair (centered)
- chat (left-anchored, independent)
- status effects (top-right cluster)

User-defined `additionalLayerIds` receive `globalOffset` only.

### Config schema additions

New `hudPositionGroup` section in `AutoHideHUDConfig.java`:

```
globalOffsetX                  (int, default 0)
globalOffsetY                  (int, default 0)

# per-element overrides, added on top of global where applicable
hotbarOffsetX / hotbarOffsetY
healthBarOffsetX / healthBarOffsetY
armorOffsetX / armorOffsetY
foodOffsetX / foodOffsetY
airOffsetX / airOffsetY
vehicleHealthOffsetX / vehicleHealthOffsetY
experienceLevelOffsetX / experienceLevelOffsetY
contextualInfoBarOffsetX / contextualInfoBarOffsetY   # 1.21.6+ only
selectedItemNameOffsetX / selectedItemNameOffsetY
statusEffectsOffsetX / statusEffectsOffsetY           # global does NOT apply
chatOffsetX / chatOffsetY                             # global does NOT apply
crosshairOffsetX / crosshairOffsetY                   # global does NOT apply
```

The NeoForge `ConfigurationScreen` auto-generates UI from the spec — no separate screen work.

### Render flow

**Pose push must run before any existing logic in `onRenderHUDPre`** — the current handler has multiple early `return` paths inside the fade branch, and offsets must apply independently of whether auto-hiding is enabled (it's a positioning feature, not a fade feature). So the placement is:

In `AutoHideHUD.onRenderHUDPre(RenderGuiLayerEvent.Pre event)`, as the **very first** statements — *before* the existing `if (!enableAutoHiding) return;` gate:

```java
int[] off = getLayerOffset(event.getName());   // {dx, dy}
if (off[0] != 0 || off[1] != 0) {
    var pose = event.getGuiGraphics().pose();
    pose.pushMatrix();                          // method name verified per-version
    pose.translate(off[0], off[1]);
    posePushed = true;                          // instance field
}
// ...existing alpha logic follows, untouched...
```

In `AutoHideHUD.onRenderHUDPost(RenderGuiLayerEvent.Post event)`, before the existing `alpha = 1f` reset (Post has no early returns, so placement here is safe):

```java
if (posePushed) {
    event.getGuiGraphics().pose().popMatrix();
    posePushed = false;
}
// ...existing alpha = 1f reset follows...
```

`getLayerOffset(name)` returns the sum of `globalOffset` (when the layer is "core HUD") plus the matching per-element offset. It uses the same layer-to-config-key grouping that `shouldHideLayer()` uses, including AppleSkin layer routing (see next section).

**`CONTEXTUAL_INFO_BAR_BACKGROUND` routes to the same `contextualInfoBarOffset` as `CONTEXTUAL_INFO_BAR`** — otherwise the background would visually detach from the bar.

**Offsets apply regardless of `enableAutoHiding`.** A user who disables fade behavior can still use the position-offset feature.

### AppleSkin layer grouping (extended to all versions)

Each version's `shouldHideLayer()` gains the same six AppleSkin checks that 1.21.10 already has:

```java
if (hideHealthBar && layerName.toString().equals("appleskin:health_offset"))   return true;
if (hideHealthBar && layerName.toString().equals("appleskin:health_restored")) return true;
if (hideFoodLevel && layerName.toString().equals("appleskin:hunger_restored")) return true;
if (hideFoodLevel && layerName.toString().equals("appleskin:food_offset"))     return true;
if (hideFoodLevel && layerName.toString().equals("appleskin:saturation_level"))return true;
if (hideFoodLevel && layerName.toString().equals("appleskin:exhaustion_level"))return true;
```

`getLayerOffset()` routes those same layer IDs to `healthBarOffset` / `foodOffset` (respectively) so AppleSkin overlays move with their parent.

**Risk to flag:** these layer IDs were verified against the AppleSkin build for MC 1.21.10. AppleSkin builds for other MC versions are expected to use the same un-versioned IDs, but this has not been confirmed for every release. The `additionalLayerIds` config remains the safety net if any specific AppleSkin build uses a different name.

### Push/pop balance

A single instance field `boolean posePushed` is enough. NeoForge's `RenderGuiLayerEvent` never nests (layers render serially), and `Pre` is never cancelled by this mod, so the `Pre`/`Post` pairing is guaranteed. The flag avoids tracking which specific layer pushed.

### Hotbar items (no extra work)

`GuiItemRenderState` snapshots the pose matrix at submit time. So translating the pose during the `HOTBAR` layer's `Pre`/`Post` automatically carries through to the items that `GuiRenderer.submitBlitFromItemAtlas` flushes later. No `GuiRendererMixin` changes needed.

## Per-version notes

| Version    | Pose API to verify              | Notes                                      |
|------------|---------------------------------|--------------------------------------------|
| 1.21.0–1.21.5  | `pose().pushMatrix/translate/popMatrix` (or `pushPose/popPose` on the oldest)  | Verify exact method names per version during implementation. No `CONTEXTUAL_INFO_BAR` config entry. |
| 1.21.6–1.21.8  | Same as above                                                                  | Hybrid pipeline; no impact on offsets.     |
| 1.21.9–1.21.10 | Same as above                                                                  | 1.21.10 already has AppleSkin wiring; this spec extends it backward. |
| 1.21.11        | Same as above                                                                  | Source-level `ResourceLocation`→`Identifier` rename was already applied. |

No mixin changes in any version. AppleSkin grouping is identical in every version's `shouldHideLayer()`.

## Edge cases

- **Fade interaction**: alpha and offset are independent. A hidden (alpha≈0) element still renders through the translated pose — no conflict.
- **Reveal-on-condition**: forced reveal (low health, R key, creative) doesn't touch offsets — they always apply.
- **F1 / no-HUD**: events still fire but draw nothing; `pushPose`/`popPose` are no-ops in effect.
- **`additionalLayerIds`**: get `globalOffset` only. Documented in the config comment for that entry.
- **JEI / REI overlays**: drawn outside `RenderGuiLayerEvent` flow → unaffected. Matches existing `alpha = 1f` reset in `Post`.
- **Push/pop balance**: single `posePushed` boolean; safe because layers don't nest and `Pre` is not cancelled.

## Verification

For each version (full sweep on 1.21.10, smoke test on at least one older and one mid-range version, then quick build verification on the rest):

1. `./gradlew build` — confirms compile.
2. `./gradlew runClient` — join world.
3. Set `globalOffsetY = 20`. Confirm all core-HUD elements shift up 20px as a group; crosshair, chat, status effects unchanged.
4. Add `hotbarOffsetY = 10`. Confirm hotbar shifts 30px total (global + per-element) while siblings stay at 20px.
5. Set `crosshairOffsetX = 50`. Confirm crosshair moves; global isn't applied to it.
6. With AppleSkin installed (1.21.10 plus 1–2 other versions where the AppleSkin jar is available), confirm AppleSkin health/food overlays move with their parent.
7. Trigger fade-out, confirm fade still works at the new position.
8. F1 toggle, confirm no crashes from unmatched push/pop.

## Out of scope (deferred)

- Per-element scale.
- Anchor switches.
- Drag-to-position GUI editor.
- Boss bar / scoreboard / sidebar offsets.
- Presets.
- 26.x port.

## Follow-up

- Fix the CLAUDE.md package-name note (all 12 versions use `com.krookedlilly.autohidehud`; the `autohidehotbar` claim is stale and only true of the superseded `autohidehudcompanion/` legacy project).
- Update the CLAUDE.md AppleSkin section to say AppleSkin is supported on all versions (after this lands).
