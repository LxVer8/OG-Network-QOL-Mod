package com.example.ognpricetooltips;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class KeybindHandler {
    public static KeyBinding openHudEditorKey;

    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("ognpricetooltips", "main"));

    public static void init() {
        openHudEditorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ognpricetooltips.hud_editor",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openHudEditorKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new HudEditScreen(null));
                }
            }
        });
    }
}