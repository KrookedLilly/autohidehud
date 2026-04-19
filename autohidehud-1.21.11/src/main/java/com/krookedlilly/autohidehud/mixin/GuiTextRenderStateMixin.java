package com.krookedlilly.autohidehud.mixin;

import com.krookedlilly.autohidehud.AutoHideHUD;
import net.minecraft.client.gui.render.state.GuiTextRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GuiTextRenderState.class)
public abstract class GuiTextRenderStateMixin {
    @ModifyVariable(
            // 1.21.11 changed the matrix type to the read-only Matrix3x2fc interface and added a second boolean.
            method = {"<init>(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;Lorg/joml/Matrix3x2fc;IIIIZZLnet/minecraft/client/gui/navigation/ScreenRectangle;)V"},
            at = @At("CTOR_HEAD"),
            ordinal = 2,
            argsOnly = true
    )
    private int modifyTextColor(int color) {
        return AutoHideHUD.applyAlpha(color, true);
    }
}