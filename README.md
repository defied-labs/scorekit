# ScoreKit

Leaderboards for Java that stay fast with millions of entries. Scores live in a database, Redis keeps a ranked copy, and neither `top(n)` nor `rank(key)` ever loads a whole leaderboard into memory.

- Any number of providers, each with any number of leaderboards
- Generic keys and scores: numbers, strings or your own types through codecs
- Storage backends: in memory, Redis, PostgreSQL, MySQL, MariaDB, MongoDB, or one of the databases with a Redis cache in front

Requires Java 21.

## Usage

A provider names a family of leaderboards and fixes their key and score types.

```java
final class PizzaEaters implements ScoreProvider<UUID, Integer> {
    @Override
    public String name() {
        return "pizza";
    }
}

PizzaEaters pizza = new PizzaEaters();
ScoreKit scoreKit = ScoreKit.builder()
        .registerProvider(pizza)
        .build();

Leaderboard<UUID, Integer> weekly = scoreKit.leaderboard(pizza, "weekly");
weekly.put(player, 12);
weekly.top(10);
weekly.rank(player);
weekly.score(player);
weekly.remove(player);
```

Higher scores rank first; ties are broken by key. Ranks start at 1.

### Codecs

Keys of type `String`, `UUID`, `Integer` and `Long`, and scores of type `Integer`, `Long`, `Double` and `String` work without configuration. Anything else needs a codec:

```java
record PlayTime(int hours, int minutes) {
}

ScoreCodec<PlayTime, Double> playTime = ScoreCodec.of(ScoreType.NUMERIC,
        time -> time.hours() * 60.0 + time.minutes(),
        total -> new PlayTime((int) (total / 60), (int) (total % 60)));

ScoreKit.builder()
        .registerProvider(arcade, config -> config
                .evaluationCriteria(playTime)
                .keyCodec(KeyCodec.of(Player::id, Player::new)))
        .build();
```

A score is encoded either as a `double` (`ScoreType.NUMERIC`) or as a `String` compared by code point (`ScoreType.LEXICOGRAPHIC`). `Long` scores must stay within ±2^53 so that they convert to a `double` exactly.

## Storage

The default storage is in memory. Pick another one with `storage(...)`:

```java
ScoreKit.builder()
        .storage(factory)
        .registerProvider(pizza)
        .build();
```

| Factory | Use |
|---|---|
| `InMemoryStorageFactory` | tests, single process |
| `RedisStorageFactory(UnifiedJedis)` | Redis only |
| `JdbcStorageFactory(DataSource)` | PostgreSQL, MySQL or MariaDB only |
| `MongoStorageFactory(MongoDatabase)` | MongoDB only |
| `CachedStorageFactory(cache, primary)` | a database as the source of truth, Redis as the read cache |

`cached(false)` and `approximateRank(...)` in a provider's configuration choose, per provider, whether its leaderboards use the Redis cache and whether ranks are estimated. They are ignored by storages they do not apply to.

No client library is bundled. Add the ones you use to your own build: `redis.clients:jedis` 8.x, `org.mongodb:mongodb-driver-sync` 5.x, or your JDBC driver.

### Database with a Redis cache

```java
UnifiedJedis jedis = RedisClient.create("localhost", 6379);
CachedStorageFactory storage = new CachedStorageFactory(new RedisStorageFactory(jedis), new JdbcStorageFactory(dataSource));

ScoreKit scoreKit = ScoreKit.builder()
        .storage(storage)
        .registerProvider(pizza)
        .build();

storage.close();
```

Every write goes to the database first and then to Redis. Reads come from Redis. A leaderboard is copied to Redis the first time it is read: until then the request is answered by the database while the cache warms up in the background. Writes to leaderboards that nobody reads stay out of Redis. If Redis fails, it stays out of the way for a few seconds. Writes carry versions, so instances racing on the same key always converge, and a sweep every 10 seconds copies to Redis any change that did not reach it. `close()` stops the sweeps.

The timings can be changed, and leaderboards that nobody reads for a while can be removed from Redis:

```java
CachedStorageFactory storage = CachedStorageFactory.builder(new RedisStorageFactory(jedis), new JdbcStorageFactory(dataSource))
        .sweepInterval(Duration.ofSeconds(10))
        .retryDelay(Duration.ofSeconds(5))
        .evictIdleAfter(Duration.ofHours(1))
        .build();
```

An evicted leaderboard is copied back on its next read.

Requirements:

- Redis 7.4 or newer (or Valkey 9), for per-field hash expiration.
- An eviction policy that cannot drop ScoreKit keys: `noeviction` or one of the `volatile-*` policies.
- Clocks on the application servers within 20 seconds of each other.

### Very large leaderboards

Redis answers `rank` in O(log N), but it holds a full copy of every cached leaderboard, roughly 100 bytes per entry. A database alone answers `rank` by counting the entries ahead, which is slow deep in a leaderboard of hundreds of millions.

For those leaderboards a provider can stay out of Redis and estimate ranks instead:

```java
ScoreKit.builder()
        .storage(storage)
        .registerProvider(global, config -> config
                .cached(false)
                .approximateRank(new RankSampling(10_000, Duration.ofMinutes(10))))
        .build();
```

Every 10 minutes, in the background, ScoreKit walks the ranking in the database and remembers the entry at every 10 000th position. `rank` then finds the nearest remembered entry ahead of the player and counts at most about 10 000 entries in the index, so it costs the same at position 10 or 500 000 000.

- Ranks are exact until the leaderboard changes. After that, a rank is off by the number of entries that were added or removed ahead of the remembered entry since the last walk. Ranks ahead of the first remembered entry, about the first 10 000 positions, are always exact.
- `top`, `score`, `put` and `remove` are not affected and stay exact.
- Each walk reads the whole ranking index once, on every application instance that serves `rank` for that leaderboard. It only starts for leaderboards whose ranks are requested.
- Until the first walk after a start finishes, ranks deeper than the part already walked are counted the slow way.
- The remembered entries take memory in each instance: about 50 000 entries for 500 million players with the default interval.

`approximateRank()` without arguments uses these defaults. With `cached(true)`, the default, ranks come from Redis and sampling only serves reads that fall back to the database.

### Relational databases

`JdbcStorageFactory` detects PostgreSQL, MySQL (8.0 or newer) and MariaDB (10.2 or newer) from the `DataSource`. Create the tables once, or apply the script for your database from `dev/defied/scorekit/storage/jdbc/` in the jar with your migration tool:

```java
JdbcStorageFactory jdbc = new JdbcStorageFactory(dataSource);
jdbc.createSchema();
```

On MySQL and MariaDB:

- Provider names, leaderboard names, keys and text scores are limited to 191 characters.
- Scores cannot be infinite.

### MongoDB

```java
MongoStorageFactory mongo = new MongoStorageFactory(client.getDatabase("game"));
```

MongoDB 4.2 or newer is required. The factory creates its collections and indexes when it starts.

## Building

```
./gradlew build
```

The Redis and database tests run in Docker through Testcontainers.

## License

[MIT](LICENSE)
