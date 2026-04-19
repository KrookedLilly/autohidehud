package com.krookedlilly.autohidehud.mixin;

import com.krookedlilly.autohidehud.AutoHideHUD;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 1.21.8's GuiGraphics is a HYBRID: it has the new RenderPipeline arg on blit/fill/blitSprite
// (unlike 1.21.4), but does NOT have the submit-based color-packed draw API that 1.21.10 uses.
// We intercept the int color arg directly on each draw method.
@Mixin(GuiGraphics.class)
public class GuiGraphicsMixin {

    // Item rendering uses a separate 3D pipeline whose shaders don't respect setShaderColor.
    // Cancel item renders once fade alpha drops near zero so the hotbar can fully hide.
    private static final float ITEM_CANCEL_THRESHOLD = 0.05f;

    // fill(x1, y1, x2, y2, color)
    @ModifyVariable(method = "fill(IIIII)V", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int modifyFillColor(int color) {
        return AutoHideHUD.applyAlpha(color);
    }

    // fill(RenderPipeline, x1, y1, x2, y2, color)
    @ModifyVariable(method = "fill(Lcom/mojang/blaze3d/pipeline/RenderPipeline;IIIII)V", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int modifyFillColorRp(int color) {
        return AutoHideHUD.applyAlpha(color);
    }

    // fillGradient(x1, y1, x2, y2, colorFrom, colorTo)
    @ModifyVariable(method = "fillGradient(IIIIII)V", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int modifyGradientFrom(int color) {
        return AutoHideHUD.applyAlpha(color);
    }

    @ModifyVariable(method = "fillGradient(IIIIII)V", at = @At("HEAD"), argsOnly = true, ordinal = 5)
    private int modifyGradientTo(int color) {
        return AutoHideHUD.applyAlpha(color);
    }

    // drawString variants — all take int color as the 3rd int.
    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int modifyDrawStringStr(int color) {
        return AutoHideHUD.applyAlpha(color, true);
    }

    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int modifyDrawStringStrShadow(int color) {
        return AutoHideHUD.applyAlpha(color, true);
    }

    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int modifyDrawStringFcs(int color) {
        return AutoHideHUD.applyAlpha(color, true);
    }

    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int modifyDrawStringFcsShadow(int color) {
        return AutoHideHUD.applyAlpha(color, true);
    }

    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int modifyDrawStringComponent(int color) {
        return AutoHideHUD.applyAlpha(color, true);
    }

    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int modifyDrawStringComponentShadow(int color) {
        return AutoHideHUD.applyAlpha(color, true);
    }

    // blit(RenderPipeline, ResourceLocation, int, int, float, float, int, int, int, int, int) — 7 ints + 2 floats, color last.
    @ModifyVariable(method = "blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/ResourceLocation;IIFFIIIII)V", at = @At("HEAD"), argsOnly = true, ordinal = 6)
    private int modifyBlitColor(int color) {
        return AutoHideHUD.applyAlpha(color);
    }

    // blit with 8 trailing ints (extra int in middle)
    @ModifyVariable(method = "blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/ResourceLocation;IIFFIIIIII)V", at = @At("HEAD"), argsOnly = true, ordinal = 7)
    private int modifyBlitColor8(int color) {
        return AutoHideHUD.applyAlpha(color);
    }

    // blitSprite(RenderPipeline, ResourceLocation, int, int, int, int, int) — 5 ints, tint color last.
    @ModifyVariable(method = "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/ResourceLocation;IIIII)V", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int modifyBlitSprite5(int color) {
        return AutoHideHUD.applyAlpha(color);
    }

    // blitSprite with 8 ints (9-arg variant)
    @ModifyVariable(method = "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/ResourceLocation;IIIIIIII)V", at = @At("HEAD"), argsOnly = true, ordinal = 7)
    private int modifyBlitSprite8(int color) {
        return AutoHideHUD.applyAlpha(color);
    }

    // --- Item render cancellation (same pattern as 1.21.4 since 3D item pipeline ignores tint) ---

    @Inject(method = "renderItem(Lnet/minecraft/world/item/ItemStack;II)V", at = @At("HEAD"), cancellable = true)
    private void autohidehud$cancelRenderItem(net.minecraft.world.item.ItemStack stack, int x, int y, CallbackInfo ci) {
        if (AutoHideHUD.getAlpha() < ITEM_CANCEL_THRESHOLD) ci.cancel();
    }

    @Inject(method = "renderItem(Lnet/minecraft/world/item/ItemStack;III)V", at = @At("HEAD"), cancellable = true)
    private void autohidehud$cancelRenderItemSeed(net.minecraft.world.item.ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
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
