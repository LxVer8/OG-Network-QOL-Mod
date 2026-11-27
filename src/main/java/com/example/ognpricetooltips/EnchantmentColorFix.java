package com.example.ognpricetooltips;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EnchantmentColorFix implements ClientModInitializer {

    private static final Pattern LEVEL_PATTERN = Pattern.compile("enchantment\\.level\\.(\\d+)");

    @Override
    public void onInitializeClient() {
        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) -> {
            if (!ModConfig.INSTANCE.enableEnchantmentColorFix && 
                !ModConfig.INSTANCE.enableOverMaxIndicator && 
                !ModConfig.INSTANCE.enableHighLevelEnchantFix && 
                !ModConfig.INSTANCE.enableRomanToArabicFix) return;

            Map<String, Integer> activeEnchants = new HashMap<>();
            
            ItemEnchantmentsComponent enchants = stack.get(DataComponentTypes.ENCHANTMENTS);
            if (enchants != null) {
                for (RegistryEntry<Enchantment> entry : enchants.getEnchantments()) {
                    activeEnchants.put(normalize(entry.value().description().getString()), enchants.getLevel(entry));
                }
            }
            
            ItemEnchantmentsComponent stored = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
            if (stored != null) {
                for (RegistryEntry<Enchantment> entry : stored.getEnchantments()) {
                    activeEnchants.put(normalize(entry.value().description().getString()), stored.getLevel(entry));
                }
            }

            if (activeEnchants.isEmpty()) {
                return;
            }

            List<String> sortedRunes = new ArrayList<>(RuneHighlighter.MAX_LEVELS.keySet());
            sortedRunes.sort((a, b) -> Integer.compare(b.length(), a.length()));

            for (int i = 0; i < lines.size(); i++) {
                Text line = lines.get(i);
                String lineText = line.getString();
                String originalText = lineText;
                
                if (ModConfig.INSTANCE.enableHighLevelEnchantFix) {
                    Matcher matcher = LEVEL_PATTERN.matcher(lineText);
                    if (matcher.find()) {
                        int lvl = Integer.parseInt(matcher.group(1));
                        String roman = toRoman(lvl);
                        lineText = lineText.replace("enchantment.level." + lvl, roman);
                    }
                }

                String normalizedLine = normalize(lineText);
                String matchedRune = null;
                int level = -1;

                for (String runeName : sortedRunes) {
                    String normalizedRuneName = normalize(runeName);
                    if (normalizedLine.contains(normalizedRuneName)) {
                        matchedRune = runeName;
                        level = activeEnchants.getOrDefault(normalizedRuneName, RuneHighlighter.extractLevel(lineText));
                        break;
                    }
                }

                if (matchedRune == null) {
                    continue;
                }

                if (level > 1 && ModConfig.INSTANCE.enableHighLevelEnchantFix) {
                    String roman = toRoman(level);
                    boolean hasLevel = false;
                    String[] parts = lineText.split(" ");
                    if (parts.length > 1) {
                        String lastPart = parts[parts.length - 1];
                        if (lastPart.equals(roman) || lastPart.equals(String.valueOf(level))) {
                            hasLevel = true;
                        }
                    }
                    
                    if (!hasLevel) {
                        lineText = lineText + " " + roman;
                        normalizedLine = normalize(lineText);
                    }
                }

                if (ModConfig.INSTANCE.enableRomanToArabicFix) {
                    String[] parts = lineText.split(" ");
                    if (parts.length > 1) {
                        String lastPart = parts[parts.length - 1];
                        if (lastPart.matches("[IVXLCDM]+")) {
                            int arabic = romanToInt(lastPart);
                            if (arabic > 0) {
                                lineText = lineText.substring(0, lineText.length() - lastPart.length()) + arabic;
                                normalizedLine = normalize(lineText);
                            }
                        }
                    }
                }

                Integer maxLevel = RuneHighlighter.MAX_LEVELS.get(matchedRune);
                
                boolean isOverMax = ModConfig.INSTANCE.enableOverMaxIndicator && (maxLevel != null && level > maxLevel);
                String newLineText = lineText;
                
                if (isOverMax) {
                    int extraLevels = level - maxLevel;
                    newLineText = "+".repeat(extraLevels) + lineText;
                }
                
                Formatting color = ModConfig.INSTANCE.enableEnchantmentColorFix ? RuneHighlighter.getRuneColor(matchedRune, level) : null;
                
                if (color != null) {
                    lines.set(i, Text.literal(newLineText).formatted(color));
                } else if (!newLineText.equals(originalText)) {
                    lines.set(i, Text.literal(newLineText).setStyle(line.getStyle()));
                }
            }
        });
    }

    private static String toRoman(int number) {
        return switch (number) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; case 10 -> "X";
            case 11 -> "XI"; case 12 -> "XII"; case 13 -> "XIII"; case 14 -> "XIV"; case 15 -> "XV";
            default -> String.valueOf(number);
        };
    }

    private static int romanToInt(String s) {
        int result = 0;
        for (int i = 0; i < s.length(); i++) {
            int current = valueOf(s.charAt(i));
            if (current == 0) return 0;
            if (i + 1 < s.length() && valueOf(s.charAt(i + 1)) > current) result -= current;
            else result += current;
        }
        return result;
    }

    private static int valueOf(char c) {
        return switch (c) {
            case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50;
            case 'C' -> 100; case 'D' -> 500; case 'M' -> 1000; default -> 0;
        };
    }

    private static String normalize(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}