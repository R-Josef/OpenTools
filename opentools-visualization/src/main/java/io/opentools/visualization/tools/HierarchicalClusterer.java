package io.opentools.visualization.tools;

import io.opentools.api.tool.CancellationToken;
import io.opentools.api.tool.OpenToolException;
import io.opentools.core.util.OrderedParallelProcessor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Deterministic average-linkage clustering using 1-Pearson distance on
 * pairwise-complete observations. Uncorrelated, constant, or non-overlapping
 * vectors receive the maximum finite distance of 1.
 */
final class HierarchicalClusterer {
    static final String ALGORITHM = "AVERAGE_LINKAGE_1_MINUS_PEARSON";

    private HierarchicalClusterer() {
    }

    static List<Integer> order(
            List<? extends List<Double>> vectors,
            List<String> labels,
            int maximumItems,
            CancellationToken cancellationToken
    ) {
        return order(vectors, labels, maximumItems, cancellationToken, 1);
    }

    static List<Integer> order(
            List<? extends List<Double>> vectors,
            List<String> labels,
            int maximumItems,
            CancellationToken cancellationToken,
            int threads
    ) {
        int size = vectors.size();
        if (size != labels.size()) {
            throw new IllegalArgumentException("Cluster vectors and labels must have equal size");
        }
        if (size <= 1) {
            List<Integer> singleton = new ArrayList<>(size);
            if (size == 1) {
                singleton.add(0);
            }
            return singleton;
        }
        if (size > maximumItems) {
            throw new OpenToolException(
                    "VIS_CLUSTER_LIMIT",
                    "Hierarchical clustering requested for " + size
                            + " items, exceeding maxClusterItems=" + maximumItems + ".",
                    "Increase maxClusterItems, reduce maxRows/maxColumns, or use mean/name ordering."
            );
        }

        double[][] distances = new double[size][size];
        PriorityQueue<Pair> queue = new PriorityQueue<>(PAIR_ORDER);
        Cluster[] clusters = new Cluster[size];
        boolean[] active = new boolean[size];
        int[] nextRow = {0};
        try {
            OrderedParallelProcessor.processChunks(
                    threads,
                    8,
                    cancellationToken,
                    () -> nextRow[0] < size ? nextRow[0]++ : null,
                    rows -> {
                        double[][] chunk = new double[rows.size()][size];
                        for (int index = 0; index < rows.size(); index++) {
                            int left = rows.get(index);
                            for (int right = 0; right < size; right++) {
                                if (right != left) {
                                    chunk[index][right] = distance(
                                            vectors.get(left), vectors.get(right));
                                }
                            }
                        }
                        return new DistanceRows(rows.getFirst(), chunk);
                    },
                    result -> {
                        for (int index = 0; index < result.values().length; index++) {
                            int left = result.startRow() + index;
                            for (int right = 0; right < size; right++) {
                                double value = right == left
                                        ? 0 : result.values()[index][right];
                                distances[left][right] = value;
                                distances[right][left] = value;
                            }
                        }
                    }
            );
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Parallel distance calculation failed", ex);
        }
        for (int left = 0; left < size; left++) {
            cancellationToken.throwIfCancelled();
            clusters[left] = Cluster.leaf(left, labels.get(left));
            active[left] = true;
        }
        for (int left = 0; left < size; left++) {
            for (int right = left + 1; right < size; right++) {
                queue.add(pair(distances, clusters, left, right));
            }
        }

        int remaining = size;
        while (remaining > 1) {
            cancellationToken.throwIfCancelled();
            Pair pair = nextValidPair(queue, clusters, active);
            if (pair == null) {
                throw new IllegalStateException("Hierarchical clustering lost all active pairs");
            }
            int first = pair.left();
            int second = pair.right();
            Cluster leftCluster = clusters[first];
            Cluster rightCluster = clusters[second];
            if (leftCluster.minKey().compareTo(rightCluster.minKey()) > 0) {
                int swap = first;
                first = second;
                second = swap;
                leftCluster = clusters[first];
                rightCluster = clusters[second];
            }
            double leftSize = leftCluster.size();
            double rightSize = rightCluster.size();
            for (int other = 0; other < size; other++) {
                if (other == first || other == second || !active[other]) {
                    continue;
                }
                double merged = (leftSize * distances[first][other]
                        + rightSize * distances[second][other])
                        / (leftSize + rightSize);
                distances[first][other] = merged;
                distances[other][first] = merged;
            }
            clusters[first] = leftCluster.merge(rightCluster);
            active[second] = false;
            remaining--;
            for (int other = 0; other < size; other++) {
                if (other != first && active[other]) {
                    queue.add(pair(distances, clusters, first, other));
                }
            }
        }

        int root = -1;
        for (int index = 0; index < size; index++) {
            if (active[index]) {
                root = index;
                break;
            }
        }
        List<Integer> order = new ArrayList<>(size);
        flatten(clusters[root], order);
        return List.copyOf(order);
    }

