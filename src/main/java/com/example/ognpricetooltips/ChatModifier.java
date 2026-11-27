package com.example.ognpricetooltips;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ChatModifier {

    private record Seg(String text, Style style) {}

    private record Parsed(List<Seg> segs, int nameStart, int nameEnd, int nameFrom, int msgFrom,
                          String realName, Seg rankGlyph, Seg rankSpace) {}

    private static final Style WHITE = Style.EMPTY.withColor(Formatting.WHITE);

    public static void init() {
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
            if (overlay) return message;
            try {
                return modify(message);
            } catch (Exception e) {
                e.printStackTrace();
                return message;
            }
        });
    }

    private static Text modify(Text message) {
        ModConfig cfg = ModConfig.INSTANCE;

        boolean messageColor = cfg.chatMessageColorRemoverActive();
        boolean nameColor = cfg.chatNameColorRemoverActive();
        boolean removeRank = cfg.chatRankRemoverActive();
        boolean removeTag = cfg.chatTagRemoverActive();
        boolean realName = cfg.realNameDisplayActive();

        boolean anyChatMod = messageColor || nameColor || removeRank || removeTag || realName;
        if (!anyChatMod) return message;

        Parsed p = parse(message);
        if (p == null) return message;

        return buildCustom(p, messageColor, nameColor, removeRank, removeTag, realName);
    }

    private static Parsed parse(Text message) {
        List<Seg> segs = new ArrayList<>();
        message.<Object>visit((style, str) -> {
            if (!str.isEmpty()) segs.add(new Seg(str, style));
            return Optional.empty();
        }, Style.EMPTY);

        int nameStart = -1;
        String realName = null;
        for (int i = 0; i < segs.size(); i++) {
            String cmd = clickCommand(segs.get(i).style());
            if (cmd != null && cmd.startsWith("/msg ")) {
                nameStart = i;
                realName = cmd.substring(5).trim();
                break;
            }
        }

        int barIdx = -1;
        if (nameStart != -1) {
            int nameEnd = nameStart;
            while (nameEnd < segs.size()) {
                String cmd = clickCommand(segs.get(nameEnd).style());
                if (cmd == null || !cmd.startsWith("/msg ")) break;
                nameEnd++;
            }

            for (int i = nameEnd; i < segs.size(); i++) {
                if (segs.get(i).text().trim().equals("»")) {
                    barIdx = i;
                    break;
                }
            }
        }

        if (nameStart != -1 && barIdx != -1 && realName != null) {
            int nameEnd = nameStart;
            while (nameEnd < segs.size()) {
                String cmd = clickCommand(segs.get(nameEnd).style());
                if (cmd == null || !cmd.startsWith("/msg ")) break;
                nameEnd++;
            }

            int nameFrom = nameStart;
            Seg rankGlyph = null;
            Seg rankSpace = null;
            Seg first = segs.get(nameStart);
            if (first.text().length() == 1 && Character.getType(first.text().charAt(0)) == Character.PRIVATE_USE) {
                rankGlyph = first;
                nameFrom++;
                if (nameFrom < nameEnd && segs.get(nameFrom).text().isBlank()) {
                    rankSpace = segs.get(nameFrom);
                    nameFrom++;
                }
            }

            if (nameFrom < nameEnd) {
                int msgFrom = barIdx + 1;
                if (msgFrom < segs.size() && segs.get(msgFrom).text().isBlank()) msgFrom++;
                return new Parsed(segs, nameStart, nameEnd, nameFrom, msgFrom, realName, rankGlyph, rankSpace);
            }
        }

        return null;
    }

    private static Text buildCustom(Parsed p, boolean messageColor, boolean nameColor,
                                    boolean removeRank, boolean removeTag, boolean realName) {
        List<Seg> segs = p.segs();
        MutableText out = Text.empty();

        for (int i = 0; i < p.nameStart(); i++) {
            Seg s = segs.get(i);
            boolean isTag = "/tags".equals(clickCommand(s.style()));
            if (isTag && removeTag) continue;
            out.append(piece(s.text(), s.style()));
        }

        if (p.rankGlyph() != null && !removeRank) {
            out.append(piece(p.rankGlyph().text(), p.rankGlyph().style()));
            if (p.rankSpace() != null) out.append(piece(p.rankSpace().text(), p.rankSpace().style()));
        }

        if (realName) {
            Style nameStyle = WHITE
                    .withHoverEvent(segs.get(p.nameFrom()).style().getHoverEvent())
                    .withClickEvent(segs.get(p.nameFrom()).style().getClickEvent());
            out.append(piece(p.realName(), nameStyle));
        } else {
            for (int i = p.nameFrom(); i < p.nameEnd(); i++) {
                Seg s = segs.get(i);
                Style style = nameColor ? s.style().withColor(Formatting.WHITE) : s.style();
                out.append(piece(s.text(), style));
            }
        }

        for (int i = p.nameEnd(); i < p.msgFrom(); i++) {
            Seg s = segs.get(i);
            out.append(piece(s.text(), s.style()));
        }

        for (int i = p.msgFrom(); i < segs.size(); i++) {
            Seg s = segs.get(i);
            Style style = s.style();
            if (messageColor && !isItemHover(style)) {
                style = style.withColor(Formatting.WHITE);
            }
            out.append(piece(s.text(), style));
        }

        return out;
    }

    private static Text piece(String text, Style style) {
        return Text.literal(text).setStyle(style);
    }

    private static String clickCommand(Style style) {
        Object click = style.getClickEvent();
        if (click == null) return null;
        String s = click.toString();
        int i = s.indexOf("[command=");
        if (i < 0 || !s.endsWith("]")) return null;
        return s.substring(i + "[command=".length(), s.length() - 1);
    }

    private static boolean isItemHover(Style style) {
        Object hover = style.getHoverEvent();
        return hover != null && hover.toString().contains("item=");
    }
}