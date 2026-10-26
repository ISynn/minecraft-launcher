module fr.launcher {
    requires javafx.controls;
    requires java.net.http;
    requires java.logging;
    requires jdk.management;
    requires com.google.gson;

    exports fr.launcher to javafx.graphics;
    opens fr.launcher.core.auth to com.google.gson;
    opens fr.launcher.core.settings to com.google.gson;
    opens fr.launcher.core.modpack to com.google.gson;
    opens fr.launcher.core.news to com.google.gson;
}
