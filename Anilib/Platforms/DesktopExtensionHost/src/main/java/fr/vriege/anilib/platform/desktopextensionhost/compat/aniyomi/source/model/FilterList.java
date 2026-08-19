package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class FilterList extends AbstractList<Filter<?>> {
    private final List<Filter<?>> list;

    public FilterList(List<? extends Filter<?>> values) { list = new ArrayList<>(values); }
    public FilterList(Filter<?>... values) { this(Arrays.asList(values)); }
    public List<Filter<?>> getList() { return list; }
    public List<Filter<?>> component1() { return list; }
    public FilterList copy(List<? extends Filter<?>> values) { return new FilterList(values); }
    public int getSize() { return list.size(); }
    @Override public Filter<?> get(int index) { return list.get(index); }
    @Override public int size() { return list.size(); }
    @Override public Filter<?> set(int index, Filter<?> value) { return list.set(index, value); }
    @Override public void add(int index, Filter<?> value) { list.add(index, value); }
    @Override public Filter<?> remove(int index) { return list.remove(index); }
    @Override public boolean equals(Object value) {
        return value instanceof FilterList other && list.equals(other.list);
    }
    @Override public int hashCode() { return Objects.hashCode(list); }
    @Override public String toString() { return "FilterList(list=" + list + ')'; }
}
