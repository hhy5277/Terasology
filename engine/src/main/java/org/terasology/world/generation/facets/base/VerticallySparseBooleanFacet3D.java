// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.world.generation.facets.base;

import org.joml.Vector3i;
import org.joml.Vector3ic;
import org.terasology.math.JomlUtil;
import org.terasology.math.Region3i;
import org.terasology.world.generation.Border3D;
import org.terasology.world.generation.WorldFacet3D;

import java.util.HashSet;
import java.util.Set;

/**
 * Sparse facet data optimised for looking up what positions are marked within
 * a vertical column.
 */
public class VerticallySparseBooleanFacet3D implements WorldFacet3D {

    private Region3i worldRegion;
    private Region3i relativeRegion;
    private Set<Integer>[] data;

    public VerticallySparseBooleanFacet3D(Region3i targetRegion, Border3D border) {
        worldRegion = border.expandTo3D(targetRegion);
        relativeRegion = border.expandTo3D(targetRegion.size());
        data = new Set[worldRegion.sizeX() * worldRegion.sizeZ()];
        for (int i = 0; i < data.length; i++) {
            data[i] = new HashSet<Integer>();
        }
    }

    @Override
    public Region3i getWorldRegion() {
        return worldRegion;
    }

    @Override
    public Region3i getRelativeRegion() {
        return relativeRegion;
    }

    public boolean get(int x, int y, int z) {
        return get(new Vector3i(x, y, z));
    }

    public boolean get(Vector3ic pos) {
        Set<Integer> column = data[getRelativeIndex(pos)];
        return column.contains(pos.y() + worldRegion.minY() - relativeRegion.minY());
    }

    public void set(int x, int y, int z, boolean value) {
        set(new Vector3i(x, y, z), value);
    }

    public void set(Vector3ic pos, boolean value) {
        Set<Integer> column = data[getRelativeIndex(pos)];
        int y = pos.y() + worldRegion.minY() - relativeRegion.minY();
        if (value) {
            column.add(y);
        } else {
            column.remove(y);
        }
    }

    public boolean getWorld(int x, int y, int z) {
        return getWorld(new Vector3i(x, y, z));
    }

    public boolean getWorld(Vector3ic pos) {
        Set<Integer> column = data[getWorldIndex(pos)];
        return column.contains(pos.y());
    }

    public void setWorld(int x, int y, int z, boolean value) {
        setWorld(new Vector3i(x, y, z), value);
    }

    public void setWorld(Vector3ic pos, boolean value) {
        Set<Integer> column = data[getWorldIndex(pos)];
        if (value) {
            column.add(pos.y());
        } else {
            column.remove(pos.y());
        }
    }

    public Set<Integer> getWorldColumn(int x, int z) {
        return data[getWorldIndex(new Vector3i(x, worldRegion.minY(), z))];
    }

    protected final int getRelativeIndex(Vector3ic pos) {
        if (!relativeRegion.encompasses(JomlUtil.from(pos))) {
            throw new IllegalArgumentException(String.format("Out of bounds: %s for region %s", pos.toString(), relativeRegion.toString()));
        }
        return pos.x() - relativeRegion.minX() + relativeRegion.sizeX() * (pos.z() - relativeRegion.minZ());
    }

    protected final int getWorldIndex(Vector3ic pos) {
        if (!worldRegion.encompasses(JomlUtil.from(pos))) {
            throw new IllegalArgumentException(String.format("Out of bounds: %s for region %s", pos.toString(), worldRegion.toString()));
        }
        return pos.x() - worldRegion.minX() + worldRegion.sizeX() * (pos.z() - worldRegion.minZ());
    }
}