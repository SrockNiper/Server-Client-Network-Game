package cz.zcu.kiv.ups.semestralka.upssemestralka.model;

/**
 * Konstanty Constants - Centralized definition of all protocol magic strings, command IDs, and error codes
 * 
 * This class consolidates all hardcoded protocol values to improve maintainability
 * and prevent inconsistencies across the codebase.
 */
public final class Konstanty {
    

    private Konstanty() {
        throw new AssertionError("Konstanty is a utility class and should not be instantiated");
    }
    

    public static final String MAGIC = "DCG";
    
    // ========== COMMAND IDs ==========
    
    /**
     * CMD 0: Disconnect notification
     * Server->Klient: Odhlášení od uživatele
     */
    public static final int CMD_DISCONNECT = 0;

    /**
     * CMD 1: List/Join lobby - vypsání místností
     * Body: Nickname
     */
    public static final int CMD_LIST_ROOMS = 1;
    
    /**
     * CMD 2: Join/Create room
     * Body: Room ID (number) or "C;Capacity;Score" for creating new room
     */
    public static final int CMD_JOIN_ROOM = 2;
    
    /**
     * CMD 3: Pass selected dice and end turn
     * Body: 6-char mask of selected dice (e.g., "105000")
     */
    public static final int CMD_GAME_PASS = 3;
    
    /**
     * CMD 4: Reroll unselected dice
     * Body: 6-char mask of selected dice to keep (e.g., "105000")
     */
    public static final int CMD_GAME_REROLL = 4;
    
    /**
     * CMD 5: Claim victory
     * Body: 6-char mask of selected dice
     */
    public static final int CMD_GAME_WIN = 5;
    
    /**
     * CMD 6: Leave room and return to lobby
     * Body: Empty
     */
    public static final int CMD_RETURN_TO_LOBBY = 6;
    
    /**
     * CMD 7: Vote/Event
     * Server->Client: Game events (TURN, ROUND, SCORE, FARKLE, WIN, READY, START, JOINED, LEFT)
     * Client->Server: Vote response ("VOTE KICK", "VOTE WAIT") or Ready signal (empty)
     */
    public static final int CMD_VOTE = 7;
    
    /**
     * CMD 8: Acknowledgment (reliability layer)
     * Body: Message ID
     */
    public static final int CMD_ACK = 8;
    
    /**
     * CMD 9: Ping/Pong keepalive
     * Body: "PING" or "PING <timestamp>"
     */
    public static final int CMD_PING = 9;

    /**
     * Délka zprávy: MAGIC(3) + CMD(1) + LEN(2) = 6 bytes
     */
    public static final int HEADER_LENGTH = 6;

}

