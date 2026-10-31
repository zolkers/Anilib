package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource;

import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.Hoster;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.SAnime;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.SEpisode;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.Video;

import kotlin.coroutines.Continuation;

import java.util.List;

public interface AnimeSource {
    long getId();

    String getName();

    default String getLang() {
        return "";
    }

    boolean getSupportsLatest();

    default Object getAnimeDetails(SAnime anime, Continuation<? super SAnime> continuation) {
        throw new UnsupportedOperationException("Source does not implement anime details");
    }

    default Object getEpisodeList(SAnime anime, Continuation<? super List<SEpisode>> continuation) {
        throw new UnsupportedOperationException("Source does not implement episodes");
    }

    default Object getSeasonList(SAnime anime, Continuation<? super List<SAnime>> continuation) {
        throw new UnsupportedOperationException("Source does not implement seasons");
    }

    default Object getHosterList(
            SEpisode episode, Continuation<? super List<Hoster>> continuation) {
        throw new UnsupportedOperationException("Source does not implement hosters");
    }

    default Object getVideoList(Hoster hoster, Continuation<? super List<Video>> continuation) {
        if (hoster.getVideoList() != null) return hoster.getVideoList();
        throw new UnsupportedOperationException("Source does not implement hoster resolution");
    }
}
