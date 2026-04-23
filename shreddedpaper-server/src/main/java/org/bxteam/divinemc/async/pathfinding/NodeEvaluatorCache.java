package org.bxteam.divinemc.async.pathfinding;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.level.pathfinder.BinaryHeap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;

public final class NodeEvaluatorCache {
    private static final Map<CacheKey, ArrayDeque<NodeEvaluator>> threadLocalNodeEvaluators = new HashMap<>();
    private static final Object2ObjectOpenHashMap<NodeEvaluator, NodeEvaluatorGenerator> nodeEvaluatorToGenerator = new Object2ObjectOpenHashMap<>();
    public static final ThreadLocal<BinaryHeap> HEAP_LOCAL = ThreadLocal.withInitial(BinaryHeap::new);
    public static final ThreadLocal<Node[]> NEIGHBORS_LOCAL = ThreadLocal.withInitial(() -> new Node[32]);

    private NodeEvaluatorCache() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static synchronized @NotNull NodeEvaluator takeNodeEvaluator(@NotNull NodeEvaluatorGenerator generator, @NotNull NodeEvaluator localNodeEvaluator) {
        final int nodeEvaluatorFeatures = NodeEvaluatorFeatures.fromNodeEvaluator(localNodeEvaluator);
        final CacheKey cacheKey = new CacheKey(nodeEvaluatorFeatures, generator);
        NodeEvaluator nodeEvaluator = threadLocalNodeEvaluators.computeIfAbsent(cacheKey, key -> new ArrayDeque<>()).poll();

        if (nodeEvaluator == null) {
            nodeEvaluator = generator.generate(NodeEvaluatorFeatures.unpack(nodeEvaluatorFeatures));
        }

        nodeEvaluatorToGenerator.put(nodeEvaluator, generator);

        return nodeEvaluator;
    }

    public static synchronized void returnNodeEvaluator(@NotNull final NodeEvaluator nodeEvaluator) {
        final NodeEvaluatorGenerator generator = nodeEvaluatorToGenerator.remove(nodeEvaluator);
        Validate.notNull(generator, "NodeEvaluator already returned");

        final int nodeEvaluatorFeatures = NodeEvaluatorFeatures.fromNodeEvaluator(nodeEvaluator);
        final CacheKey cacheKey = new CacheKey(nodeEvaluatorFeatures, generator);
        threadLocalNodeEvaluators.computeIfAbsent(cacheKey, key -> new ArrayDeque<>()).offer(nodeEvaluator);
    }

    public static synchronized void removeNodeEvaluator(@NotNull final NodeEvaluator nodeEvaluator) {
        nodeEvaluatorToGenerator.remove(nodeEvaluator);
    }

    private record CacheKey(int nodeEvaluatorFeatures, NodeEvaluatorGenerator generator) {
    }
}
