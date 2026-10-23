package fr.launcher;

import fr.launcher.ui.LauncherUi;
import javafx.application.Application;
import javafx.stage.Stage;

public final class LauncherApp extends Application {

    @Override
    public void start(Stage stage) {
        LauncherUi.start(stage, getHostServices());
    }
}
