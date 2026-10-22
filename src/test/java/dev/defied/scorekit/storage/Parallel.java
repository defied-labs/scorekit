package dev.defied.scorekit.storage;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public final class Parallel {

    private Parallel() {
    }

    public static void run(int threads, Runnable task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = IntStream.range(0, threads).<Future<?>>mapToObj(thread -> executor.submit(task)).toList();
            for (Future<?> future : futures) {
                future.get(120, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}