package cz.zcu.kiv.ups.semestralka.upssemestralka.utils;

/**
 * Pomocná třída pro sestavování síťových paketů.
 * Zajišťuje správné formátování zpráv podle protokolu.
 */
public final class PacketBuilder {

    private PacketBuilder() {
    }

    /**
     * Sestavení paketu pro přihlášení do lobby.
     * @param nickname Přezdívka hráče
     * @return Řetězec s přezdívkou
     */
    public static String buildLoginPacket(String nickname) {
        return nickname != null ? nickname : "";
    }

    /**
     * Sestavení paketu pro připojení do místnosti.
     * @param roomId ID místnosti
     * @return ID místnosti jako řetězec
     */
    public static String buildJoinRoomPacket(int roomId) {
        return String.valueOf(roomId);
    }

    /**
     * Sestavení paketu pro vytvoření nové místnosti.
     * Formát: "C;Kapacita;VitezneSkore"
     * @param capacity Maximální počet hráčů (2-6)
     * @param winningScore Skóre potřebné k výhře
     * @return Formátovaný řetězec pro vytvoření místnosti
     */
    public static String buildCreateRoomPacket(int capacity, int winningScore) {
        return "C;" + capacity + ";" + winningScore;
    }

    /**
     * Sestavení paketu pro předání tahu (konec tahu).
     * @param diceMask Maska vybraných kostek (6 znaků)
     * @return Maska kostek
     */
    public static String buildGamePassPacket(String diceMask) {
        if (diceMask == null || diceMask.length() != 6) {
            throw new IllegalArgumentException("Maska kostek musí mít přesně 6 znaků");
        }
        return diceMask;
    }

    /**
     * Sestavení paketu pro přehození kostek (reroll).
     * @param diceMask Maska kostek, které se mají ponechat
     * @return Maska kostek
     */
    public static String buildGameRerollPacket(String diceMask) {
        if (diceMask == null || diceMask.length() != 6) {
            throw new IllegalArgumentException("Maska kostek musí mít přesně 6 znaků");
        }
        return diceMask;
    }

    /**
     * Sestavení paketu pro nárokování výhry.
     * @param diceMask Maska vybraných kostek
     * @return Maska kostek
     */
    public static String buildGameWinPacket(String diceMask) {
        if (diceMask == null || diceMask.length() != 6) {
            throw new IllegalArgumentException("Maska kostek musí mít přesně 6 znaků");
        }
        return diceMask;
    }

    /**
     * Sestavení paketu připravenosti (READY).
     * @return Prázdný řetězec
     */
    public static String buildReadyPacket() {
        return "";
    }

    /**
     * Sestavení paketu pro hlasování o vyhození.
     * @return "VOTE KICK"
     */
    public static String buildVoteKickPacket() {
        return "VOTE KICK";
    }

    /**
     * Sestavení paketu pro hlasování o čekání.
     * @return "VOTE WAIT"
     */
    public static String buildVoteWaitPacket() {
        return "VOTE WAIT";
    }

    /**
     * Sestavení číselného hlasovacího paketu.
     * @param voteKick true pro vyhození, false pro čekání
     * @return "1" nebo "0"
     */
    public static String buildNumericVotePacket(boolean voteKick) {
        return voteKick ? "1" : "0";
    }

    /**
     * Kontrola, zda je maska prázdná.
     * @param mask Maska kostek
     * @return true, pokud maska obsahuje jen nuly
     */
    public static boolean isEmptyMask(String mask) {
        return "000000".equals(mask);
    }

}