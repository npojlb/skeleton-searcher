package cn.yanyiming.skeletonsearcher;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 将运算结果中的每一个命中方块绘制为一个独立的半透明实心立方体。
 * 方块之间保留极小间隙，便于在连续区域中辨认单格轮廓。
 */
public final class SolidBlockRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("skeleton_searcher/renderer");
    private static final int MAX_RENDERED_BLOCKS = 350_000;
    private static final float INSET = 0.035f;

    private final ExecutorService executor;
    private final AtomicBoolean building = new AtomicBoolean(false);
    private final AtomicInteger generation = new AtomicInteger();

    private volatile BlockCache cache = BlockCache.empty();
    private volatile RequestKey lastSubmitted;
    private int tickCounter;

    public SolidBlockRenderer() {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "skeleton-searcher-block-builder");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        };
        executor = Executors.newSingleThreadExecutor(factory);
    }

    public void tick(MinecraftClient client) {
        if (++tickCounter % 10 != 0 || client.player == null || client.world == null) {
            return;
        }
        requestRebuild(client);
    }

    public void markDirty() {
        generation.incrementAndGet();
        lastSubmitted = null;
    }

    public void clearCache() {
        generation.incrementAndGet();
        cache = BlockCache.empty();
        lastSubmitted = null;
    }

    public void requestRebuild(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }

        ModConfig.Snapshot snapshot = ModConfig.snapshot();
        if (snapshot.spheres().isEmpty() || !snapshot.mode().shouldRender(snapshot.spheres().size())) {
            clearCache();
            return;
        }

        BlockPos playerPos = client.player.getBlockPos();
        String dimension = client.world.getRegistryKey().getValue().toString();
        int anchorStep = 4;
        int anchorX = Math.floorDiv(playerPos.getX(), anchorStep) * anchorStep;
        int anchorY = Math.floorDiv(playerPos.getY(), anchorStep) * anchorStep;
        int anchorZ = Math.floorDiv(playerPos.getZ(), anchorStep) * anchorStep;

        RequestKey desired = new RequestKey(
                dimension,
                anchorX,
                anchorY,
                anchorZ,
                snapshot.renderDistance(),
                snapshot.revision());

        if (desired.equals(cache.key) || desired.equals(lastSubmitted) || !building.compareAndSet(false, true)) {
            return;
        }

        int requestGeneration = generation.get();
        lastSubmitted = desired;
        executor.execute(() -> {
            try {
                BlockCache built = build(desired, snapshot);
                if (generation.get() == requestGeneration) {
                    cache = built;
                } else {
                    lastSubmitted = null;
                }
            } catch (RuntimeException exception) {
                LOGGER.error("构建 Skeleton Searcher 高亮方块时发生错误", exception);
                lastSubmitted = null;
            } finally {
                building.set(false);
            }
        });
    }

    public void render(WorldRenderContext context) {
        BlockCache current = cache;
        if (current.blocks.isEmpty()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }
        String dimension = client.world.getRegistryKey().getValue().toString();
        if (!Objects.equals(dimension, current.key.dimension)) {
            return;
        }

        ModConfig.Snapshot snapshot = ModConfig.snapshot();
        if (!snapshot.mode().shouldRender(snapshot.spheres().size())) {
            return;
        }

        int alpha = Math.clamp(Math.round(snapshot.opacity() * 255.0f), 8, 220);
        int color = (alpha << 24) | 0x00FFFFFF;

        Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
        MatrixStack matrices = context.matrices();
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        VertexConsumer vertices = context.consumers().getBuffer(RenderLayers.debugFilledBox());
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        for (BlockVoxel block : current.blocks) {
            drawSolidCube(vertices, matrix, block, color);
        }

        matrices.pop();
    }

    private static BlockCache build(RequestKey key, ModConfig.Snapshot snapshot) {
        List<SphereRegion> spheres = snapshot.spheres().stream()
                .filter(sphere -> key.dimension.equals(sphere.dimension))
                .toList();
        if (spheres.isEmpty() || !snapshot.mode().shouldRender(spheres.size())) {
            return new BlockCache(key, List.of(), false);
        }

        int radius = key.renderDistance;
        SphereMath.Bounds local = new SphereMath.Bounds(
                key.anchorX - radius,
                key.anchorY - radius,
                key.anchorZ - radius,
                key.anchorX + radius,
                key.anchorY + radius,
                key.anchorZ + radius);

        SphereMath.Bounds sphereBounds = snapshot.mode().usesIntersectionMath()
                ? SphereMath.outerIntersectionBounds(spheres)
                : SphereMath.outerUnionBounds(spheres);
        if (sphereBounds == null) {
            return new BlockCache(key, List.of(), false);
        }

        SphereMath.Bounds scan = local.intersect(sphereBounds);
        if (scan == null) {
            return new BlockCache(key, List.of(), false);
        }

        long total = (long) (scan.maxX() - scan.minX() + 1)
                * (scan.maxY() - scan.minY() + 1)
                * (scan.maxZ() - scan.minZ() + 1);
        if (total > 16_000_000L) {
            throw new IllegalStateException("局部扫描范围异常：" + total);
        }

        List<BlockVoxel> blocks = new ArrayList<>((int) Math.min(total, MAX_RENDERED_BLOCKS));
        boolean truncated = false;
        outer:
        for (int x = scan.minX(); x <= scan.maxX(); x++) {
            for (int y = scan.minY(); y <= scan.maxY(); y++) {
                for (int z = scan.minZ(); z <= scan.maxZ(); z++) {
                    if (!SphereMath.containsResult(spheres, snapshot.mode(), x, y, z)) {
                        continue;
                    }
                    blocks.add(new BlockVoxel(x, y, z));
                    if (blocks.size() >= MAX_RENDERED_BLOCKS) {
                        truncated = true;
                        break outer;
                    }
                }
            }
        }

        if (truncated) {
            LOGGER.warn("命中方块超过 {} 个，本次仅渲染前 {} 个；建议降低渲染范围或增加交集球体。",
                    MAX_RENDERED_BLOCKS, MAX_RENDERED_BLOCKS);
        }
        return new BlockCache(key, List.copyOf(blocks), truncated);
    }

    private static void drawSolidCube(VertexConsumer vertices, Matrix4f matrix, BlockVoxel block, int color) {
        float x0 = block.x + INSET;
        float y0 = block.y + INSET;
        float z0 = block.z + INSET;
        float x1 = block.x + 1.0f - INSET;
        float y1 = block.y + 1.0f - INSET;
        float z1 = block.z + 1.0f - INSET;

        quad(vertices, matrix, color, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
        quad(vertices, matrix, color, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        quad(vertices, matrix, color, x1, y0, z0, x1, y1, z0, x0, y1, z0, x0, y0, z0);
        quad(vertices, matrix, color, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1);
        quad(vertices, matrix, color, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1);
        quad(vertices, matrix, color, x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0);
    }

    private static void quad(
            VertexConsumer vertices,
            Matrix4f matrix,
            int color,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4
    ) {
        vertices.vertex(matrix, x1, y1, z1).color(color);
        vertices.vertex(matrix, x2, y2, z2).color(color);
        vertices.vertex(matrix, x3, y3, z3).color(color);
        vertices.vertex(matrix, x4, y4, z4).color(color);
    }

    private record RequestKey(
            String dimension,
            int anchorX,
            int anchorY,
            int anchorZ,
            int renderDistance,
            int revision
    ) {
    }

    private record BlockVoxel(int x, int y, int z) {
    }

    private record BlockCache(RequestKey key, List<BlockVoxel> blocks, boolean truncated) {
        static BlockCache empty() {
            return new BlockCache(new RequestKey("", 0, 0, 0, 0, -1), List.of(), false);
        }
    }
}
