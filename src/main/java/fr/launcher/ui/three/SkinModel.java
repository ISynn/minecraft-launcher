package fr.launcher.ui.three;

import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.transform.Rotate;

/**
 * Personnage Minecraft en 3D construit à partir d'un skin (64×64 ou ancien format 64×32).
 * Unités = pixels du skin. Pieds en y = 0, tête en y = -32, le personnage regarde vers la caméra (-z).
 */
final class SkinModel {

    final Group root = new Group();
    final Rotate headYaw = new Rotate(0, 0, -24, 0, Rotate.Y_AXIS);
    final Rotate headPitch = new Rotate(0, 0, -24, 0, Rotate.X_AXIS);
    final Rotate rightArmSwing;
    final Rotate rightArmSpread;
    final Rotate leftArmSwing;
    final Rotate leftArmSpread;

    private final int texW;
    private final int texH;
    private final float eps;

    SkinModel(Image skin) {
        texW = (int) skin.getWidth();
        texH = (int) skin.getHeight();
        eps = PixelTextures.eps();
        boolean legacy = texH == 32;
        boolean slim = !legacy && skin.getPixelReader().getColor(54, 20).getOpacity() == 0;
        int arm = slim ? 3 : 4;
        PhongMaterial material = PixelTextures.matte(PixelTextures.upscale(skin));

        Group rightLeg = part(material, -4, -12, -2, 4, 12, 4, 0, 16, legacy ? -1 : 0, 32, 0.25f);
        Group leftLeg = part(material, 0, -12, -2, 4, 12, 4, legacy ? 0 : 16, legacy ? 16 : 48,
                legacy ? -1 : 0, 48, 0.25f);
        Group body = part(material, -4, -24, -2, 8, 12, 4, 16, 16, legacy ? -1 : 16, 32, 0.25f);
        Group rightArm = part(material, -4 - arm, -24, -2, arm, 12, 4, 40, 16, legacy ? -1 : 40, 32, 0.25f);
        Group leftArm = part(material, 4, -24, -2, arm, 12, 4, legacy ? 40 : 32, legacy ? 16 : 48,
                legacy ? -1 : 48, 48, 0.25f);
        Group head = part(material, -4, -32, -4, 8, 8, 8, 0, 0, 32, 0, 0.5f);

        rightArmSwing = new Rotate(0, -4 - arm / 2.0, -22, 0, Rotate.X_AXIS);
        rightArmSpread = new Rotate(0, -4, -22, 0, Rotate.Z_AXIS);
        leftArmSwing = new Rotate(0, 4 + arm / 2.0, -22, 0, Rotate.X_AXIS);
        leftArmSpread = new Rotate(0, 4, -22, 0, Rotate.Z_AXIS);
        rightArm.getTransforms().addAll(rightArmSpread, rightArmSwing);
        leftArm.getTransforms().addAll(leftArmSpread, leftArmSwing);
        head.getTransforms().addAll(headYaw, headPitch);

        // La tête en dernier : sa couche « chapeau » transparente doit être dessinée après le reste.
        root.getChildren().addAll(rightLeg, leftLeg, body, rightArm, leftArm, head);
    }

    /** Un membre : la couche de base + la couche extérieure (veste, chapeau…) légèrement plus grande. */
    private Group part(PhongMaterial material, float x, float y, float z, int w, int h, int d,
                       int u, int v, int overlayU, int overlayV, float inflate) {
        Group group = new Group();
        MeshBuilder base = new MeshBuilder();
        box(base, x, y, z, w, h, d, u, v, 0);
        group.getChildren().add(base.build(material));
        if (overlayU >= 0) {
            MeshBuilder overlay = new MeshBuilder();
            box(overlay, x, y, z, w, h, d, overlayU, overlayV, inflate);
            group.getChildren().add(overlay.build(material));
        }
        return group;
    }

    /** Pavé texturé selon le patron officiel des skins : [droite][avant][gauche][arrière] sous [dessus][dessous]. */
    private void box(MeshBuilder b, float x, float y, float z, int w, int h, int d, int u, int v, float inflate) {
        float bx = x - inflate;
        float by = y - inflate;
        float bz = z - inflate;
        float bw = w + inflate * 2;
        float bh = h + inflate * 2;
        float bd = d + inflate * 2;
        rect(b, MeshBuilder.Face.FRONT, bx, by, bz, bw, bh, bd, u + d, v + d, w, h);
        rect(b, MeshBuilder.Face.BACK, bx, by, bz, bw, bh, bd, u + d + w + d, v + d, w, h);
        rect(b, MeshBuilder.Face.RIGHT, bx, by, bz, bw, bh, bd, u, v + d, d, h);
        rect(b, MeshBuilder.Face.LEFT, bx, by, bz, bw, bh, bd, u + d + w, v + d, d, h);
        rect(b, MeshBuilder.Face.TOP, bx, by, bz, bw, bh, bd, u + d, v, w, d);
        rect(b, MeshBuilder.Face.BOTTOM, bx, by, bz, bw, bh, bd, u + d + w, v, w, d);
    }

    private void rect(MeshBuilder b, MeshBuilder.Face face, float x, float y, float z, float w, float h, float d,
                      int ru, int rv, int rw, int rh) {
        b.face(face, x, y, z, w, h, d,
                (ru + eps) / texW, (rv + eps) / texH, (ru + rw - eps) / texW, (rv + rh - eps) / texH);
    }
}
