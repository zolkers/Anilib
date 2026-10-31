package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AnimeFilterList extends AbstractList<AnimeFilter<?>> {
    private final List<AnimeFilter<?>> list;

    public AnimeFilterList(List<? extends AnimeFilter<?>> values) {
        list = new ArrayList<>(values);
    }

    public AnimeFilterList(AnimeFilter<?>... values) {
        this(Arrays.asList(values));
    }

    public List<AnimeFilter<?>> getList() {
        return list;
    }

    public int getSize() {
        return list.size();
    }

    @Override
    public AnimeFilter<?> get(int index) {
        return list.get(index);
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public AnimeFilter<?> set(int index, AnimeFilter<?> value) {
        return list.set(index, value);
    }

    @Override
    public void add(int index, AnimeFilter<?> value) {
        list.add(index, value);
    }

    @Override
    public AnimeFilter<?> remove(int index) {
        return list.remove(index);
    }

    @Override
    public boolean add(AnimeFilter<?> value) {
        return list.add(value);
    }

    public void addFirst(AnimeFilter<?> value) {
        list.addFirst(value);
    }

    public void addLast(AnimeFilter<?> value) {
        list.addLast(value);
    }

    @Override
    public AnimeFilter<?> removeFirst() {
        return list.removeFirst();
    }

    @Override
    public AnimeFilter<?> removeLast() {
        return list.removeLast();
    }

    public boolean contains(AnimeFilter<?> value) {
        return list.contains(value);
    }

    public int indexOf(AnimeFilter<?> value) {
        return list.indexOf(value);
    }

    public int lastIndexOf(AnimeFilter<?> value) {
        return list.lastIndexOf(value);
    }

    public List<AnimeFilter<?>> component1() {
        return list;
    }

    public AnimeFilterList copy(List<? extends AnimeFilter<?>> values) {
        return new AnimeFilterList(values);
    }

    public static AnimeFilterList copy$default(
            AnimeFilterList source,
            List<? extends AnimeFilter<?>> values,
            int mask,
            Object marker) {
        return source.copy((mask & 1) == 0 ? values : source.list);
    }
}
