package com.example.ognpricetooltips;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
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

            List<String> activeEnchants = new ArrayList<>();
            
            ItemEnchantmentsComponent enchants = stack.get(DataComponentTypes.ENCHANTMENTS);
            if (enchants != null) {
                for (RegistryEntry<Enchantment> entry : enchants.getEnchantments()) {
                    activeEnchants.add(entry.value().description().getString().toLowerCase());
                }
            }
            
            ItemEnchantmentsComponent stored = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
            if (stored != null) {
                for (RegistryEntry<Enchantment> entry : stored.getEnchantments()) {
                    activeEnchants.add(entry.value().description().getString().toLowerCase());
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
                        int level = Integer.parseInt(matcher.group(1));
                        String roman = toRoman(level);
                        lineText = lineText.replace("enchantment.level." + level, roman);
                    }
                }

                String lowerLineText = lineText.toLowerCase();

                boolean isActiveEnchant = false;
                for (String active : activeEnchants) {
                    if (lowerLineText.contains(active)) {
                        isActiveEnchant = true;
                        break;
                    }
                }

                if (!isActiveEnchant) {
                    continue;
                }

                if (ModConfig.INSTANCE.enableRomanToArabicFix) {
                    String[] parts = lineText.split(" ");
                    if (parts.length > 1) {
                        String lastPart = parts[parts.length - 1];
                        if (lastPart.matches("[IVXLCDM]+")) {
                            int arabic = romanToInt(lastPart);
                            if (arabic > 0) {
                                lineText = lineText.substring(0, lineText.length() - lastPart.length()) + arabic;
                                lowerLineText = lineText.toLowerCase();
                            }
                        }
                    }
                }

                for (String runeName : sortedRunes) {
                    String lowerRuneName = runeName.toLowerCase();
                    
                    if (lowerLineText.contains(lowerRuneName)) {
                        int level = RuneHighlighter.extractLevel(lineText);
                        Integer maxLevel = RuneHighlighter.MAX_LEVELS.get(runeName);
                        
                        boolean isOverMax = ModConfig.INSTANCE.enableOverMaxIndicator && (maxLevel != null && level > maxLevel);
                        String newLineText = lineText;
                        
                        if (isOverMax) {
                            int extraLevels = level - maxLevel;
                            newLineText = "+".repeat(extraLevels) + lineText;
                        }
                        
                        Formatting color = ModConfig.INSTANCE.enableEnchantmentColorFix ? RuneHighlighter.getRuneColor(runeName, level) : null;
                        
                        if (color != null) {
                            lines.set(i, Text.literal(newLineText).formatted(color));
                        } else if (!newLineText.equals(originalText)) {
                            lines.set(i, Text.literal(newLineText).setStyle(line.getStyle()));
                        }
                        break; 
                    }
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
}