package com.castlewar.voxel;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;

/**
 * Builds optimized meshes for voxel chunks with face culling.
 */
public class VoxelMeshBuilder {
    private static final int VERTICES_PER_FACE = 4;
    private static final int FLOATS_PER_VERTEX = 6; // x, y, z, r, g, b
    
    private final FloatArray vertices = new FloatArray();
    private final ShortArray indices = new ShortArray();
    private short vertexCount = 0;

    public Mesh buildChunkMesh(Chunk chunk, VoxelWorld world) {
        vertices.clear();
        indices.clear();
        vertexCount = 0;

        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < Chunk.CHUNK_SIZE; y++) {
                for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                    BlockType block = chunk.getBlock(x, y, z);
                    if (!block.isSolid()) continue;

                    int worldX = chunk.getChunkX() * Chunk.CHUNK_SIZE + x;
                    int worldY = chunk.getChunkY() * Chunk.CHUNK_SIZE + y;
                    int worldZ = chunk.getChunkZ() * Chunk.CHUNK_SIZE + z;

                    addBlockFaces(worldX, worldY, worldZ, block, world);
                }
            }
        }

        if (vertices.isEmpty()) {
            return null;
        }

        Mesh mesh = new Mesh(true, vertices.size / FLOATS_PER_VERTEX, indices.size,
                VertexAttribute.Position(),
                VertexAttribute.ColorUnpacked());
        
        mesh.setVertices(vertices.items, 0, vertices.size);
        mesh.setIndices(indices.items, 0, indices.size);
        
        return mesh;
    }

    private void addBlockFaces(int x, int y, int z, BlockType block, VoxelWorld world) {
        float[] color = block.getColor();
        float x0 = x;
        float x1 = x + 1;
        float y0 = y;
        float y1 = y + 1;
        float z0 = z;
        float z1 = z + 1;

        // Check each face and only add if adjacent block is not solid (face culling)
        
        // Top face (+Z) - facing up
        if (!world.getBlock(x, y, z + 1).isSolid()) {
            addQuad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, color);
        }

        // Bottom face (-Z) - facing down
        if (!world.getBlock(x, y, z - 1).isSolid()) {
            addQuad(x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0, color);
        }

        // North face (+Y) - facing forward
        if (!world.getBlock(x, y + 1, z).isSolid()) {
            addQuad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, color);
        }

        // South face (-Y) - facing backward
        if (!world.getBlock(x, y - 1, z).isSolid()) {
            addQuad(x1, y0, z0, x0, y0, z0, x0, y0, z1, x1, y0, z1, color);
        }

        // East face (+X) - facing right
        if (!world.getBlock(x + 1, y, z).isSolid()) {
            addQuad(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, color);
        }

        // West face (-X) - facing left
        if (!world.getBlock(x - 1, y, z).isSolid()) {
            addQuad(x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, color);
        }
    }

    private void addQuad(float x1, float y1, float z1,
                         float x2, float y2, float z2,
                         float x3, float y3, float z3,
                         float x4, float y4, float z4,
                         float[] color) {
        // Add 4 vertices forming a quad (counter-clockwise winding)
        addVertex(x1, y1, z1, color);
        addVertex(x2, y2, z2, color);
        addVertex(x3, y3, z3, color);
        addVertex(x4, y4, z4, color);

        // Add 2 triangles (6 indices) with counter-clockwise winding
        short base = vertexCount;
        indices.add(base);
        indices.add((short) (base + 1));
        indices.add((short) (base + 2));
        
        indices.add(base);
        indices.add((short) (base + 2));
        indices.add((short) (base + 3));

        vertexCount += 4;
    }

    private void addVertex(float x, float y, float z, float[] color) {
        vertices.add(x);
        vertices.add(y);
        vertices.add(z);
        vertices.add(color[0]);
        vertices.add(color[1]);
        vertices.add(color[2]);
    }
}
