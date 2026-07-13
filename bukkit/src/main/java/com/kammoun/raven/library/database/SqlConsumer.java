package com.kammoun.raven.library.database;

import java.sql.SQLException;

@FunctionalInterface
public interface SqlConsumer<T> {
    void accept(T t) throws SQLException;
}
