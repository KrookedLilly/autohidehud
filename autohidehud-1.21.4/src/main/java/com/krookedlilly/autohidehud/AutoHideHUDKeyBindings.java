package com.krookedlilly.autohidehud;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber
public class AutoHideHUDKeyBindings {
    // 1.21.4's KeyMapping takes the category as a String constant rather than the
    // KeyMapping.Category enum introduced in later versions.
    public static final Lazy<KeyMapping> REVEAL_KEY = Lazy.of(() -> new KeyMapping(
            "autohidehud.keybindings.revealkey",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            KeyMapping.CATEGORY_MISC
    ));

    @SubscribeEvent // on the mod event bus only on the physical client
    public static void registerBindings(RegisterKeyMappingsEvent event) {
        event.register(REVEAL_KEY.get());
    }
}
