package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AnimeFilterList extends AbstractList<AnimeFilter<?>> {
    private final List<AnimeFilter<?>> list;

    public AnimeFilterList(List<? extends AnimeFilter<?>> values) { list = new ArrayList<>(values); }
    public AnimeFilterList(AnimeFilter<?>... values) { this(Arrays.asList(values)); }
    public List<AnimeFilter<?>> getList() { return list; }
    public int getSize() { return list.size(); }
    @Override public AnimeFilter<?> get(int index) { return list.get(index); }
    @Override public int size() { return list.size(); }
    @Override public AnimeFilter<?> set(int index, AnimeFilter<?> value) { return list.set(index, value); }
    @Override public void add(int index, AnimeFilter<?> value) { list.add(index, value); }
    @Override public AnimeFilter<?> remove(int index) { return list.remove(index); }
}
