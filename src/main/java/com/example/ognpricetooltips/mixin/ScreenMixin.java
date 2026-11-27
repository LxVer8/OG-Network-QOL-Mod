package com.example.ognpricetooltips.mixin;

import com.example.ognpricetooltips.MenuOutline;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Inject(method = "renderWithTooltip(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"))
    private void ognpricetooltips$menuRenderStart(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        MenuOutline.beginScreenRender((Screen) (Object) this);
    }

    @Inject(method = "renderWithTooltip(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("RETURN"))
    private void ognpricetooltips$menuRenderEnd(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        MenuOutline.endScreenRender();
    }
}