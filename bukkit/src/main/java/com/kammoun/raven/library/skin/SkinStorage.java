package com.kammoun.raven.library.skin;

import com.kammoun.raven.library.skin.model.SkinData;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface SkinStorage {

    CompletableFuture<Void> initialize();

    CompletableFuture<Optional<SkinData>> load(@NotNull UUID uuid);

    CompletableFuture<Void> save(@NotNull SkinData skinData);

    CompletableFuture<Void> delete(@NotNull UUID uuid);

    CompletableFuture<Boolean> exists(@NotNull UUID uuid);

    CompletableFuture<Void> shutdown();
}