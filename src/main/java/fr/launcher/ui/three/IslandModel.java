package fr.launcher.ui.three;

import fr.launcher.ui.three.PixelTextures.Tile;
import javafx.scene.Node;
import javafx.scene.paint.PhongMaterial;

import java.util.HashMap;
import java.util.Map;

/**
 * Petite île flottante en blocs (herbe, terre, pierre, arbre).
 *
 * <p>Comme dans Minecraft, seules les faces visibles sont générées (une face collée à un bloc plein
 * est invisible), et tout est regroupé en deux maillages : un opaque et un pour les feuilles transparentes.
 * Deux objets à dessiner au lieu de plusieurs centaines de cubes.</p>
 */
final class IslandModel {

    static final int BLOCK = 16;

    enum Block { GRASS, DIRT, STONE, LOG, LEAVES }

    /** Les deux maillages : à ajouter à la scène avant (solide) et après (feuilles) le personnage. */
    record Meshes(Node solid, Node leaves) {
    }

    private final Map<Long, Block> blocks = new HashMap<>();

    private IslandModel() {
        // Couche du dessus : un disque 5×5 sans les coins, en herbe.
        for (int i = -2; i <= 2; i++) {
            for (int k = -2; k <= 2; k++) {
                if (Math.abs(i) == 2 && Math.abs(k) == 2) continue;
                set(i, 0, k, Block.GRASS);
                // Dessous qui s'affine en pointe.
                if (Math.abs(i) + Math.abs(k) <= 2) set(i, 1, k, (i == 1 && k == -1) ? Block.STONE : Block.DIRT);
                if (Math.abs(i) + Math.abs(k) <= 1) set(i, 2, k, (i + k) % 2 == 0 ? Block.STONE : Block.DIRT);
            }
        }
        set(0, 3, 0, Block.STONE);
        set(0, 3, 1, Block.STONE);

        // Un arbre à l'arrière gauche.
        int ti = -2;
        int tk = 1;
        for (int j = -4; j <= -1; j++) set(ti, j, tk, Block.LOG);
        for (int i = ti - 1; i <= ti + 1; i++) {
            for (int k = tk - 1; k <= tk + 1; k++) {
                for (int j = -4; j <= -3; j++) {
                    if (get(i, j, k) == null) set(i, j, k, Block.LEAVES);
                }
            }
        }
        set(ti, -5, tk, Block.LEAVES);
        set(ti - 1, -5, tk, Block.LEAVES);
        set(ti + 1, -5, tk, Block.LEAVES);
        set(ti, -5, tk - 1, Block.LEAVES);
        set(ti, -5, tk + 1, Block.LEAVES);
        set(ti, -6, tk, Block.LEAVES);
        // Un deuxième petit buisson à droite pour équilibrer.
        set(2, -1, -1, Block.LEAVES);
    }

    static Meshes build() {
        IslandModel island = new IslandModel();
        PhongMaterial material = PixelTextures.matte(PixelTextures.blockAtlas());
        MeshBuilder solid = new MeshBuilder();
        MeshBuilder leaves = new MeshBuilder();

        for (Map.Entry<Long, Block> entry : island.blocks.entrySet()) {
            long key = entry.getKey();
            int i = (int) (short) (key >> 32);
            int j = (int) (short) (key >> 16);
            int k = (int) (short) key;
            Block block = entry.getValue();
            MeshBuilder target = block == Block.LEAVES ? leaves : solid;
            for (MeshBuilder.Face face : MeshBuilder.Face.values()) {
                Block neighbour = island.get(i + face.dx, j + face.dy, k + face.dz);
                boolean hidden = neighbour != null && (neighbour != Block.LEAVES || block == Block.LEAVES);
                if (hidden) continue;
                float[] uv = PixelTextures.uv(tile(block, face));
                target.face(face, i * BLOCK - BLOCK / 2f, j * BLOCK, k * BLOCK - BLOCK / 2f, BLOCK, BLOCK, BLOCK,
                        uv[0], uv[1], uv[2], uv[3]);
            }
        }
        return new Meshes(solid.build(material), leaves.build(material));
    }

    private static Tile tile(Block block, MeshBuilder.Face face) {
        boolean vertical = face == MeshBuilder.Face.TOP || face == MeshBuilder.Face.BOTTOM;
        return switch (block) {
            case GRASS -> face == MeshBuilder.Face.TOP ? Tile.GRASS_TOP
                    : face == MeshBuilder.Face.BOTTOM ? Tile.DIRT : Tile.GRASS_SIDE;
            case DIRT -> Tile.DIRT;
            case STONE -> Tile.STONE;
            case LOG -> vertical ? Tile.LOG_TOP : Tile.LOG_SIDE;
            case LEAVES -> Tile.LEAVES;
        };
    }

    private void set(int i, int j, int k, Block block) {
        blocks.put(key(i, j, k), block);
    }

    private Block get(int i, int j, int k) {
        return blocks.get(key(i, j, k));
    }

    /** Coordonnées (y vers le bas : j = couche, 0 = surface) compactées dans un long. */
    private static long key(int i, int j, int k) {
        return ((long) (i & 0xffff) << 32) | ((long) (j & 0xffff) << 16) | (k & 0xffff);
    }
}
