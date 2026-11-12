package com.example.ognpricetooltips.mixin;

import com.example.ognpricetooltips.PriceTooltipsClient;
import com.example.ognpricetooltips.RuneHighlighter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public class HandledScreenMixin {
    @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"))
    private void onDrawTooltip(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        PriceTooltipsClient.renderInventoryValues(context, (HandledScreen<?>)(Object)this, MinecraftClient.getInstance(), mouseX, mouseY);
        RuneHighlighter.renderRuneIndicators(context, (HandledScreen<?>)(Object)this, MinecraftClient.getInstance(), mouseX, mouseY);
    }
}