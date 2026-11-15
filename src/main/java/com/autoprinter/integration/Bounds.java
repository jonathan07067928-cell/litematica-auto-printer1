package com.autoprinter.integration;

/** An inclusive block-coordinate box. Plain data, no Minecraft or Litematica types. */
public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public static Bounds ofCorners(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Bounds(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    public Bounds union(Bounds other) {
        return new Bounds(Math.min(minX, other.minX), Math.min(minY, other.minY), Math.min(minZ, other.minZ),
                Math.max(maxX, other.maxX), Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }
}
