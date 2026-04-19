package com.krookedlilly.autohidehud.mixin;

import com.krookedlilly.autohidehud.AutoHideHUD;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.BlitRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// submitBlitFromItemAtlas in 1.21.11 is private, but mixin method targets don't care about
// visibility — we intercept the BlitRenderState right before it's submitted to the render state.
// 1.21.11's BlitRenderState ctor takes TWO ScreenRectangles (scissorArea + bounds) instead of
// one, so the reconstruction signature differs from 1.21.10.
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {

    @ModifyArg(
            method = {"submitBlitFromItemAtlas(Lnet/minecraft/client/gui/render/state/GuiItemRenderState;FFII)V"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/state/GuiRenderState;submitBlitToCurrentLayer(Lnet/minecraft/client/gui/render/state/BlitRenderState;)V"
            )
    )
    private BlitRenderState modifyItemColor(BlitRenderState original) {
        // use the stored HOTBAR_ALPHA value instead of current ALPHA
        if (AutoHideHUD.hotbarAlpha >= 1f || AutoHideHUD.inMenu) {
            return original;
        }

        int originalColor = original.color();

        int alpha = (originalColor >> 24) & 0xFF;
        int red = (originalColor >> 16) & 0xFF;
        int green = (originalColor >> 8) & 0xFF;
        int blue = originalColor & 0xFF;

        int newAlpha = (int) (alpha * AutoHideHUD.hotbarAlpha);
        int newRed = (int) (red * AutoHideHUD.hotbarAlpha);
        int newGreen = (int) (green * AutoHideHUD.hotbarAlpha);
        int newBlue = (int) (blue * AutoHideHUD.hotbarAlpha);

        int modifiedColor = (newAlpha << 24) | (newRed << 16) | (newGreen << 8) | newBlue;

        return new BlitRenderState(
                original.pipeline(),
                original.textureSetup(),
                original.pose(),
                original.x0(),
                original.y0(),
                original.x1(),
                original.y1(),
                original.u0(),
                original.u1(),
                original.v0(),
                original.v1(),
                modifiedColor,
                original.scissorArea(),
                original.bounds()
        );
    }
}
