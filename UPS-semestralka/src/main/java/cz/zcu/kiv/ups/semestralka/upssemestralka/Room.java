package cz.zcu.kiv.ups.semestralka.upssemestralka;

/**
 * Třída reprezentující herní místnost
 */
public class Room {
    private int id;
    private String name;
    private int currentPlayers;
    private int maxPlayers;
    private boolean isFull;
    public int winningScore;

    public Room(String name, int currentPlayers, int maxPlayers) {
        this.name = name;
        this.currentPlayers = currentPlayers;
        this.maxPlayers = maxPlayers;
        this.isFull = currentPlayers >= maxPlayers;
    }

    /**
     * Konstruktor pro vytvoření místnosti z dat serveru
     * @param id ID místnosti
     * @param currentPlayers Aktuální počet hráčů
     * @param maxPlayers Maximální počet hráčů
     * @param winningScore Vítězné skóre
     */
    public Room(int id, int currentPlayers, int maxPlayers, int winningScore) {
        this.id = id;
        this.name = "Místnost " + id;
        this.currentPlayers = currentPlayers;
        this.maxPlayers = maxPlayers;
        this.winningScore = winningScore;
        this.isFull = currentPlayers >= maxPlayers;
    }
    public int getId(){return id;}
    public String getName() {
        return name;
    }

    public int getCurrentPlayers() {
        return currentPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public boolean isFull() {
        return isFull;
    }

    public int getWinningScore() {
        return winningScore;
    }

    public void setWinningScore(int winningScore) {
        this.winningScore = winningScore;
    }

    public void setCurrentPlayers(int currentPlayers) {
        this.currentPlayers = currentPlayers;
        this.isFull = currentPlayers >= maxPlayers;
    }

    @Override
    public String toString() {
        String status = isFull ? " - PLNÁ" : "";
        return name + " (" + currentPlayers + "/" + maxPlayers + " hráči)" + status + " - Cíl: " + winningScore;
    }
}

