package cz.zcu.kiv.ups.semestralka.upssemestralka.model;

import cz.zcu.kiv.ups.semestralka.upssemestralka.utils.PacketBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Pravidla hry a validace tahů.
 */
public final class FarkleRules {
    

    private FarkleRules() {
        throw new AssertionError("Vytváření třídy FarkleRules není povoleno.");
    }

    private static final int STRAIGHT_SCORE = 1500;
    private static final int TRIPLE_ONE_BASE = 1000;
    private static final int TRIPLE_OTHER_BASE = 100;
    private static final int SINGLE_ONE_SCORE = 100;
    private static final int SINGLE_FIVE_SCORE = 50;

    /**
     * Kontrola validity vybraných kostek.
     *
     * @param mask Maska kostek (6 znaků, např. "105000")
     * @return true pokud výběr je validní
     */
    public static boolean isValidSelection(String mask) {
        if (mask == null || mask.length() != 6) {
            return false;
        }


        if (isEmptyMask(mask)) {
            return false;
        }


        Map<Integer, Integer> valueCounts = new HashMap<>();

        for (int i = 0; i < 6; i++) {
            int value = Character.getNumericValue(mask.charAt(i));
            if (value > 0 && value <= 6) {
                valueCounts.put(value, valueCounts.getOrDefault(value, 0) + 1);
            }
        }


        if (valueCounts.size() == 6 && valueCounts.values().stream().allMatch(count -> count == 1)) {
            return true;
        }


        for (Map.Entry<Integer, Integer> entry : valueCounts.entrySet()) {
            int value = entry.getKey();
            int count = entry.getValue();

            if (count >= 3) {

                continue;
            }


            if (value != 1 && value != 5) {

                return false;
            }
        }

        return true;
    }

    /**
     * Kontrola, zda je maska prázdná.
     *
     * @param mask Maska kostek
     * @return true pokud maska obsahuje pouze nuly
     */
    public static boolean isEmptyMask(String mask) {
        return PacketBuilder.isEmptyMask(mask);
    }

    /**
     * Výpočet skóre pro vybrané kostky.
     *
     * @param mask Maska kostek (6 znaků, např. "105000")
     * @return Celkové skóre
     */
    public static int calculateScore(String mask) {

        if (mask == null || mask.equals("000000") || mask.length() != 6) {
            return 0;
        }
        

        Map<Integer, Integer> valueCounts = new HashMap<>();
        
        for (int i = 0; i < 6; i++) {
            int value = Character.getNumericValue(mask.charAt(i));
            if (value > 0 && value <= 6) {
                valueCounts.put(value, valueCounts.getOrDefault(value, 0) + 1);
            }
        }
        

        if (valueCounts.size() == 6 && valueCounts.values().stream().allMatch(count -> count == 1)) {
            return STRAIGHT_SCORE;
        }
        
        int totalScore = 0;
        

        for (Map.Entry<Integer, Integer> entry : valueCounts.entrySet()) {
            int value = entry.getKey();
            int count = entry.getValue();
            
            if (count >= 3) {

                totalScore += calculateMultipleScore(value, count);
            } else {

                totalScore += calculateIndividualScore(value, count);
            }
        }
        
        return totalScore;
    }
    
    /**
     * Výpočet skóre pro tři a více stejných hodnot.
     *
     * @param value Hodnota kostky (1-6)
     * @param count Počet kostek s touto hodnotou (>= 3)
     * @return Skóre za tuto kombinaci
     */
    private static int calculateMultipleScore(int value, int count) {

        int baseScore = (value == 1) ? TRIPLE_ONE_BASE : value * TRIPLE_OTHER_BASE;
        

        switch (count) {
            case 3:
                return baseScore;
            case 4:
                return baseScore * 2;
            case 5:
                return baseScore * 4;
            case 6:
                return baseScore * 8;
            default:
                return 0;
        }
    }
    
    /**
     * Výpočet skóre pro jednotlivé kostky.
     *
     * @param value Hodnota kostky (1-6)
     * @param count Počet kostek s touto hodnotou (< 3)
     * @return Skóre za tyto kostky
     */
    private static int calculateIndividualScore(int value, int count) {
        if (value == 1) {
            return count * SINGLE_ONE_SCORE;
        } else if (value == 5) {
            return count * SINGLE_FIVE_SCORE;
        } else {
            return 0;
        }
    }
    
    /**
     * Kontrola, zda hráč vyhrál na základě celkového skóre.
     *
     * @param currentTotal Aktuální celkové skóre hráče
     * @param roundScore Skóre nasbírané v aktuálním kole
     * @param selectedScore Skóre z aktuálně vybraných kostek
     * @param winningScore Potřebné skóre k výhře
     * @return true pokud hráč vyhraje uložením těchto kostek
     */
    public static boolean wouldWin(int currentTotal, int roundScore, int selectedScore, int winningScore) {
        int projectedTotal = currentTotal + roundScore + selectedScore;
        return projectedTotal >= winningScore;
    }
    
    /**
     * Kontrola, zda tah povede k výhře.
     *
     * @param currentTotal Aktuální celkové skóre hráče
     * @param roundScore Skóre v aktuálním kole
     * @param mask Maska kostek
     * @param winningScore Potřebné skóre k výhře
     * @return true pokud tento tah vyhraje hru
     */
    public static boolean isWinningMove(int currentTotal, int roundScore, String mask, int winningScore) {
        int selectedScore = calculateScore(mask);
        return wouldWin(currentTotal, roundScore, selectedScore, winningScore);
    }

}

