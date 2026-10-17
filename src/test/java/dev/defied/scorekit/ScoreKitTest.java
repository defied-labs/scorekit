package dev.defied.scorekit;

import dev.defied.scorekit.codec.EvaluationCriteria;
import dev.defied.scorekit.codec.KeyCodecs;
import dev.defied.scorekit.codec.ScoreCodec;
import dev.defied.scorekit.codec.ScoreType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScoreKitTest {

    static final class PizzaEaterProvider implements ScoreProvider<UUID, Integer> {
        @Override
        public String name() {
            return "pizza";
        }
    }

    record PlayTime(int hours, int minutes) {
    }

    static final class ArcadeProvider implements ScoreProvider<String, PlayTime> {
        @Override
        public String name() {
            return "arcade";
        }
    }

    static final ScoreCodec<PlayTime, Double> PLAY_TIME = ScoreCodec.of(ScoreType.NUMERIC,
            time -> time.hours() * 60.0 + time.minutes(),
            total -> new PlayTime((int) (total / 60), (int) (total % 60)));

    @Test
    void configuredProviderStoresAndRanksScores() {
        PizzaEaterProvider pizza = new PizzaEaterProvider();
        ScoreKit scoreKit = ScoreKit.builder()
                .registerProvider(pizza, provider -> provider
                        .evaluationCriteria(EvaluationCriteria.INTEGER))
                .build();
        UUID mario = UUID.randomUUID();
        UUID luigi = UUID.randomUUID();

        Leaderboard<UUID, Integer> weekly = scoreKit.leaderboard(pizza, "weekly");
        weekly.put(mario, 12);
        weekly.put(luigi, 30);

        assertEquals(List.of(new LeaderboardEntry<>(luigi, 30, 1), new LeaderboardEntry<>(mario, 12, 2)), weekly.top(10));
        assertEquals(OptionalLong.of(2), weekly.rank(mario));
        assertEquals(Optional.of(12), weekly.score(mario));
    }

    @Test
    void defaultConfigurationIsResolvedFromProviderGenerics() {
        PizzaEaterProvider pizza = new PizzaEaterProvider();
        ScoreKit scoreKit = ScoreKit.builder().registerProvider(pizza).build();
        UUID mario = UUID.randomUUID();

        scoreKit.leaderboard(pizza, "daily").put(mario, 3);

        assertEquals(Optional.of(3), scoreKit.leaderboard(pizza, "daily").score(mario));
    }

    @Test
    void customValueTypeRequiresCodec() {
        assertThrows(IllegalStateException.class, () -> ScoreKit.builder().registerProvider(new ArcadeProvider()));
    }

    @Test
    void customCodecDecodesStoredScores() {
        ArcadeProvider arcade = new ArcadeProvider();
        ScoreKit scoreKit = ScoreKit.builder()
                .registerProvider(arcade, provider -> provider.evaluationCriteria(PLAY_TIME))
                .build();

        Leaderboard<String, PlayTime> pacman = scoreKit.leaderboard(arcade, "pacman");
        pacman.put("alice", new PlayTime(1, 21));
        pacman.put("bob", new PlayTime(1, 20));

        assertEquals(new LeaderboardEntry<>("alice", new PlayTime(1, 21), 1), pacman.top(1).getFirst());
    }

    @Test
    void lambdaProviderNeedsExplicitCodecs() {
        ScoreProvider<String, Integer> anonymous = () -> "anonymous";

        assertThrows(IllegalStateException.class, () -> ScoreKit.builder().registerProvider(anonymous));

        ScoreKit scoreKit = ScoreKit.builder()
                .registerProvider(anonymous, provider -> provider
                        .keyCodec(KeyCodecs.STRING)
                        .evaluationCriteria(EvaluationCriteria.INTEGER))
                .build();
        scoreKit.leaderboard(anonymous, "all").put("a", 1);
        assertEquals(1, scoreKit.leaderboard(anonymous, "all").size());
    }

    @Test
    void leaderboardsAreCachedAndIndependent() {
        PizzaEaterProvider pizza = new PizzaEaterProvider();
        ScoreKit scoreKit = ScoreKit.builder().registerProvider(pizza).build();

        Leaderboard<UUID, Integer> weekly = scoreKit.leaderboard(pizza, "weekly");
        weekly.put(UUID.randomUUID(), 1);

        assertSame(weekly, scoreKit.leaderboard(pizza, "weekly"));
        assertNotSame(weekly, scoreKit.leaderboard(pizza, "monthly"));
        assertEquals(0, scoreKit.leaderboard(pizza, "monthly").size());
    }

    @Test
    void duplicateProviderNameIsRejected() {
        ScoreKit.Builder builder = ScoreKit.builder().registerProvider(new PizzaEaterProvider());

        assertThrows(IllegalArgumentException.class, () -> builder.registerProvider(new PizzaEaterProvider()));
    }

    @Test
    void unregisteredProviderIsRejected() {
        ScoreKit scoreKit = ScoreKit.builder().build();

        assertThrows(IllegalArgumentException.class, () -> scoreKit.leaderboard(new PizzaEaterProvider(), "weekly"));
    }

    @Test
    void invalidArgumentsAreRejected() {
        PizzaEaterProvider pizza = new PizzaEaterProvider();
        ScoreKit scoreKit = ScoreKit.builder().registerProvider(pizza).build();
        Leaderboard<UUID, Integer> weekly = scoreKit.leaderboard(pizza, "weekly");

        assertThrows(IllegalArgumentException.class, () -> scoreKit.leaderboard(pizza, " "));
        assertThrows(IllegalArgumentException.class, () -> weekly.top(-1));
        assertThrows(NullPointerException.class, () -> weekly.put(null, 1));
        assertThrows(NullPointerException.class, () -> weekly.put(UUID.randomUUID(), null));
    }
}
