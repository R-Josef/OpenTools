package io.opentools.core.util;

import io.opentools.api.tool.CancellationToken;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class OrderedParallelProcessor {
    public static final int AUTO_THREADS = 0;
    public static final long AUTO_BYTES = 8L * 1024 * 1024;
    private static final int MAX_THREADS = 64;

    private OrderedParallelProcessor() {
    }

    public static int resolveThreads(int requested, long inputBytes) {
        return resolveThreads(requested, inputBytes, AUTO_BYTES);
    }

    public static int resolveThreads(int requested, long inputBytes, long autoBytes) {
        if (requested < AUTO_THREADS) {
            throw new IllegalArgumentException("threads must be >= 0");
        }
        if (requested > 1) {
            return Math.min(requested, MAX_THREADS);
        }
        if (requested == 1 || inputBytes < Math.max(1, autoBytes)) {
            return 1;
        }
        return Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
    }

    public static <I, R> void process(
            int threads,
            int batchSize,
            CancellationToken cancellationToken,
            Source<I> source,
            Mapper<I, R> mapper,
            Sink<R> sink
    ) throws Exception {
        processChunks(
                threads,
                batchSize,
                cancellationToken,
                source,
                chunk -> {
                    List<R> results = new ArrayList<>(chunk.size());
                    for (I item : chunk) {
                        results.add(mapper.map(item));
                    }
                    return results;
                },
                results -> {
                    for (R result : results) {
                        if (result != null) {
                            cancellationToken.throwIfCancelled();
                            sink.accept(result);
                        }
                    }
                }
        );
    }

    public static <I, R> void processChunks(
            int threads,
            int batchSize,
            CancellationToken cancellationToken,
            Source<I> source,
            ChunkMapper<I, R> mapper,
            Sink<R> sink
    ) throws Exception {
        int workerCount = Math.max(1, Math.min(threads, MAX_THREADS));
        int actualBatchSize = Math.max(1, batchSize);
        if (workerCount == 1) {
            processChunksSequential(
                    actualBatchSize, cancellationToken, source, mapper, sink);
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(
                workerCount,
                Thread.ofPlatform()
                        .daemon(true)
                        .name("opentools-record-worker-", 0)
                        .factory()
        );
        Deque<Future<R>> pending = new ArrayDeque<>();
        List<I> chunk = new ArrayList<>(actualBatchSize);
        int maxPending = workerCount * 2;
        try {
            I item;
            while ((item = source.next()) != null) {
                cancellationToken.throwIfCancelled();
                chunk.add(item);
                if (chunk.size() >= actualBatchSize) {
                    submit(chunk, executor, mapper, pending);
                    chunk = new ArrayList<>(actualBatchSize);
                }
                if (pending.size() >= maxPending) {
                    finishFirst(cancellationToken, pending, sink);
                }
            }
            if (!chunk.isEmpty()) {
                submit(chunk, executor, mapper, pending);
            }
            while (!pending.isEmpty()) {
                finishFirst(cancellationToken, pending, sink);
            }
        } catch (Throwable failure) {
            for (Future<R> future : pending) {
                future.cancel(true);
            }
            rethrow(failure);
        } finally {
            executor.shutdownNow();
            try {
                executor.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static <I, R> void processChunksSequential(
            int batchSize,
            CancellationToken cancellationToken,
            Source<I> source,
            ChunkMapper<I, R> mapper,
            Sink<R> sink
    ) throws Exception {
        List<I> chunk = new ArrayList<>(batchSize);
        I item;
        while ((item = source.next()) != null) {
            cancellationToken.throwIfCancelled();
            chunk.add(item);
            if (chunk.size() >= batchSize) {
                emit(cancellationToken, mapper.map(List.copyOf(chunk)), sink);
                chunk = new ArrayList<>(batchSize);
            }
        }
        if (!chunk.isEmpty()) {
            emit(cancellationToken, mapper.map(List.copyOf(chunk)), sink);
        }
    }

    private static <I, R> void emit(
            CancellationToken cancellationToken,
            R result,
            Sink<R> sink
    ) throws Exception {
        if (result != null) {
            cancellationToken.throwIfCancelled();
            sink.accept(result);
        }
    }

    private static <I, R> void submit(
            List<I> chunk,
            ExecutorService executor,
            ChunkMapper<I, R> mapper,
            Deque<Future<R>> pending
    ) {
        List<I> work = List.copyOf(chunk);
        pending.addLast(executor.submit(() -> mapper.map(work)));
    }

    private static <R> void finishFirst(
            CancellationToken cancellationToken,
            Deque<Future<R>> pending,
            Sink<R> sink
    ) throws Exception {
        Future<R> future = pending.removeFirst();
        R result;
        try {
            result = future.get();
        } catch (ExecutionException ex) {
            rethrow(ex.getCause());
            return;
        }
        emit(cancellationToken, result, sink);
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure instanceof Exception exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException(failure);
    }

    @FunctionalInterface
    public interface Source<T> {
        T next() throws Exception;
    }

    @FunctionalInterface
    public interface Mapper<T, R> {
        R map(T value) throws Exception;
    }

    @FunctionalInterface
    public interface ChunkMapper<T, R> {
        R map(List<T> values) throws Exception;
    }

    @FunctionalInterface
    public interface Sink<T> {
        void accept(T value) throws Exception;
    }
}