    private static Pair nextValidPair(
            PriorityQueue<Pair> queue,
            Cluster[] clusters,
            boolean[] active
    ) {
        while (!queue.isEmpty()) {
            Pair pair = queue.poll();
            if (pair.left() >= active.length || pair.right() >= active.length
                    || !active[pair.left()] || !active[pair.right()]
                    || clusters[pair.left()].version() != pair.leftVersion()
                    || clusters[pair.right()].version() != pair.rightVersion()) {
                continue;
            }
            return pair;
        }
        return null;
    }

    private static Pair pair(double[][] distances, Cluster[] clusters, int left, int right) {
        if (clusters[left].minKey().compareTo(clusters[right].minKey()) > 0) {
            int swap = left;
            left = right;
            right = swap;
        }
        return new Pair(
                distances[left][right],
                left,
                right,
                clusters[left].version(),
                clusters[right].version(),
                clusters[left].minKey(),
                clusters[right].maxKey()
        );
    }

    private static double distance(List<Double> left, List<Double> right) {
        int count = Math.min(left.size(), right.size());
        double leftSum = 0;
        double rightSum = 0;
        int shared = 0;
        for (int index = 0; index < count; index++) {
            Double a = left.get(index);
            Double b = right.get(index);
            if (a != null && b != null && Double.isFinite(a) && Double.isFinite(b)) {
                leftSum += a;
                rightSum += b;
                shared++;
            }
        }
        if (shared < 2) {
            return 1.0;
        }
        double leftMean = leftSum / shared;
        double rightMean = rightSum / shared;
        double covariance = 0;
        double leftVariance = 0;
        double rightVariance = 0;
        for (int index = 0; index < count; index++) {
            Double a = left.get(index);
            Double b = right.get(index);
            if (a == null || b == null || !Double.isFinite(a) || !Double.isFinite(b)) {
                continue;
            }
            double leftDelta = a - leftMean;
            double rightDelta = b - rightMean;
            covariance += leftDelta * rightDelta;
            leftVariance += leftDelta * leftDelta;
            rightVariance += rightDelta * rightDelta;
        }
        if (leftVariance <= 1.0e-15 || rightVariance <= 1.0e-15) {
            return 1.0;
        }
        double correlation = covariance / Math.sqrt(leftVariance * rightVariance);
        if (!Double.isFinite(correlation)) {
            return 1.0;
        }
        correlation = Math.max(-1.0, Math.min(1.0, correlation));
        return (1.0 - correlation) / 2.0;
    }

    private static void flatten(Cluster cluster, List<Integer> output) {
        if (cluster.leafIndex() >= 0) {
            output.add(cluster.leafIndex());
            return;
        }
        flatten(cluster.left(), output);
        flatten(cluster.right(), output);
    }

    private record Pair(
            double distance,
            int left,
            int right,
            int leftVersion,
            int rightVersion,
            LeafKey leftMinimum,
            LeafKey rightMaximum
    ) {
    }

    private record DistanceRows(int startRow, double[][] values) {
    }

    private static final Comparator<Pair> PAIR_ORDER = Comparator
            .comparingDouble(Pair::distance)
            .thenComparing(Pair::leftMinimum)
            .thenComparing(Pair::rightMaximum)
            .thenComparingInt(Pair::left)
            .thenComparingInt(Pair::right);

    private record LeafKey(String label, int sourceIndex) implements Comparable<LeafKey> {
        @Override
        public int compareTo(LeafKey other) {
            int labelOrder = String.CASE_INSENSITIVE_ORDER.compare(label, other.label);
            if (labelOrder != 0) {
                return labelOrder;
            }
            int exact = label.compareTo(other.label);
            return exact != 0 ? exact : Integer.compare(sourceIndex, other.sourceIndex);
        }
    }

    private record Cluster(
            int leafIndex,
            int version,
            LeafKey minKey,
            LeafKey maxKey,
            int size,
            Cluster left,
            Cluster right
    ) {
        static Cluster leaf(int index, String label) {
            LeafKey key = new LeafKey(label == null ? "" : label.trim(), index);
            return new Cluster(index, 0, key, key, 1, null, null);
        }

        Cluster merge(Cluster other) {
            LeafKey minimum = minKey.compareTo(other.minKey) <= 0 ? minKey : other.minKey;
            LeafKey maximum = maxKey.compareTo(other.maxKey) >= 0 ? maxKey : other.maxKey;
            return new Cluster(
                    -1,
                    version + 1,
                    minimum,
                    maximum,
                    size + other.size,
                    this,
                    other
            );
        }
    }
}
