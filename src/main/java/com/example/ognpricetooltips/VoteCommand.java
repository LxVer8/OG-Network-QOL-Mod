package com.example.ognpricetooltips;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class VoteCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("qol")
                .then(ClientCommandManager.literal("vote")
                    .then(ClientCommandManager.literal("complete")
                        .then(ClientCommandManager.literal("MinecraftServerList").executes(context -> completeVote(context.getSource(), "MinecraftServerList")))
                        .then(ClientCommandManager.literal("MinecraftBestServers").executes(context -> completeVote(context.getSource(), "MinecraftBestServers")))
                        .then(ClientCommandManager.literal("BlockAtlas").executes(context -> completeVote(context.getSource(), "BlockAtlas")))
                    )
                    .then(ClientCommandManager.literal("timer")
                        .then(ClientCommandManager.literal("MinecraftServerList")
                            .then(ClientCommandManager.argument("hours", IntegerArgumentType.integer(0))
                                .then(ClientCommandManager.argument("minutes", IntegerArgumentType.integer(0, 59))
                                    .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(0, 59))
                                        .executes(context -> setTimer(context.getSource(), "MinecraftServerList",
                                            IntegerArgumentType.getInteger(context, "hours"),
                                            IntegerArgumentType.getInteger(context, "minutes"),
                                            IntegerArgumentType.getInteger(context, "seconds")))
                                    )
                                )
                            )
                        )
                        .then(ClientCommandManager.literal("MinecraftBestServers")
                            .then(ClientCommandManager.argument("hours", IntegerArgumentType.integer(0))
                                .then(ClientCommandManager.argument("minutes", IntegerArgumentType.integer(0, 59))
                                    .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(0, 59))
                                        .executes(context -> setTimer(context.getSource(), "MinecraftBestServers",
                                            IntegerArgumentType.getInteger(context, "hours"),
                                            IntegerArgumentType.getInteger(context, "minutes"),
                                            IntegerArgumentType.getInteger(context, "seconds")))
                                    )
                                )
                            )
                        )
                        .then(ClientCommandManager.literal("BlockAtlas")
                            .then(ClientCommandManager.argument("hours", IntegerArgumentType.integer(0))
                                .then(ClientCommandManager.argument("minutes", IntegerArgumentType.integer(0, 59))
                                    .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(0, 59))
                                        .executes(context -> setTimer(context.getSource(), "BlockAtlas",
                                            IntegerArgumentType.getInteger(context, "hours"),
                                            IntegerArgumentType.getInteger(context, "minutes"),
                                            IntegerArgumentType.getInteger(context, "seconds")))
                                    )
                                )
                            )
                        )
                    )
                )
            );
        });
    }

    private static int completeVote(FabricClientCommandSource source, String website) {
        long now = System.currentTimeMillis();
        long next24h = now + (24 * 60 * 60 * 1000);

        String message;

        if (website.equals("MinecraftServerList")) {
            ModConfig.INSTANCE.lastVotedMinecraftMP = now;
            ModConfig.INSTANCE.customTimerMSL = 0;
            ModConfig.INSTANCE.notifiedMSL = false;
            message = "Marked MinecraftServerList as voted. Counting down to next 2 AM server time.";
        } else if (website.equals("MinecraftBestServers")) {
            ModConfig.INSTANCE.lastVotedMBS = now;
            ModConfig.INSTANCE.customTimerMBS = 0;
            ModConfig.INSTANCE.notifiedMBS = false;
            message = "Marked MinecraftBestServers as voted. Counting down to next 9 PM server time.";
        } else if (website.equals("BlockAtlas")) {
            ModConfig.INSTANCE.lastVotedBlockAtlas = now;
            ModConfig.INSTANCE.customTimerBA = next24h;
            ModConfig.INSTANCE.notifiedBA = false;
            message = "Marked BlockAtlas as voted. Timer set to 24h.";
        } else {
            return 0;
        }

        ModConfig.INSTANCE.save();
        source.sendFeedback(Text.literal(message).formatted(Formatting.GREEN));
        return 1;
    }

    private static int setTimer(FabricClientCommandSource source, String website, int hours, int minutes, int seconds) {
        long targetTime = System.currentTimeMillis() + (hours * 60L * 60L * 1000L) + (minutes * 60L * 1000L) + (seconds * 1000L);

        if (website.equals("MinecraftServerList")) {
            ModConfig.INSTANCE.customTimerMSL = targetTime;
            ModConfig.INSTANCE.notifiedMSL = false;
        } else if (website.equals("MinecraftBestServers")) {
            ModConfig.INSTANCE.customTimerMBS = targetTime;
            ModConfig.INSTANCE.notifiedMBS = false;
        } else if (website.equals("BlockAtlas")) {
            ModConfig.INSTANCE.customTimerBA = targetTime;
            ModConfig.INSTANCE.notifiedBA = false;
        }

        ModConfig.INSTANCE.save();
        source.sendFeedback(Text.literal("Set " + website + " timer to " + hours + "h " + minutes + "m " + seconds + "s.").formatted(Formatting.GOLD));
        return 1;
    }
}