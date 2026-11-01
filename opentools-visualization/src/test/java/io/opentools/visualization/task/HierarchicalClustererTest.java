package io.opentools.visualization.task;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.OpenToolException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HierarchicalClustererTest {
    @Test
    void groupsCorrelatedRowsAndIsDeterministic() {
        List<List<Double>> vectors = List.of(
                Arrays.asList(1.0, 2.0, 3.0, null),
                Arrays.asList(1.1, 2.1, 3.1, null),
                Arrays.asList(-1.0, -2.0, -3.0, null),
                Arrays.asList(-1.1, -2.1, -3.1, null)
        );
        List<String> labels = List.of("alpha_2", "alpha_1", "beta_2", "beta_1");

        List<Integer> first = HierarchicalClusterer.order(
                vectors, labels, 100, new CancellationToken());
        List<Integer> second = HierarchicalClusterer.order(
                vectors, labels, 100, new CancellationToken());
        List<Integer> parallel = HierarchicalClusterer.order(
                vectors, labels, 100, new CancellationToken(), 3);

        assertEquals(first, second);
        assertEquals(first, parallel);
        assertAdjacent(first, 0, 1);
        assertAdjacent(first, 2, 3);
        assertEquals(4, first.size());

        OpenToolException limit = assertThrows(OpenToolException.class, () ->
                HierarchicalClusterer.order(vectors, labels, 2, new CancellationToken()));
        assertEquals("VIS_CLUSTER_LIMIT", limit.code());
    }

    private static void assertAdjacent(List<Integer> order, int left, int right) {
        int difference = Math.abs(order.indexOf(left) - order.indexOf(right));
        assertTrue(difference == 1, order.toString());
    }
}
