package com.example.ognpricetooltips;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.util.Formatting;

public final class MenuOutline {

    public static final String MENU_TITLE = "OG-Network QOL";

    private static boolean renderingMenu = false;

    private static boolean drawing = false;

    private MenuOutline() {}

    public static void beginScreenRender(Screen screen) {
        renderingMenu = screen != null && MENU_TITLE.equals(screen.getTitle().getString());
    }

    public static void endScreenRender() {
        renderingMenu = false;
    }

    public static void draw(DrawContext context, TextRenderer renderer, OrderedText text, int x, int y, int color) {
        if (!renderingMenu || drawing || text == null || (color >>> 24) == 0) return;

        OrderedText black = visitor -> text.accept(
                (index, style, codePoint) -> visitor.accept(index, style.withColor(Formatting.BLACK), codePoint));

        drawing = true;
        try {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    context.drawText(renderer, black, x + dx, y + dy, 0xFF000000, false);
                }
            }
        } finally {
            drawing = false;
        }
    }
}