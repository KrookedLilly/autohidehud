package com.krookedlilly.autohidehud.mixin;

import com.krookedlilly.autohidehud.AutoHideHUD;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 1.21.4 doesn't have the newer GuiRenderer / GuiTextRenderState / submit* pipeline that 1.21.10 uses.
// It still draws via direct GuiGraphics methods, each taking an int-packed RGBA color at HEAD.
// We intercept the color arg and apply the current fade alpha to it.
@Mixin(GuiGraphics.class)
public class GuiGraphicsMixin {

    // Alpha threshold below which item renders are cancelled entirely. Items use a 3D pipeline
    // whose shaders don't respect RenderSystem.setShaderColor, so smooth fade isn't feasible —
    // we fade everything else smoothly, then cut items when the HUD is nearly invisible.
    private static final float ITEM_CANCEL_THRESHOLD = 0.05f;

    // fill(x1, y1, x2, y2, color) — color is argument index 4.
    @ModifyVariable(method = "fill(IIIII)V", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int modifyFillColor(int color) {
        return AutoHideHUD.applyAlpha(color);
    }

    // fill(x1, y1, x2, y2, z, color) — color is the 6th int (ordinal 5).
    @ModifyVariable(method = "fill(IIIIII)V", at = @At("HEAD"), argsOnly = true, ordinal = 5)
    private int modifyFillColorZ(int color) {
        return AutoHideHUD.applyAlpha(color);
    }

    // fillGradient(x1, y1, x2, y2, colorFrom, colorTo) — two color args, modify both.
    @ModifyVariable(method = "fillGradient(IIIIII)V", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int modifyGradientColorFrom(int color) {
        return AutoHideHUD.applyAlpha(color);
    }

    @ModifyVariable(method = "fillGradient(IIIIII)V", at = @At("HEAD"), argsOnly = true, ordinal = 5)
    private int modifyGradientColorTo(int color) {
        return AutoHideHUD.applyAlpha(color);
    }

    // fillGradient(x1, y1, x2, y2, z, colorFrom, colorTo) — color args at ordinals 5, 6.
    @ModifyVariable(method = "fillGradient(IIIIIII)V", at = @At("HEAD"), argsOnly = true, ordinal = 5)
    private int modifyGradientColorFromZ(int color) {
        return AutoHideHUD.applyAlpha(color);
    }

    @ModifyVariable(method = "fillGradient(IIIIIII)V", at = @At("HEAD"), argsOnly = true, ordinal = 6)
    private int modifyGradientColorToZ(int color) {
        return AutoHideHUD.applyAlpha(color);
    }

    // drawString(Font, String, int, int, int) — the final int is the color.
    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int modifyDrawStringColor(int color) {
        return AutoHideHUD.applyAlpha(color, true);
    }

    // drawString(Font, String, int, int, int, boolean) — integer color, shadow toggle.
    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int modifyDrawStringColorShadow(int color) {
        return AutoHideHUD.applyAlpha(color, true);
    }

    // drawString(Font, FormattedCharSequence, int, int, int) — final int is color.
    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int modifyDrawStringFcsColor(int color) {
        return AutoHideHUD.applyAlpha(color, true);
    }

    // drawString(Font, FormattedCharSequence, int, int, int, boolean) — color + shadow.
    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int modifyDrawStringFcsColorShadow(int color) {
        return AutoHideHUD.applyAlpha(color, true);
    }

    // NOTE (1.21 / 1.21.1): no Function-based blit(Function, RL, ...) overloads exist yet and no
    // blit variant accepts a tint/color arg. Images are still tinted via RenderSystem.setShaderColor,
    // which our mixin can't reach, so background blits don't fade here — same posture as 1.21.4.

    // drawString(Font, Component, int, int, int) — Component-based text (used by many HUD elements).
    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int modifyDrawStringComponent(int color) {
        return AutoHideHUD.applyAlpha(color, true);
    }

    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int modifyDrawStringComponentShadow(int color) {
        return AutoHideHUD.applyAlpha(color, true);
    }

    // NOTE (1.21 / 1.21.1): blitSprite overloads don't accept a tint color in this version
    // (the Function-based overload + color arg was introduced in 1.21.2). No interception target here.

    // --- Item render cancellation ---
    // 3D item rendering bypasses color args. When fade alpha drops below threshold, cancel
    // the render call entirely so items don't sit fully-opaque on top of a faded hotbar.

    @Inject(method = "renderItem(Lnet/minecraft/world/item/ItemStack;II)V", at = @At("HEAD"), cancellable = true)
    private void autohidehud$cancelRenderItem(net.minecraft.world.item.ItemStack stack, int x, int y, CallbackInfo ci) {
        if (AutoHideHUD.getAlpha() < ITEM_CANCEL_THRESHOLD) ci.cancel();
    }

    @Inject(method = "renderItem(Lnet/minecraft/world/item/ItemStack;III)V", at = @At("HEAD"), cancellable = true)
    private void autohidehud$cancelRenderItemSeed(net.minecraft.world.item.ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        if (AutoHideHUD.getAlpha() < ITEM_CANCEL_THRESHOLD) ci.cancel();
    }

    @Inject(method = "renderItem(Lnet/minecraft/world/item/ItemStack;IIII)V", at = @At("HEAD"), cancellable = true)
    private void autohidehud$cancelRenderItemGuiOffset(net.minecraft.world.item.ItemStack stack, int x, int y, int seed, int guiOffset, CallbackInfo ci) {
        if (AutoHideHUD.getAlpha() < ITEM_CANCEL_THRESHOLD) ci.cancel();
    }

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;III)V", at = @At("HEAD"), cancellable = true)
    private void autohidehud$cancelRenderItemEntity(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.item.ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        if (AutoHideHUD.getAlpha() < ITEM_CANCEL_THRESHOLD) ci.cancel();
    }

    @Inject(method = "renderFakeItem(Lnet/minecraft/world/item/ItemStack;II)V", at = @At("HEAD"), cancellable = true)
    private void autohidehud$cancelRenderFakeItem(net.minecraft.world.item.ItemStack stack, int x, int y, CallbackInfo ci) {
        if (AutoHideHUD.getAlpha() < ITEM_CANCEL_THRESHOLD) ci.cancel();
    }

    @Inject(method = "renderFakeItem(Lnet/minecraft/world/item/ItemStack;III)V", at = @At("HEAD"), cancellable = true)
    private void autohidehud$cancelRenderFakeItemSeed(net.minecraft.world.item.ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        if (AutoHideHUD.getAlpha() < ITEM_CANCEL_THRESHOLD) ci.cancel();
    }

    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V", at = @At("HEAD"), cancellable = true)
    private void autohidehud$cancelRenderItemDecorations(net.minecraft.client.gui.Font font, net.minecraft.world.item.ItemStack stack, int x, int y, CallbackInfo ci) {
        if (AutoHideHUD.getAlpha() < ITEM_CANCEL_THRESHOLD) ci.cancel();
    }

    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void autohidehud$cancelRenderItemDecorationsText(net.minecraft.client.gui.Font font, net.minecraft.world.item.ItemStack stack, int x, int y, String text, CallbackInfo ci) {
        if (AutoHideHUD.getAlpha() < ITEM_CANCEL_THRESHOLD) ci.cancel();
    }
}
