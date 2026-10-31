package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.online;

import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.SAnime;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.SEpisode;

import kotlin.coroutines.Continuation;

public interface ResolvableAnimeSource {
    UriType getUriType(String uri);

    Object getAnime(String uri, Continuation<? super SAnime> continuation);

    Object getEpisode(String uri, Continuation<? super SEpisode> continuation);
}
