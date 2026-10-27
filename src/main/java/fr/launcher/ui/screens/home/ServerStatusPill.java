// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.ui.screens.home;

import fr.launcher.LauncherConfig;
import fr.launcher.core.server.ServerPinger;
import fr.launcher.ui.Fx;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * Statut réel du serveur configuré (protocole « Server List Ping »),
 * rafraîchi toutes les 30 s quand l'accueil est visible.
 */
final class ServerStatusPill extends HBox {

    private static final PseudoClass ONLINE = PseudoClass.getPseudoClass("online");

    private final Region dot = new Region();
    private final Label text = new Label("Statut du serveur…");
    private final Label detail = new Label();
    private final Timeline poll = new Timeline(new KeyFrame(Duration.ZERO, e -> refresh()),
            new KeyFrame(Duration.seconds(30)));

    ServerStatusPill() {
        super(8);
        getStyleClass().add("server-pill");
        setAlignment(Pos.CENTER_LEFT);
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        dot.getStyleClass().add("server-dot");
        text.getStyleClass().add("server-text");
        detail.getStyleClass().add("server-detail");
        getChildren().addAll(dot, text, detail);
        poll.setCycleCount(Timeline.INDEFINITE);
    }

    void setPolling(boolean on) {
        if (on) poll.play();
        else poll.stop();
    }

    private void refresh() {
        String host = LauncherConfig.SERVER_HOST;
        int port = LauncherConfig.SERVER_PORT;
        Fx.async(() -> ServerPinger.ping(host, port), status -> {
            pseudoClassStateChanged(ONLINE, status.online());
            String address = host + (port != 25565 ? ":" + port : "");
            if (status.online()) {
                text.setText("Serveur en ligne");
                detail.setText(status.players() + " / " + status.maxPlayers() + " joueurs · " + status.pingMs() + " ms");
            } else {
                text.setText("Serveur hors ligne");
                detail.setText(address);
            }
        }, error -> text.setText("Statut indisponible"));
    }
}
