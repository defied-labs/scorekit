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

Every write goes to the database first and then to Redis. Reads come from Redis. If the cache is cold, the request is answered by the database while the cache warms up in the background, and Redis stays out of the way for a few seconds after an error. Writes carry versions, so instances racing on the same key always converge, and a sweep every 10 seconds copies to Redis any change that did not reach it. `close()` stops the sweeps.

Requirements:

- Redis 7.4 or newer (or Valkey 9), for per-field hash expiration.
- An eviction policy that cannot drop ScoreKit keys: `noeviction` or one of the `volatile-*` policies.
- Clocks on the application servers within 20 seconds of each other.

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
