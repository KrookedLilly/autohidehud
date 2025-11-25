package com.krookedlilly.autohidehud.mixin;

import com.krookedlilly.autohidehud.AutoHideHUD;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GuiGraphics.class)
public class GuiGraphicsMixin {
    @ModifyVariable(
            method = {"submitBlit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/textures/GpuTextureView;IIIIFFFFI)V"},
            at = @At("HEAD"),
            ordinal = 4,
            argsOnly = true
    )
    private int modifyColor1(int color) {
        return AutoHideHUD.applyAlpha(color);
    }

    @ModifyVariable(
            method = {"submitTiledBlit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/textures/GpuTextureView;IIIIIIFFFFI)V"},
            at = @At("HEAD"),
            ordinal = 6,
            argsOnly = true
    )
    private int modifyColor2(int color) {
        return AutoHideHUD.applyAlpha(color);
    }

    @ModifyVariable(
            method = {"submitColoredRectangle(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/gui/render/TextureSetup;IIIIILjava/lang/Integer;)V"},
            at = @At("HEAD"),
            ordinal = 4,
            argsOnly = true
    )
    private int modifyBackgroundColor1(int color) {
        return AutoHideHUD.applyAlpha(color);
    }

    @ModifyVariable(
            method = {"fillGradient(IIIIII)V"},
            at = @At("HEAD"),
            ordinal = 5,
            argsOnly = true
    )
    private int modifyBackgroundColor2(int color) {
        return AutoHideHUD.applyAlpha(color);
    }

    @ModifyVariable(
            method = "fill(IIIII)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 4
    )
    private int modifyFillColor(int color) {
        return AutoHideHUD.applyAlpha(color);
    }

    @ModifyVariable(
            method = "fillGradient(IIIIII)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 4
    )
    private int modifyGradientColor1(int color1) {
        return AutoHideHUD.applyAlpha(color1);
    }

    @ModifyVariable(
            method = {"fillGradient(IIIIII)V"},
            at = @At("HEAD"),
            ordinal = 5,
            argsOnly = true
    )
    private int modifyGradientColor2(int color2) {
        return AutoHideHUD.applyAlpha(color2);
    }
}