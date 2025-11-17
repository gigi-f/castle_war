package com.castlewar.voxel;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders the voxel world using optimized meshes.
 */
public class VoxelRenderer implements Disposable {
    private final VoxelWorld world;
    private final VoxelMeshBuilder meshBuilder;
    private final Map<Long, Mesh> chunkMeshes = new HashMap<>();
    private final ShaderProgram shader;

    public VoxelRenderer(VoxelWorld world) {
        this.world = world;
        this.meshBuilder = new VoxelMeshBuilder();
        this.shader = createShader();
    }

    private ShaderProgram createShader() {
        String vertexShader = 
            "attribute vec3 a_position;\n" +
            "attribute vec4 a_color;\n" +
            "uniform mat4 u_projViewTrans;\n" +
            "varying vec4 v_color;\n" +
            "void main() {\n" +
            "    v_color = a_color;\n" +
            "    gl_Position = u_projViewTrans * vec4(a_position, 1.0);\n" +
            "}\n";

        String fragmentShader = 
            "#ifdef GL_ES\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "varying vec4 v_color;\n" +
            "void main() {\n" +
            "    gl_FragColor = v_color;\n" +
            "}\n";

        ShaderProgram shader = new ShaderProgram(vertexShader, fragmentShader);
        if (!shader.isCompiled()) {
            throw new RuntimeException("Shader compilation failed: " + shader.getLog());
        }
        return shader;
    }

    public void rebuildDirtyChunks() {
        for (Chunk chunk : world.getChunks()) {
            if (chunk.isDirty()) {
                rebuildChunkMesh(chunk);
                chunk.clearDirty();
            }
        }
    }

    private void rebuildChunkMesh(Chunk chunk) {
        long key = chunkKey(chunk.getChunkX(), chunk.getChunkY(), chunk.getChunkZ());
        
        // Dispose old mesh if exists
        Mesh oldMesh = chunkMeshes.remove(key);
        if (oldMesh != null) {
            oldMesh.dispose();
        }

        // Build new mesh
        Mesh newMesh = meshBuilder.buildChunkMesh(chunk, world);
        if (newMesh != null) {
            chunkMeshes.put(key, newMesh);
        }
    }

    public void render(Camera camera) {
        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", camera.combined);

        for (Mesh mesh : chunkMeshes.values()) {
            mesh.render(shader, GL20.GL_TRIANGLES);
        }
    }

    private long chunkKey(int x, int y, int z) {
        return ((long) x & 0xFFFFF) | (((long) y & 0xFFFFF) << 20) | (((long) z & 0xFFFFF) << 40);
    }

    @Override
    public void dispose() {
        shader.dispose();
        for (Mesh mesh : chunkMeshes.values()) {
            mesh.dispose();
        }
        chunkMeshes.clear();
    }
}
