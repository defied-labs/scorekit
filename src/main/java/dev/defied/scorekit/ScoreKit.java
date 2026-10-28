package dev.defied.scorekit;

import dev.defied.scorekit.codec.KeyCodec;
import dev.defied.scorekit.codec.ScoreCodec;
import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.StorageFactory;
import dev.defied.scorekit.storage.StorageSettings;
import dev.defied.scorekit.storage.memory.InMemoryStorageFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ScoreKit {

    private final StorageFactory storageFactory;
    private final Map<String, ProviderConfig<?, ?>> providers;
    private final Map<LeaderboardId, Leaderboard<?, ?>> leaderboards = new ConcurrentHashMap<>();

    private ScoreKit(StorageFactory storageFactory, Map<String, ProviderConfig<?, ?>> providers) {
        this.storageFactory = storageFactory;
        this.providers = Map.copyOf(providers);
    }

    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings("unchecked")
    public <K, V> Leaderboard<K, V> leaderboard(ScoreProvider<K, V> provider, String name) {
        ProviderConfig<?, ?> registered = providers.get(provider.name());
        if (registered == null || registered.provider().getClass() != provider.getClass()) {
            throw new IllegalArgumentException("Provider '" + provider.name() + "' is not registered");
        }
        ProviderConfig<K, V> config = (ProviderConfig<K, V>) registered;
        LeaderboardId id = new LeaderboardId(provider.name(), requireName(name, "Leaderboard"));
        return (Leaderboard<K, V>) leaderboards.computeIfAbsent(id, key -> create(key, config.keyCodec(), config.scoreCodec(), config.storageSettings()));
    }

    private <K, V, S> Leaderboard<K, V> create(LeaderboardId id, KeyCodec<K> keyCodec, ScoreCodec<V, S> scoreCodec, StorageSettings settings) {
        return new StoredLeaderboard<>(id.leaderboard(), keyCodec, scoreCodec, storageFactory.create(id, scoreCodec.type(), settings));
    }

    private static String requireName(String name, String owner) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(owner + " name must not be blank");
        }
        return name;
    }

    public static final class Builder {

        private final Map<String, ProviderConfig<?, ?>> providers = new HashMap<>();
        private StorageFactory storageFactory = new InMemoryStorageFactory();

        private Builder() {
        }

        public Builder storage(StorageFactory factory) {
            storageFactory = Objects.requireNonNull(factory, "factory");
            return this;
        }

        public <K, V> Builder registerProvider(ScoreProvider<K, V> provider) {
            return registerProvider(provider, config -> {
            });
        }

        public <K, V> Builder registerProvider(ScoreProvider<K, V> provider, Consumer<ProviderConfig<K, V>> configurer) {
            String name = requireName(provider.name(), "Provider");
            if (providers.containsKey(name)) {
                throw new IllegalArgumentException("Provider '" + name + "' is already registered");
            }
            ProviderConfig<K, V> config = new ProviderConfig<>(provider);
            configurer.accept(config);
            config.validate();
            providers.put(name, config);
            return this;
        }

        public ScoreKit build() {
            return new ScoreKit(storageFactory, providers);
        }
    }
}
