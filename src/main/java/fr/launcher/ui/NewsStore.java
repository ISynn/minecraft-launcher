// © 2026 ISynn — tous droits réservés. Voir le fichier LICENSE.
package fr.launcher.ui;

import fr.launcher.core.news.NewsFeed;
import fr.launcher.core.news.NewsService;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

/**
 * Actualités partagées par l'accueil et l'écran Actualités : une seule requête au blog,
 * puis un rafraîchissement à la demande.
 */
public final class NewsStore {

    private final NewsService service;
    private final ReadOnlyObjectWrapper<NewsFeed> feed = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyBooleanWrapper loading = new ReadOnlyBooleanWrapper();

    NewsStore(NewsService service) {
        this.service = service;
    }

    /** Charge au premier besoin ; ne fait rien si c'est déjà fait ou en cours. */
    public void ensureLoaded() {
        if (feed.get() == null && !loading.get()) load(false);
    }

    /** Bouton « Actualiser » : interroge toujours le blog. */
    public void refresh() {
        load(true);
    }

    private void load(boolean force) {
        if (loading.get()) return;
        loading.set(true);
        // NewsService ne lève jamais d'exception : il y a toujours un repli (cache ou démo).
        Fx.async(() -> service.load(force), result -> {
            feed.set(result);
            loading.set(false);
        }, error -> loading.set(false));
    }

    public String sourceName() {
        return "Blog PokeIsland";
    }

    /** Null tant que rien n'est chargé. */
    public ReadOnlyObjectProperty<NewsFeed> feedProperty() {
        return feed.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty loadingProperty() {
        return loading.getReadOnlyProperty();
    }
}
