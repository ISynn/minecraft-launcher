package fr.launcher.ui.components;

import fr.launcher.ui.Fx;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Fabrique des éléments récurrents : boutons, cartes, en-têtes, badges.
 * Toute l'apparence vient des classes CSS ({@code theme.css}) : ce fichier ne fixe que la structure.
 */
public final class Ui {

    public enum Tone { NEUTRAL, ACCENT, SUCCESS, WARNING, DANGER, DEMO }

    private Ui() {
    }

    /** Bouton d'action principale (jaune). Une seule action principale par écran. */
    public static Button primary(String text, String icon) {
        return button(text, icon, "btn-primary");
    }

    public static Button secondary(String text, String icon) {
        return button(text, icon, "btn-secondary");
    }

    public static Button ghost(String text, String icon) {
        return button(text, icon, "btn-ghost");
    }

    public static Button danger(String text, String icon) {
        return button(text, icon, "btn-danger");
    }

    /** Bouton carré sans texte : le libellé est porté par l'infobulle (et lu par les lecteurs d'écran). */
    public static Button icon(String icon, String label) {
        Button b = new Button();
        b.setGraphic(Fx.icon(icon, 18));
        b.getStyleClass().addAll("btn", "btn-icon");
        b.setAccessibleText(label);
        b.setTooltip(tooltip(label));
        return b;
    }

    private static Button button(String text, String icon, String style) {
        Button b = new Button(text);
        if (icon != null) b.setGraphic(Fx.icon(icon, 16));
        b.getStyleClass().addAll("btn", style);
        b.setMnemonicParsing(false);
        return b;
    }

    public static Tooltip tooltip(String text) {
        Tooltip tip = new Tooltip(text);
        tip.setShowDelay(Duration.millis(250));
        return tip;
    }

    /** Carte à fond sombre translucide, avec un en-tête icône + titre facultatif. */
    public static VBox card(String icon, String title, Node... content) {
        VBox card = new VBox(14);
        card.getStyleClass().add("card");
        if (title != null) card.getChildren().add(cardHead(icon, title));
        card.getChildren().addAll(content);
        return card;
    }

    /** En-tête de carte : icône, titre, puis éléments alignés à droite (valeur, badge…). */
    public static HBox cardHead(String icon, String title, Node... trailing) {
        Label label = new Label(title);
        label.getStyleClass().add("card-title");
        HBox head = icon != null ? new HBox(10, Fx.icon(icon, 18), label) : new HBox(10, label);
        if (trailing.length > 0) {
            head.getChildren().add(Fx.spacer());
            head.getChildren().addAll(trailing);
        }
        head.getStyleClass().add("card-head");
        head.setAlignment(Pos.CENTER_LEFT);
        return head;
    }

    /** Page défilante standard des écrans secondaires (marges et largeur communes). */
    public static ScrollPane page(Node... children) {
        VBox content = new VBox(20, children);
        content.getStyleClass().add("page");
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("page-scroll");
        return scroll;
    }

    /**
     * Colonnes de même largeur, alignées en haut. Une grille plutôt qu'une HBox : JavaFX y calcule
     * correctement la hauteur des textes à retour à la ligne.
     */
    public static GridPane columns(Node... columns) {
        GridPane grid = new GridPane();
        grid.setHgap(18);
        for (int i = 0; i < columns.length; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(100.0 / columns.length);
            c.setFillWidth(true);
            grid.getColumnConstraints().add(c);
            GridPane.setValignment(columns[i], VPos.TOP);
            grid.add(columns[i], i, 0);
        }
        return grid;
    }

    /** Titre de page + sous-titre, avec des éléments optionnels alignés à droite (badges, actions). */
    public static HBox pageHeader(String title, String subtitle, Node... trailing) {
        Label t = new Label(title);
        t.getStyleClass().add("page-title");
        VBox text = new VBox(2, t);
        if (subtitle != null) {
            Label s = new Label(subtitle);
            s.getStyleClass().add("page-subtitle");
            s.setWrapText(true);
            text.getChildren().add(s);
        }
        HBox header = new HBox(16, text, Fx.spacer());
        header.getChildren().addAll(trailing);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("page-header");
        return header;
    }

    public static Label badge(String text, Tone tone) {
        Label badge = new Label(text);
        badge.getStyleClass().addAll("badge", "badge-" + tone.name().toLowerCase());
        return badge;
    }

    /** Badge obligatoire sur toute donnée qui ne vient pas d'un vrai service. */
    public static Label demoBadge() {
        Label badge = badge("DÉMO", Tone.DEMO);
        badge.setTooltip(tooltip("Données de démonstration : aucune API PokeIsland n'est utilisée."));
        return badge;
    }

    public static Label muted(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("text-muted");
        label.setWrapText(true);
        return label;
    }
}
