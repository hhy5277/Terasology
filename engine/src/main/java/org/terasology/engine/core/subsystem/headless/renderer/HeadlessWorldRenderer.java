// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.engine.core.subsystem.headless.renderer;

import com.google.common.collect.Lists;
import org.joml.Vector3ic;
import org.terasology.engine.config.Config;
import org.terasology.engine.context.Context;
import org.terasology.engine.logic.players.LocalPlayerSystem;
import org.terasology.engine.math.Region3i;
import org.terasology.engine.monitoring.PerformanceMonitor;
import org.terasology.engine.rendering.assets.material.Material;
import org.terasology.engine.rendering.cameras.Camera;
import org.terasology.engine.rendering.cameras.SubmersibleCamera;
import org.terasology.engine.rendering.dag.RenderGraph;
import org.terasology.engine.rendering.world.WorldRenderer;
import org.terasology.engine.rendering.world.viewDistance.ViewDistance;
import org.terasology.engine.world.WorldProvider;
import org.terasology.engine.world.chunks.ChunkConstants;
import org.terasology.engine.world.chunks.ChunkProvider;
import org.terasology.engine.world.chunks.RenderableChunk;
import org.terasology.math.geom.Vector3f;
import org.terasology.math.geom.Vector3i;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class HeadlessWorldRenderer implements WorldRenderer {

    private static final int MAX_CHUNKS =
            ViewDistance.MEGA.getChunkDistance().x() * ViewDistance.MEGA.getChunkDistance().y() * ViewDistance.MEGA.getChunkDistance().z();
    private final List<RenderableChunk> chunksInProximity = Lists.newArrayListWithCapacity(MAX_CHUNKS);
    private final WorldProvider worldProvider;
    private final ChunkProvider chunkProvider;
    private final Camera noCamera = new NullCamera(null, null);
    /* CHUNKS */
    private boolean pendingChunks;
    private final Vector3i chunkPos = new Vector3i();

    private final Config config;

    public HeadlessWorldRenderer(Context context) {
        this.worldProvider = context.get(WorldProvider.class);
        this.chunkProvider = context.get(ChunkProvider.class);
        LocalPlayerSystem localPlayerSystem = context.get(LocalPlayerSystem.class);
        localPlayerSystem.setPlayerCamera(noCamera);
        config = context.get(Config.class);
    }

    @Override
    public float getSecondsSinceLastFrame() {
        return 0;
    }

    @Override
    public Material getMaterial(String assetId) {
        return null;
    }

    @Override
    public boolean isFirstRenderingStageForCurrentFrame() {
        return false;
    }

    @Override
    public void onChunkLoaded(Vector3i pos) {

    }

    @Override
    public void onChunkUnloaded(Vector3i pos) {

    }

    @Override
    public SubmersibleCamera getActiveCamera() {
        return (SubmersibleCamera) noCamera;
    }

    @Override
    public void update(float delta) {

        worldProvider.processPropagation();

        // Free unused space
        PerformanceMonitor.startActivity("Update Chunk Cache");
        chunkProvider.completeUpdate();
        chunkProvider.beginUpdate();
        PerformanceMonitor.endActivity();

        PerformanceMonitor.startActivity("Update Close Chunks");
        updateChunksInProximity(false);
        PerformanceMonitor.endActivity();
    }

    @Override
    public void increaseTrianglesCount(int increase) {
        // we are not going to count triangles in headless
    }

    @Override
    public void increaseNotReadyChunkCount(int increase) {
        // we are not going to count not ready chunks in headless
    }

    @Override
    public void render(RenderingStage mono) {
        // TODO Auto-generated method stub
    }

    @Override
    public void requestTaskListRefresh() {

    }

    @Override
    public void dispose() {
        worldProvider.dispose();

    }

    @Override
    public boolean pregenerateChunks() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setViewDistance(ViewDistance viewDistance) {
        // TODO Auto-generated method stub

    }

    @Override
    public float getRenderingLightIntensityAt(Vector3f vector3f) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public float getMainLightIntensityAt(Vector3f worldPos) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public float getBlockLightIntensityAt(Vector3f worldPos) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public float getTimeSmoothedMainLightIntensity() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public float getMillisecondsSinceRenderingStart() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public RenderingStage getCurrentRenderStage() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getMetrics() {
        return "";
    }

    @Override
    public RenderGraph getRenderGraph() {
        return null;
    }

    /**
     * Updates the list of chunks around the player.
     *
     * @param force Forces the update
     * @return True if the list was changed
     */
    public boolean updateChunksInProximity(boolean force) {
        Vector3i newChunkPos = calcCamChunkOffset();

        // TODO: This should actually be done based on events from the ChunkProvider on new chunk availability/old
        //  chunk removal
        boolean chunksCurrentlyPending = false;
        if (!newChunkPos.equals(chunkPos) || force || pendingChunks) {
            Vector3ic viewingDistance = config.getRendering().getViewDistance().getChunkDistance();
            Region3i viewRegion = Region3i.createFromCenterExtents(newChunkPos, new Vector3i(viewingDistance.x() / 2,
                    viewingDistance.y() / 2, viewingDistance.z() / 2));
            if (chunksInProximity.size() == 0 || force || pendingChunks) {
                // just add all visible chunks
                chunksInProximity.clear();
                for (Vector3i chunkPosition : viewRegion) {
                    RenderableChunk c = chunkProvider.getChunk(chunkPosition);
                    if (c != null && worldProvider.getLocalView(c.getPosition()) != null) {
                        chunksInProximity.add(c);
                    } else {
                        chunksCurrentlyPending = true;
                    }
                }
            } else {
                Region3i oldRegion = Region3i.createFromCenterExtents(chunkPos, new Vector3i(viewingDistance.x() / 2,
                        viewingDistance.y() / 2, viewingDistance.z() / 2));

                Iterator<Vector3i> chunksForRemove = oldRegion.subtract(viewRegion);
                // remove
                while (chunksForRemove.hasNext()) {
                    Vector3i r = chunksForRemove.next();
                    RenderableChunk c = chunkProvider.getChunk(r);
                    if (c != null) {
                        chunksInProximity.remove(c);
                        c.disposeMesh();
                    }
                }

                // add
                for (Vector3i chunkPosition : viewRegion) {
                    RenderableChunk c = chunkProvider.getChunk(chunkPosition);
                    if (c != null && worldProvider.getLocalView(c.getPosition()) != null) {
                        chunksInProximity.add(c);
                    } else {
                        chunksCurrentlyPending = true;
                    }
                }
            }

            chunkPos.set(newChunkPos);
            pendingChunks = chunksCurrentlyPending;

            Collections.sort(chunksInProximity, new ChunkFrontToBackComparator());

            return true;
        }

        return false;
    }

    /**
     * Chunk position of the player.
     *
     * @return The player offset on the x-axis
     */
    private Vector3i calcCamChunkOffset() {
        return new Vector3i((int) (getActiveCamera().getPosition().x / ChunkConstants.SIZE_X),
                (int) (getActiveCamera().getPosition().y / ChunkConstants.SIZE_Y),
                (int) (getActiveCamera().getPosition().z / ChunkConstants.SIZE_Z));
    }

    private float distanceToCamera(RenderableChunk chunk) {
        Vector3f result = new Vector3f((chunk.getPosition().x + 0.5f) * ChunkConstants.SIZE_X, 0,
                (chunk.getPosition().z + 0.5f) * ChunkConstants.SIZE_Z);

        org.joml.Vector3f cameraPos = getActiveCamera().getPosition();
        result.x -= cameraPos.x;
        result.z -= cameraPos.z;

        return result.length();
    }

    private class ChunkFrontToBackComparator implements Comparator<RenderableChunk> {

        @Override
        public int compare(RenderableChunk o1, RenderableChunk o2) {
            double distance = distanceToCamera(o1);
            double distance2 = distanceToCamera(o2);

            if (o1 == null) {
                return -1;
            } else if (o2 == null) {
                return 1;
            }

            if (distance == distance2) {
                return 0;
            }

            return distance2 > distance ? -1 : 1;
        }
    }
}