package com.example.ognpricetooltips;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class HudEditScreen extends Screen {

    private final Screen parent;

    private boolean draggingVote = false;
    private boolean draggingChat = false;
    private boolean draggingSeismic = false;

    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    private static final int VOTE_WIDTH = VoteTracker.WIDTH;
    private static final int CHAT_WIDTH = ChatGamesTracker.WIDTH;
    private static final int SEISMIC_WIDTH = SeismicSlamTracker.WIDTH;
    private static final int VOTE_HEIGHT = 36;
    private static final int CHAT_HEIGHT = 10;
    private static final int SEISMIC_HEIGHT = 10;

    private static final int PANEL_W = 180;
    private static final int ROW_H = 24;
    private ButtonWidget eyeButton;
    private final List<ButtonWidget> panelButtons = new ArrayList<>();
    private boolean eyeOpen = false;
    private int panelX, panelY, panelH;

    public HudEditScreen(Screen parent) {
        super(Text.literal("Edit HUD Layout"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> {
            ModConfig.INSTANCE.save();
            MinecraftClient.getInstance().setScreen(parent);
        }).dimensions(this.width / 2 - 50, this.height - 30, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Reset to Default"), button -> {
            ModConfig.INSTANCE.voteTrackerX = 5;
            ModConfig.INSTANCE.voteTrackerY = 10;
            ModConfig.INSTANCE.chatGamesX = 5;
            ModConfig.INSTANCE.chatGamesY = 50;
            ModConfig.INSTANCE.seismicSlamX = 5;
            ModConfig.INSTANCE.seismicSlamY = 70;
            ModConfig.INSTANCE.voteTrackerRightAligned = false;
            ModConfig.INSTANCE.chatGamesRightAligned = false;
            ModConfig.INSTANCE.seismicSlamRightAligned = false;
        }).dimensions(this.width / 2 - 100, 10, 200, 20).build());

        eyeButton = this.addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
            eyeOpen = !eyeOpen;
            rebuildPanel();
        }).dimensions(this.width / 2 + 104, 10, 20, 20)
                .tooltip(Tooltip.of(Text.literal("Show or hide HUD elements")))
                .build());

        panelButtons.clear();
        rebuildPanel();
    }

    private void rebuildPanel() {
        for (ButtonWidget b : panelButtons) this.remove(b);
        panelButtons.clear();
        if (!eyeOpen) return;

        panelX = eyeButton.getX() + eyeButton.getWidth() - PANEL_W;
        panelY = eyeButton.getY() + eyeButton.getHeight() + 4;
        panelH = HudElements.ALL.size() * ROW_H + 6;

        int y = panelY + 4;
        for (HudElements.Entry entry : HudElements.ALL) {
            ButtonWidget btn = ButtonWidget.builder(label(entry), button -> {
                entry.set(!entry.get());
                ModConfig.INSTANCE.save();
                button.setMessage(label(entry));
            }).dimensions(panelX + 5, y, PANEL_W - 10, 20)
                    .tooltip(Tooltip.of(Text.literal(entry.tooltip())))
                    .build();
            panelButtons.add(this.addDrawableChild(btn));
            y += ROW_H;
        }
    }

    private static Text label(HudElements.Entry entry) {
        boolean on = entry.get();
        return Text.literal(entry.name() + ": ")
                .append(Text.literal(on ? "ON" : "OFF").formatted(on ? Formatting.GREEN : Formatting.RED));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x88000000);

        MinecraftClient client = MinecraftClient.getInstance();

        if (ModConfig.INSTANCE.enableVoteTracker) {
            drawBox(context, ModConfig.INSTANCE.voteTrackerX, ModConfig.INSTANCE.voteTrackerY, VOTE_WIDTH, VOTE_HEIGHT, draggingVote);
        }
        if (ModConfig.INSTANCE.enableChatGamesTracker) {
            drawBox(context, ModConfig.INSTANCE.chatGamesX, ModConfig.INSTANCE.chatGamesY, CHAT_WIDTH, CHAT_HEIGHT, draggingChat);
        }
        if (ModConfig.INSTANCE.enableSeismicSlamTimer) {
            drawBox(context, ModConfig.INSTANCE.seismicSlamX, ModConfig.INSTANCE.seismicSlamY, SEISMIC_WIDTH, SEISMIC_HEIGHT, draggingSeismic);
        }

        if (ModConfig.INSTANCE.enableVoteTracker) {
            VoteTracker.render(context, client, ModConfig.INSTANCE.voteTrackerX, ModConfig.INSTANCE.voteTrackerY,
                    ModConfig.INSTANCE.voteTrackerRightAligned);
        }
        if (ModConfig.INSTANCE.enableChatGamesTracker) {
            ChatGamesTracker.render(context, client, ModConfig.INSTANCE.chatGamesX, ModConfig.INSTANCE.chatGamesY,
                    ModConfig.INSTANCE.chatGamesRightAligned);
        }
        if (ModConfig.INSTANCE.enableSeismicSlamTimer) {
            SeismicSlamTracker.render(context, client, ModConfig.INSTANCE.seismicSlamX, ModConfig.INSTANCE.seismicSlamY,
                    ModConfig.INSTANCE.seismicSlamRightAligned, true);
        }

        if (eyeOpen) {
            context.fill(panelX - 1, panelY - 1, panelX + PANEL_W + 1, panelY + panelH + 1, 0xFFAAAAAA);
            context.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xEE101010);
        }

        super.render(context, mouseX, mouseY, delta);

        drawEyeIcon(context);
        drawFlipHint(context);

        context.drawCenteredTextWithShadow(this.textRenderer, "Drag the boxes to move them. Right-click a box to flip its alignment.", this.width / 2, this.height - 50, 0xFFFFFF);
    }

    private void drawEyeIcon(DrawContext context) {
        int ex = eyeButton.getX() + 3;
        int ey = eyeButton.getY() + 6;
        int[][] rows = {{4, 6}, {2, 10}, {1, 12}, {0, 14}, {0, 14}, {1, 12}, {2, 10}, {4, 6}};
        for (int i = 0; i < rows.length; i++) {
            context.fill(ex + rows[i][0], ey + i, ex + rows[i][0] + rows[i][1], ey + i + 1, 0xFFFFFFFF);
        }
        context.fill(ex + 5, ey + 2, ex + 9, ey + 6, 0xFF202020);
    }

    private void drawFlipHint(DrawContext context) {
        String hint = "Right click to flip the object horizontally";

        int buttonLeft = this.width / 2 - 100;
        int available = buttonLeft - 6 - 4;
        int textWidth = this.textRenderer.getWidth(hint);

        float scale = Math.min(0.75f, available / (float) textWidth);
        if (scale < 0.4f) return;

        float x = buttonLeft - 6 - textWidth * scale;
        float y = 10 + (20 - 9 * scale) / 2f;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);
        context.drawTextWithShadow(this.textRenderer, hint, 0, 0, 0xFFAAAAAA);
        context.getMatrices().popMatrix();
    }

    private void drawBox(DrawContext context, int x, int y, int width, int height, boolean isDragging) {
        int color = isDragging ? 0xFF00FF00 : 0x55FFFFFF;
        context.fill(x - 2, y - 2, x + width + 2, y, color);
        context.fill(x - 2, y + height, x + width + 2, y + height + 2, color);
        context.fill(x - 2, y, x, y + height, color);
        context.fill(x + width, y, x + width + 2, y + height, color);
    }

    private boolean clearFocus(boolean handled) {
        this.setFocused(null);
        return handled;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();

        boolean inPanel = eyeOpen && mouseX >= panelX && mouseX <= panelX + PANEL_W
                && mouseY >= panelY && mouseY <= panelY + panelH;

        boolean overVote = ModConfig.INSTANCE.enableVoteTracker
                && isHovered(mouseX, mouseY, ModConfig.INSTANCE.voteTrackerX, ModConfig.INSTANCE.voteTrackerY, VOTE_WIDTH, VOTE_HEIGHT);
        boolean overChat = ModConfig.INSTANCE.enableChatGamesTracker
                && isHovered(mouseX, mouseY, ModConfig.INSTANCE.chatGamesX, ModConfig.INSTANCE.chatGamesY, CHAT_WIDTH, CHAT_HEIGHT);
        boolean overSeismic = ModConfig.INSTANCE.enableSeismicSlamTimer
                && isHovered(mouseX, mouseY, ModConfig.INSTANCE.seismicSlamX, ModConfig.INSTANCE.seismicSlamY, SEISMIC_WIDTH, SEISMIC_HEIGHT);

        if (click.button() == 0) {
            if (inPanel) {
                return clearFocus(super.mouseClicked(click, doubled));
            }

            if (overVote) {
                this.setFocused(null);
                draggingVote = true;
                dragOffsetX = (int) mouseX - ModConfig.INSTANCE.voteTrackerX;
                dragOffsetY = (int) mouseY - ModConfig.INSTANCE.voteTrackerY;
                return true;
            }
            if (overChat) {
                this.setFocused(null);
                draggingChat = true;
                dragOffsetX = (int) mouseX - ModConfig.INSTANCE.chatGamesX;
                dragOffsetY = (int) mouseY - ModConfig.INSTANCE.chatGamesY;
                return true;
            }
            if (overSeismic) {
                this.setFocused(null);
                draggingSeismic = true;
                dragOffsetX = (int) mouseX - ModConfig.INSTANCE.seismicSlamX;
                dragOffsetY = (int) mouseY - ModConfig.INSTANCE.seismicSlamY;
                return true;
            }
        } else if (click.button() == 1 && !inPanel) {
            if (overVote) {
                ModConfig.INSTANCE.voteTrackerRightAligned = !ModConfig.INSTANCE.voteTrackerRightAligned;
                ModConfig.INSTANCE.save();
                return true;
            }
            if (overChat) {
                ModConfig.INSTANCE.chatGamesRightAligned = !ModConfig.INSTANCE.chatGamesRightAligned;
                ModConfig.INSTANCE.save();
                return true;
            }
            if (overSeismic) {
                ModConfig.INSTANCE.seismicSlamRightAligned = !ModConfig.INSTANCE.seismicSlamRightAligned;
                ModConfig.INSTANCE.save();
                return true;
            }
        }
        return clearFocus(super.mouseClicked(click, doubled));
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        double mouseX = click.x();
        double mouseY = click.y();

        if (draggingVote) {
            ModConfig.INSTANCE.voteTrackerX = (int) mouseX - dragOffsetX;
            ModConfig.INSTANCE.voteTrackerY = (int) mouseY - dragOffsetY;
            return true;
        }
        if (draggingChat) {
            ModConfig.INSTANCE.chatGamesX = (int) mouseX - dragOffsetX;
            ModConfig.INSTANCE.chatGamesY = (int) mouseY - dragOffsetY;
            return true;
        }
        if (draggingSeismic) {
            ModConfig.INSTANCE.seismicSlamX = (int) mouseX - dragOffsetX;
            ModConfig.INSTANCE.seismicSlamY = (int) mouseY - dragOffsetY;
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0) {
            draggingVote = false;
            draggingChat = false;
            draggingSeismic = false;
        }
        return super.mouseReleased(click);
    }

    private boolean isHovered(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x - 2 && mouseX <= x + width + 2 && mouseY >= y - 2 && mouseY <= y + height + 2;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}