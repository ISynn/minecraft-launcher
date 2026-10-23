package fr.launcher.ui.three;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;

import java.util.Random;

/** Textures pixel art générées par le code (aucune image de Mojang) et agrandissement « pixels nets ». */
final class PixelTextures {

    enum Tile { GRASS_TOP, GRASS_SIDE, DIRT, STONE, LOG_SIDE, LOG_TOP, LEAVES }

    static final int TILE = 16;
    private static final int SCALE = 8;
    /** Recul d'un demi-texel agrandi : évite que le filtrage GPU mélange deux tuiles voisines. */
    private static final float EPS = 0.5f / SCALE;

    private static final int[] GRASS = {0xff5f9e3c, 0xff6aab45, 0xff57933a, 0xff79ba52, 0xff4c8633};
    private static final int[] DIRT = {0xff866043, 0xff79553a, 0xff8f6a48, 0xff6c4c33, 0xff967453};
    private static final int[] STONE = {0xff7d7d80, 0xff8a8a8d, 0xff707073, 0xff96969a, 0xff646467};
    private static final int[] BARK = {0xff6b5132, 0xff5b432a, 0xff7a5d3a, 0xff4e3922};
    private static final int[] WOOD = {0xffb8945f, 0xff9e7c4c};
    private static final int[] LEAVES = {0xff3f8a2e, 0xff4b9a37, 0xff347a26, 0xff5aa842};

    private PixelTextures() {
    }

    /** Atlas des blocs : une rangée de tuiles 16×16, agrandie ×8. */
    static Image blockAtlas() {
        Tile[] tiles = Tile.values();
        int w = TILE * tiles.length;
        int[] px = new int[w * TILE];
        Random r = new Random(1337);
        for (Tile tile : tiles) {
            int ox = tile.ordinal() * TILE;
            for (int y = 0; y < TILE; y++) {
                for (int x = 0; x < TILE; x++) {
                    px[y * w + ox + x] = pixel(tile, x, y, r);
                }
            }
        }
        return upscale(px, w, TILE);
    }

    private static int pixel(Tile tile, int x, int y, Random r) {
        return switch (tile) {
            case GRASS_TOP -> pick(GRASS, r);
            case DIRT -> pick(DIRT, r);
            case STONE -> r.nextInt(7) == 0 ? STONE[4] : pick(STONE, r);
            case GRASS_SIDE -> {
                boolean green = y < 3 || (y == 3 && r.nextBoolean()) || (y == 4 && r.nextInt(5) == 0);
                yield green ? pick(GRASS, r) : pick(DIRT, r);
            }
            case LOG_SIDE -> (x % 4 == 0 && r.nextInt(3) > 0) ? BARK[3] : pick(BARK, r);
            case LOG_TOP -> {
                double dist = Math.max(Math.abs(x - 7.5), Math.abs(y - 7.5));
                yield dist > 6.5 ? pick(BARK, r) : WOOD[((int) dist) % 2];
            }
            case LEAVES -> r.nextInt(6) == 0 ? 0x00000000 : pick(LEAVES, r);
        };
    }

    private static int pick(int[] palette, Random r) {
        return palette[r.nextInt(palette.length)];
    }

    /** Coordonnées UV {u0, v0, u1, v1} d'une tuile de l'atlas. */
    static float[] uv(Tile tile) {
        float count = Tile.values().length * TILE;
        int ox = tile.ordinal() * TILE;
        return new float[]{(ox + EPS) / count, EPS / TILE, (ox + TILE - EPS) / count, (TILE - EPS) / TILE};
    }

    /**
     * Agrandit une image en « plus proche voisin ». Le GPU lisse toujours les textures 3D :
     * en lui donnant une texture 8× plus grande, ce lissage devient invisible et les pixels restent carrés.
     */
    static Image upscale(Image source) {
        int w = (int) source.getWidth();
        int h = (int) source.getHeight();
        int[] px = new int[w * h];
        source.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), px, 0, w);
        return upscale(px, w, h);
    }

    private static Image upscale(int[] px, int w, int h) {
        int sw = w * SCALE;
        int[] out = new int[sw * h * SCALE];
        for (int y = 0; y < h * SCALE; y++) {
            int row = (y / SCALE) * w;
            for (int x = 0; x < sw; x++) out[y * sw + x] = px[row + x / SCALE];
        }
        WritableImage image = new WritableImage(sw, h * SCALE);
        image.getPixelWriter().setPixels(0, 0, sw, h * SCALE, PixelFormat.getIntArgbInstance(), out, 0, sw);
        return image;
    }

    /** Matériau mat (pas de reflet brillant), comme dans Minecraft. */
    static PhongMaterial matte(Image texture) {
        PhongMaterial material = new PhongMaterial(Color.WHITE);
        material.setDiffuseMap(texture);
        material.setSpecularColor(Color.BLACK);
        return material;
    }

    static float eps() {
        return EPS;
    }
}
