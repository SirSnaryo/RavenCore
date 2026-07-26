package com.kammoun.raven.stats.model;

public final class StatsDelta {
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

    public void add(StatsType type, double amount) {
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

    public void merge(StatsDelta other) {
        kills += other.kills;
        deaths += other.deaths;
        blocksBroken += other.blocksBroken;
        blocksPlaced += other.blocksPlaced;
        mobKills += other.mobKills;
        playTime += other.playTime;
        itemsCrafted += other.itemsCrafted;
        jumps += other.jumps;
        chatMessages += other.chatMessages;
        moneySpent += other.moneySpent;
        moneyMade += other.moneyMade;
    }

    public boolean isEmpty() {
        return kills == 0 && deaths == 0 && blocksBroken == 0 && blocksPlaced == 0
                && mobKills == 0 && playTime == 0 && itemsCrafted == 0 && jumps == 0
                && chatMessages == 0 && moneySpent == 0D && moneyMade == 0D;
    }

    public long kills() { return kills; }
    public long deaths() { return deaths; }
    public long blocksBroken() { return blocksBroken; }
    public long blocksPlaced() { return blocksPlaced; }
    public long mobKills() { return mobKills; }
    public long playTime() { return playTime; }
    public long itemsCrafted() { return itemsCrafted; }
    public long jumps() { return jumps; }
    public long chatMessages() { return chatMessages; }
    public double moneySpent() { return moneySpent; }
    public double moneyMade() { return moneyMade; }
}
