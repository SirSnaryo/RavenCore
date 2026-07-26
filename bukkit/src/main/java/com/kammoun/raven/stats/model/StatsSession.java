package com.kammoun.raven.stats.model;

/**
 * Allocation-light, per-player state. Hot gameplay events only take this
 * player's monitor and increment primitive fields; snapshots are allocated
 * only when a GUI/placeholder/database cycle actually requests one.
 */
public final class StatsSession {
    private final java.util.UUID uuid;
    private String playerName;

    private long kills;
    private long deaths;
    private long blocksBroken;
    private long blocksPlaced;
    private long mobKills;
    private long playTime;
    private long itemsCrafted;
    private long jumps;
    private long chatMessages;
    private double moneySpent;
    private double moneyMade;

    private StatsDelta pending = new StatsDelta();
    private long playTimeMark = System.currentTimeMillis();

    public StatsSession(StatsSnapshot snapshot) {
        uuid = snapshot.uuid();
        replaceTotals(snapshot);
    }

    public synchronized void increment(StatsType type, double amount) {
        if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) return;
        pending.add(type, amount);
        addToTotal(type, amount);
    }

    public synchronized void capturePlayTime() {
        long now = System.currentTimeMillis();
        long seconds = (now - playTimeMark) / 1000L;
        if (seconds <= 0) return;
        pending.add(StatsType.PLAY_TIME, seconds);
        playTime += seconds;
        playTimeMark += seconds * 1000L;
    }

    public synchronized StatsSnapshot snapshot() {
        return new StatsSnapshot(uuid, playerName, kills, deaths, blocksBroken, blocksPlaced,
                mobKills, playTime, itemsCrafted, jumps, chatMessages, moneySpent, moneyMade);
    }

    public synchronized String playerName() {
        return playerName;
    }

    public synchronized StatsDelta drain() {
        StatsDelta result = pending;
        pending = new StatsDelta();
        return result;
    }

    public synchronized void restore(StatsDelta failed) {
        pending.merge(failed);
    }

    /**
     * Reconciles a database total without losing increments that have not yet
     * been included in the database.
     */
    public synchronized void replacePersisted(StatsSnapshot persisted) {
        playerName = persisted.playerName();
        kills = persisted.kills() + pending.kills();
        deaths = persisted.deaths() + pending.deaths();
        blocksBroken = persisted.blocksBroken() + pending.blocksBroken();
        blocksPlaced = persisted.blocksPlaced() + pending.blocksPlaced();
        mobKills = persisted.mobKills() + pending.mobKills();
        playTime = persisted.playTime() + pending.playTime();
        itemsCrafted = persisted.itemsCrafted() + pending.itemsCrafted();
        jumps = persisted.jumps() + pending.jumps();
        chatMessages = persisted.chatMessages() + pending.chatMessages();
        moneySpent = persisted.moneySpent() + pending.moneySpent();
        moneyMade = persisted.moneyMade() + pending.moneyMade();
    }

    private void replaceTotals(StatsSnapshot snapshot) {
        playerName = snapshot.playerName();
        kills = snapshot.kills();
        deaths = snapshot.deaths();
        blocksBroken = snapshot.blocksBroken();
        blocksPlaced = snapshot.blocksPlaced();
        mobKills = snapshot.mobKills();
        playTime = snapshot.playTime();
        itemsCrafted = snapshot.itemsCrafted();
        jumps = snapshot.jumps();
        chatMessages = snapshot.chatMessages();
        moneySpent = snapshot.moneySpent();
        moneyMade = snapshot.moneyMade();
    }

    private void addToTotal(StatsType type, double amount) {
        switch (type) {
            case KILLS -> kills += (long) amount;
            case DEATHS -> deaths += (long) amount;
            case BLOCKS_BROKEN -> blocksBroken += (long) amount;
            case BLOCKS_PLACED -> blocksPlaced += (long) amount;
            case MOB_KILLS -> mobKills += (long) amount;
            case PLAY_TIME -> playTime += (long) amount;
            case ITEMS_CRAFTED -> itemsCrafted += (long) amount;
            case JUMPS -> jumps += (long) amount;
            case CHAT_MESSAGES -> chatMessages += (long) amount;
            case MONEY_SPENT -> moneySpent += amount;
            case MONEY_MADE -> moneyMade += amount;
        }
    }
}
