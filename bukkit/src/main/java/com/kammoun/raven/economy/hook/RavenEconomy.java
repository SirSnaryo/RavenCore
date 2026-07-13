package com.kammoun.raven.economy.hook;

import com.kammoun.raven.economy.service.EconomyService;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

import java.text.DecimalFormat;
import java.util.List;

public class RavenEconomy implements Economy {

    private final EconomyService service;
    private final String singular;
    private final String plural;
    private final int fractionalDigits;
    private final DecimalFormat format;

    public RavenEconomy(EconomyService service, String singular, String plural, int fractionalDigits) {
        this.service = service;
        this.singular = singular;
        this.plural = plural;
        this.fractionalDigits = fractionalDigits;
        String pattern = "#,##0" + (fractionalDigits > 0 ? "." + "0".repeat(fractionalDigits) : "");
        this.format = new DecimalFormat(pattern);
    }

    @Override public boolean isEnabled() { return true; }
    @Override public String getName() { return "RavenEconomy"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits() { return fractionalDigits; }
    @Override public String currencyNameSingular() { return singular; }
    @Override public String currencyNamePlural() { return plural; }

    @Override
    public String format(double amount) {
        return format.format(amount) + " " + (amount == 1.0 ? singular : plural);
    }

    @Override public boolean hasAccount(OfflinePlayer player) { return service.hasAccount(player.getUniqueId()); }
    @Override public boolean hasAccount(OfflinePlayer player, String world) { return hasAccount(player); }

    @Override public double getBalance(OfflinePlayer player) { return service.getBalance(player.getUniqueId()); }
    @Override public double getBalance(OfflinePlayer player, String world) { return getBalance(player); }

    @Override public boolean has(OfflinePlayer player, double amount) { return getBalance(player) >= amount; }
    @Override public boolean has(OfflinePlayer player, String world, double amount) { return has(player, amount); }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (amount < 0)
            return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Amount must be positive");
        if (!service.withdraw(player.getUniqueId(), amount))
            return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
    }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, String world, double amount) { return withdrawPlayer(player, amount); }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (amount < 0)
            return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Amount must be positive");
        service.deposit(player.getUniqueId(), amount);
        return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
    }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, String world, double amount) { return depositPlayer(player, amount); }

    @Override
    public EconomyResponse createBank(String s, String s1) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported");
    }

    @Override public boolean createPlayerAccount(OfflinePlayer player) { return service.createAccount(player.getUniqueId()); }
    @Override public boolean createPlayerAccount(OfflinePlayer player, String world) { return createPlayerAccount(player); }

    // Bank support is not implemented
    @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return notImpl(); }
    @Override public EconomyResponse deleteBank(String name) { return notImpl(); }
    @Override public EconomyResponse bankBalance(String name) { return notImpl(); }
    @Override public EconomyResponse bankHas(String name, double amount) { return notImpl(); }
    @Override public EconomyResponse bankWithdraw(String name, double amount) { return notImpl(); }
    @Override public EconomyResponse bankDeposit(String name, double amount) { return notImpl(); }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return notImpl(); }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) { return notImpl(); }
    @Override public List<String> getBanks() { return List.of(); }

    private EconomyResponse notImpl() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported");
    }

    // Deprecated String-based methods — not supported
    @Override @Deprecated public boolean hasAccount(String name) { return false; }
    @Override @Deprecated public boolean hasAccount(String name, String world) { return false; }
    @Override @Deprecated public double getBalance(String name) { return 0; }
    @Override @Deprecated public double getBalance(String name, String world) { return 0; }
    @Override @Deprecated public boolean has(String name, double amount) { return false; }
    @Override @Deprecated public boolean has(String name, String world, double amount) { return false; }
    @Override @Deprecated public EconomyResponse withdrawPlayer(String name, double amount) { return notImpl(); }
    @Override @Deprecated public EconomyResponse withdrawPlayer(String name, String world, double amount) { return notImpl(); }
    @Override @Deprecated public EconomyResponse depositPlayer(String name, double amount) { return notImpl(); }
    @Override @Deprecated public EconomyResponse depositPlayer(String name, String world, double amount) { return notImpl(); }
    @Override @Deprecated public boolean createPlayerAccount(String name) { return false; }
    @Override @Deprecated public boolean createPlayerAccount(String name, String world) { return false; }
    @Override @Deprecated public EconomyResponse isBankOwner(String name, String player) { return notImpl(); }
    @Override @Deprecated public EconomyResponse isBankMember(String name, String player) { return notImpl(); }
}
