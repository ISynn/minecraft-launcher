// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.ui.screens.home;

import fr.launcher.core.auth.Account;
import fr.launcher.ui.components.Avatar;
import fr.launcher.ui.components.Ui;
import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Profil du joueur : tête du skin, pseudo et état réel de la session (Microsoft ou démo hors ligne). */
final class ProfileChip extends Button {

    private static final PseudoClass DEMO = PseudoClass.getPseudoClass("demo");

    private final Avatar avatar = new Avatar(52, 10);
    private final Label name = new Label();
    private final Region dot = new Region();
    private final Label state = new Label();

    ProfileChip(ObservableValue<Account> account, Runnable onOpen) {
        getStyleClass().add("profile-chip");
        setMnemonicParsing(false);
        name.getStyleClass().add("profile-name");
        dot.getStyleClass().add("profile-dot");
        state.getStyleClass().add("profile-state");
        HBox stateRow = new HBox(6, dot, state);
        stateRow.setAlignment(Pos.CENTER_LEFT);
        VBox text = new VBox(2, name, stateRow);
        text.setAlignment(Pos.CENTER_LEFT);
        HBox content = new HBox(12, avatar, text);
        content.setAlignment(Pos.CENTER_LEFT);
        setGraphic(content);
        setOnAction(e -> onOpen.run());

        account.addListener((o, old, a) -> show(a));
        show(account.getValue());
    }

    private void show(Account account) {
        avatar.setAccount(account);
        if (account == null) {
            name.setText("Non connecté");
            state.setText("");
            return;
        }
        boolean demo = account.type() != Account.Type.MICROSOFT;
        name.setText(account.name());
        state.setText(demo ? "Mode démo" : "Compte Microsoft");
        dot.pseudoClassStateChanged(DEMO, demo);
        setAccessibleText("Mon compte : " + account.name() + ", " + state.getText());
        setTooltip(Ui.tooltip(demo
                ? "Compte hors ligne de test : il ne peut pas rejoindre un serveur en ligne."
                : "Mon compte"));
    }
}
