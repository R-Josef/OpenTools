package io.opentools.core.util;

import io.opentools.api.tool.CancellationToken;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class OrderedParallelProcessorTest {
    @Test
    void preservesInputOrderAcrossBatches() throws Exception {
        List<Integer> input = new ArrayList<>();
        for (int value = 0; value < 101; value++) {
            input.add(value);
        }
        Iterator<Integer> iterator = input.iterator();
        List<String> output = new ArrayList<>();

        OrderedParallelProcessor.process(
                3,
                7,
                new CancellationToken(),
                () -> iterator.hasNext() ? iterator.next() : null,
                value -> {
                    Thread.sleep((100 - value) % 3);
                    return "v" + value;
                },
                output::add
        );

        assertEquals(
                input.stream().map(value -> "v" + value).toList(),
                output
        );
    }

    @Test
    void propagatesWorkerFailureAndStopsIteration() {
        Iterator<Integer> iterator = List.of(1, 2, 3, 4, 5).iterator();

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> OrderedParallelProcessor.process(
                        2,
                        2,
                        new CancellationToken(),
                        () -> iterator.hasNext() ? iterator.next() : null,
                        value -> {
                            if (value == 3) {
                                throw new IllegalStateException("worker failure");
                            }
                            return value;
                        },
                        ignored -> {
                        }
                )
        );

        assertEquals("worker failure", failure.getMessage());
    }

    @Test
    void autoThreadPolicyKeepsSmallInputsSequential() {
        assertEquals(1, OrderedParallelProcessor.resolveThreads(
                OrderedParallelProcessor.AUTO_THREADS,
                OrderedParallelProcessor.AUTO_BYTES - 1
        ));
        assertEquals(1, OrderedParallelProcessor.resolveThreads(1, Long.MAX_VALUE));
    }
}
