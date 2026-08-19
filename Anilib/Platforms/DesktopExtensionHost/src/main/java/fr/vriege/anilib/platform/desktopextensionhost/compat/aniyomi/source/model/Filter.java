package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model;

import java.util.List;
import java.util.Objects;

public abstract class Filter<T> {
    private final String name;
    private T state;

    protected Filter(String name, T state) {
        this.name = Objects.requireNonNull(name, "name");
        this.state = state;
    }

    public final String getName() { return name; }
    public final T getState() { return state; }
    public final void setState(T value) { state = value; }

    @Override public boolean equals(Object value) {
        return value instanceof Filter<?> other
                && name.equals(other.name)
                && Objects.equals(state, other.state);
    }

    @Override public int hashCode() { return 31 * name.hashCode() + Objects.hashCode(state); }

    public static class Header extends Filter<Object> {
        public Header(String name) { super(name, 0); }
    }

    public static class Separator extends Filter<Object> {
        public Separator(String name) { super(name, 0); }
        public Separator(String name, int mask, kotlin.jvm.internal.DefaultConstructorMarker marker) {
            this((mask & 1) == 0 ? name : "");
        }
        public Separator() { this(""); }
    }

    public abstract static class Select<V> extends Filter<Integer> {
        private final V[] values;
        public Select(String name, V[] values, int state) {
            super(name, state);
            this.values = Objects.requireNonNull(values, "values").clone();
        }
        public Select(
                String name,
                V[] values,
                int state,
                int mask,
                kotlin.jvm.internal.DefaultConstructorMarker marker) {
            this(name, values, (mask & 4) == 0 ? state : 0);
        }
        public final V[] getValues() { return values.clone(); }
    }

    public abstract static class Text extends Filter<String> {
        public Text(String name, String state) { super(name, state); }
        public Text(String name, String state, int mask, kotlin.jvm.internal.DefaultConstructorMarker marker) {
            this(name, (mask & 2) == 0 ? state : "");
        }
    }

    public abstract static class CheckBox extends Filter<Boolean> {
        public CheckBox(String name, boolean state) { super(name, state); }
        public CheckBox(String name, boolean state, int mask, kotlin.jvm.internal.DefaultConstructorMarker marker) {
            this(name, (mask & 2) == 0 && state);
        }
    }

    public abstract static class TriState extends Filter<Integer> {
        public static final int STATE_IGNORE = 0;
        public static final int STATE_INCLUDE = 1;
        public static final int STATE_EXCLUDE = 2;

        public TriState(String name, int state) { super(name, state); }
        public TriState(String name, int state, int mask, kotlin.jvm.internal.DefaultConstructorMarker marker) {
            this(name, (mask & 2) == 0 ? state : STATE_IGNORE);
        }
        public final boolean isIgnored() { return getState() == STATE_IGNORE; }
        public final boolean isIncluded() { return getState() == STATE_INCLUDE; }
        public final boolean isExcluded() { return getState() == STATE_EXCLUDE; }
    }

    public abstract static class Group<V> extends Filter<List<? extends V>> {
        public Group(String name, List<? extends V> state) { super(name, List.copyOf(state)); }
    }

    public abstract static class Sort extends Filter<Sort.Selection> {
        private final String[] values;
        public Sort(String name, String[] values, Selection state) {
            super(name, state);
            this.values = Objects.requireNonNull(values, "values").clone();
        }
        public Sort(
                String name,
                String[] values,
                Selection state,
                int mask,
                kotlin.jvm.internal.DefaultConstructorMarker marker) {
            this(name, values, (mask & 4) == 0 ? state : null);
        }
        public final String[] getValues() { return values.clone(); }

        public static final class Selection {
            private final int index;
            private final boolean ascending;
            public Selection(int index, boolean ascending) {
                this.index = index;
                this.ascending = ascending;
            }
            public int getIndex() { return index; }
            public boolean getAscending() { return ascending; }
            public int component1() { return index; }
            public boolean component2() { return ascending; }
            public Selection copy(int newIndex, boolean newAscending) {
                return new Selection(newIndex, newAscending);
            }
            @Override public boolean equals(Object value) {
                return value instanceof Selection other
                        && index == other.index && ascending == other.ascending;
            }
            @Override public int hashCode() { return Objects.hash(index, ascending); }
            @Override public String toString() {
                return "Selection(index=" + index + ", ascending=" + ascending + ')';
            }
        }
    }
}
