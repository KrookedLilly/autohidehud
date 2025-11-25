package com.krookedlilly.autohidehud.mixin;

import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GuiGraphics.class)
public class GuiGraphicsMixin {
    @ModifyVariable(
            method = "*",
            at = @At("HEAD"),
            argsOnly = true
    )
    private int modifyColorArguments(int value) {
        // Only modify if it looks like an ARGB color (alpha channel exists)
        if ((value & 0xFF000000) != 0) {
            return applyHalfAlpha(value);
        }
        return value;
    }

//    @ModifyVariable(
//            method = {"submitBlit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/textures/GpuTextureView;IIIIFFFFI)V"},
//            at = @At("HEAD"),
//            ordinal = 4,
//            argsOnly = true
//    )
//    private int storeTextureColor1(int color) {
//         return applyHalfAlpha(color);
//    }
//
//    @ModifyVariable(
//            method = {"submitTiledBlit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/textures/GpuTextureView;IIIIIIFFFFI)V"},
//            at = @At("HEAD"),
//            ordinal = 6,
//            argsOnly = true
//    )
//    private int storeTextureColor2(int color) {
//         return applyHalfAlpha(color);
//    }
//
//    @ModifyVariable(
//            method = {"submitColoredRectangle(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/gui/render/TextureSetup;IIIIILjava/lang/Integer;)V"},
//            at = @At("HEAD"),
//            ordinal = 4,
//            argsOnly = true
//    )
//    private int storeBackgroundColor1(int color) {
//         return applyHalfAlpha(color);
//    }
//
//    @ModifyVariable(
//            method = {"fillGradient(IIIIII)V"},
//            at = @At("HEAD"),
//            ordinal = 5,
//            argsOnly = true
//    )
//    private int storeBackgroundColor2(int color) {
//         return applyHalfAlpha(color);
//    }
//    // Modify color for the 5-parameter fill method
//    @ModifyVariable(
//            method = "fill(IIIII)V",
//            at = @At("HEAD"),
//            argsOnly = true,
//            index = 4
//    )
//    private int modifyFillColor(int color) {
//        return applyHalfAlpha(color);
//    }
//
//    // Modify color for fillGradient - color1
//    @ModifyVariable(
//            method = "fillGradient(IIIIII)V",
//            at = @At("HEAD"),
//            argsOnly = true,
//            index = 4
//    )
//    private int modifyGradientColor1(int color1) {
//        return applyHalfAlpha(color1);
//    }
//
//    // Modify color for fillGradient - color2
//    @ModifyVariable(
//            method = {"fillGradient(IIIIII)V"},
//            at = @At("HEAD"),
//            ordinal = 5,
//            argsOnly = true
//    )
//    private int modifyGradientColor2(int color2) {
//        return applyHalfAlpha(color2);
//    }

    private static int applyHalfAlpha(int color) {
        // Extract ARGB components
        int alpha = (color >> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;

        // Apply half alpha
        int newAlpha = (int)(alpha * 0.5f);

        // Recombine
        return (newAlpha << 24) | rgb;
    }
}