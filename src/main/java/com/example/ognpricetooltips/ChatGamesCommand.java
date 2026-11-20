package com.example.ognpricetooltips;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ChatGamesCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("qol")
                        .then(ClientCommandManager.literal("chatgame")
                                .then(ClientCommandManager.literal("refresh")
                                        .executes(context -> {
                                            ChatGamesTracker.RefreshResult result = ChatGamesTracker.refreshFromChat();

                                            switch (result.status()) {
                                                case NOT_FOUND -> context.getSource().sendFeedback(
                                                        Text.literal("No chat game messages found in the chat since the game started. The timer was left as it was.")
                                                                .formatted(Formatting.RED));
                                                case WAITING -> context.getSource().sendFeedback(
                                                        Text.literal("A chat game is in progress. The timer will start when it ends.")
                                                                .formatted(Formatting.YELLOW));
                                                case TIMER -> {
                                                    String msg = result.leftMillis() > 0
                                                            ? "ChatGames timer refreshed: next game in " + format(result.leftMillis())
                                                                    + " (the last one ended " + format(result.agoMillis()) + " ago)."
                                                            : "ChatGames timer refreshed: the next game should start any moment (the last one ended "
                                                                    + format(result.agoMillis()) + " ago).";
                                                    context.getSource().sendFeedback(Text.literal(msg).formatted(Formatting.GREEN));
                                                }
                                            }

                                            if (!ModConfig.INSTANCE.enableChatGamesTracker) {
                                                context.getSource().sendFeedback(
                                                        Text.literal("The ChatGames tracker is turned off in the settings, so nothing is shown on screen.")
                                                                .formatted(Formatting.GRAY));
                                            }
                                            return result.status() == ChatGamesTracker.RefreshStatus.NOT_FOUND ? 0 : 1;
                                        })))));
    }

    private static String format(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) return hours + "h " + minutes + "m " + seconds + "s";
        return minutes + "m " + String.format("%02d", seconds) + "s";
    }
}