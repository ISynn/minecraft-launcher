package fr.launcher.ui.three;

import javafx.scene.paint.Material;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;

/**
 * Construit un maillage 3D face par face (pavés façon Minecraft).
 *
 * <p>Repère JavaFX : x vers la droite, y vers le bas, z vers l'intérieur de l'écran.
 * La caméra regarde vers +z, donc l'avant d'un modèle est sa face z minimum.
 * Les normales sont fournies explicitement : l'éclairage est juste quel que soit l'ordre des sommets.</p>
 */
final class MeshBuilder {

    /** Une face de pavé : sa normale et ses coins (haut-gauche, haut-droit, bas-gauche, bas-droit vus de face). */
    enum Face {
        FRONT(0, 0, -1, new int[][]{{0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {1, 1, 0}}),
        BACK(0, 0, 1, new int[][]{{1, 0, 1}, {0, 0, 1}, {1, 1, 1}, {0, 1, 1}}),
        /** Côté x négatif : le flanc droit d'un personnage qui nous fait face. */
        RIGHT(-1, 0, 0, new int[][]{{0, 0, 1}, {0, 0, 0}, {0, 1, 1}, {0, 1, 0}}),
        LEFT(1, 0, 0, new int[][]{{1, 0, 0}, {1, 0, 1}, {1, 1, 0}, {1, 1, 1}}),
        TOP(0, -1, 0, new int[][]{{0, 0, 1}, {1, 0, 1}, {0, 0, 0}, {1, 0, 0}}),
        BOTTOM(0, 1, 0, new int[][]{{0, 1, 0}, {1, 1, 0}, {0, 1, 1}, {1, 1, 1}});

        final int dx;
        final int dy;
        final int dz;
        final int[][] corners;

        Face(int dx, int dy, int dz, int[][] corners) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.corners = corners;
        }
    }

    private final TriangleMesh mesh = new TriangleMesh(VertexFormat.POINT_NORMAL_TEXCOORD);

    MeshBuilder() {
        for (Face f : Face.values()) mesh.getNormals().addAll(f.dx, f.dy, f.dz);
    }

    /** Ajoute une face du pavé (x, y, z, w, h, d) texturée par le rectangle UV (u0, v0) → (u1, v1). */
    void face(Face f, float x, float y, float z, float w, float h, float d,
              float u0, float v0, float u1, float v1) {
        int p = mesh.getPoints().size() / 3;
        for (int[] c : f.corners) mesh.getPoints().addAll(x + c[0] * w, y + c[1] * h, z + c[2] * d);
        int t = mesh.getTexCoords().size() / 2;
        mesh.getTexCoords().addAll(u0, v0, u1, v0, u0, v1, u1, v1);
        int n = f.ordinal();
        mesh.getFaces().addAll(
                p, n, t, p + 2, n, t + 2, p + 1, n, t + 1,
                p + 1, n, t + 1, p + 2, n, t + 2, p + 3, n, t + 3);
    }

    boolean isEmpty() {
        return mesh.getPoints().size() == 0;
    }

    MeshView build(Material material) {
        MeshView view = new MeshView(mesh);
        view.setMaterial(material);
        view.setCullFace(CullFace.NONE);
        return view;
    }
}
