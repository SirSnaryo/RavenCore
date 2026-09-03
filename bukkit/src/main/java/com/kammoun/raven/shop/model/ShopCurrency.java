package com.kammoun.raven.shop.model;

import com.kammoun.raven.library.util.NumberUtils;

import java.util.Locale;

public enum ShopCurrency {

    MONEY,
    RAVS;

    public static ShopCurrency parse(String value, ShopCurrency fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public String format(double amount) {
        return this == RAVS
                ? NumberUtils.compact(Math.round(amount))
                : NumberUtils.compactMoney(amount);
    }
}
