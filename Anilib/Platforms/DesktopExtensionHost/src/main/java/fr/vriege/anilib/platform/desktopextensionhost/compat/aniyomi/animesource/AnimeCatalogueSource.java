package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource;

import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.AnimeFilterList;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.AnimesPage;

import kotlin.coroutines.Continuation;

import rx.Observable;

public interface AnimeCatalogueSource extends AnimeSource {
    default AnimeFilterList getFilterList() {
        return new AnimeFilterList();
    }

    default Object getPopularAnime(int page, Continuation<? super AnimesPage> continuation) {
        return getPopularAnime$suspendImpl(this, page, continuation);
    }

    default Object getLatestUpdates(int page, Continuation<? super AnimesPage> continuation) {
        return getLatestUpdates$suspendImpl(this, page, continuation);
    }

    default Object getSearchAnime(
            int page,
            String query,
            AnimeFilterList filters,
            Continuation<? super AnimesPage> continuation) {
        return getSearchAnime$suspendImpl(this, page, query, filters, continuation);
    }

    default Observable<AnimesPage> fetchPopularAnime(int page) {
        throw new UnsupportedOperationException("Source does not implement popular anime");
    }

    default Observable<AnimesPage> fetchLatestUpdates(int page) {
        throw new UnsupportedOperationException("Source does not implement latest updates");
    }

    default Observable<AnimesPage> fetchSearchAnime(
            int page, String query, AnimeFilterList filters) {
        throw new UnsupportedOperationException("Source does not implement anime search");
    }

    static Object getPopularAnime$suspendImpl(
            AnimeCatalogueSource source, int page, Continuation<? super AnimesPage> c) {
        return source.fetchPopularAnime(page).toBlocking().single();
    }

    static Object getLatestUpdates$suspendImpl(
            AnimeCatalogueSource source, int page, Continuation<? super AnimesPage> c) {
        return source.fetchLatestUpdates(page).toBlocking().single();
    }

    static Object getSearchAnime$suspendImpl(
            AnimeCatalogueSource source,
            int page,
            String query,
            AnimeFilterList filters,
            Continuation<? super AnimesPage> c) {
        return source.fetchSearchAnime(page, query, filters).toBlocking().single();
    }

    static Object access$getPopularAnime$jd(
            AnimeCatalogueSource source, int page, Continuation<? super AnimesPage> c) {
        return getPopularAnime$suspendImpl(source, page, c);
    }

    static Object access$getLatestUpdates$jd(
            AnimeCatalogueSource source, int page, Continuation<? super AnimesPage> c) {
        return getLatestUpdates$suspendImpl(source, page, c);
    }

    static Object access$getSearchAnime$jd(
            AnimeCatalogueSource source,
            int page,
            String query,
            AnimeFilterList filters,
            Continuation<? super AnimesPage> c) {
        return getSearchAnime$suspendImpl(source, page, query, filters, c);
    }

    final class DefaultImpls {
        private DefaultImpls() {}

        public static Object getPopularAnime(
                AnimeCatalogueSource source, int page, Continuation<? super AnimesPage> c) {
            return getPopularAnime$suspendImpl(source, page, c);
        }

        public static Object getLatestUpdates(
                AnimeCatalogueSource source, int page, Continuation<? super AnimesPage> c) {
            return getLatestUpdates$suspendImpl(source, page, c);
        }

        public static Object getSearchAnime(
                AnimeCatalogueSource source,
                int page,
                String query,
                AnimeFilterList filters,
                Continuation<? super AnimesPage> c) {
            return getSearchAnime$suspendImpl(source, page, query, filters, c);
        }
    }
}
