package fr.vriege.anilib.platform.desktopextensionhost.extension;

import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.AnimeFilter;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model.Filter;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class ExtensionSourceFilterCodec {
    private ExtensionSourceFilterCodec() {
    }

    static FilterSet from(Object abiValue) {
        if (!(abiValue instanceof List<?> filters)) {
            throw new IllegalStateException("Extension filter list is not a list");
        }
        List<Definition> definitions = new ArrayList<>();
        Map<String, Binding> bindings = new LinkedHashMap<>();
        append(filters, "", "filter", definitions, bindings);
        return new FilterSet(abiValue, List.copyOf(definitions), Map.copyOf(bindings));
    }

    private static void append(
            List<?> filters,
            String groupId,
            String prefix,
            List<Definition> definitions,
            Map<String, Binding> bindings) {
        for (int index = 0; index < filters.size(); index++) {
            Object filter = Objects.requireNonNull(filters.get(index), "filter");
            String id = prefix + "." + index;
            String kind = filterKind(filter);
            String label = nullableText(ExtensionOperationDispatcher.invokeAny(filter, "getName"));
            if (kind.equals("Group")) {
                definitions.add(new Definition(id, label, Type.HEADER, List.of(), "", groupId));
                Object children = ExtensionOperationDispatcher.invokeAny(filter, "getState");
                if (!(children instanceof List<?> childFilters)) {
                    throw new IllegalStateException("Extension filter group is not a list");
                }
                append(childFilters, id, id, definitions, bindings);
                continue;
            }
            definition(id, label, groupId, kind, filter).ifPresent(projected -> {
                definitions.add(projected.definition());
                projected.binding().ifPresent(binding -> bindings.put(id, binding));
            });
        }
    }

    private static Optional<Projected> definition(
            String id,
            String label,
            String groupId,
            String kind,
            Object filter) {
        return switch (kind) {
            case "Header" -> Optional.of(stateless(id, label, Type.HEADER, groupId));
            case "Separator" -> Optional.of(stateless(id, label, Type.SEPARATOR, groupId));
            case "Text" -> Optional.of(stateful(
                    id, label, Type.TEXT, List.of(),
                    nullableText(ExtensionOperationDispatcher.invokeAny(filter, "getState")),
                    groupId, filter, List.of()));
            case "CheckBox" -> Optional.of(stateful(
                    id, label, Type.CHECKBOX, List.of(),
                    Boolean.toString(booleanValue(ExtensionOperationDispatcher.invokeAny(filter, "getState"))),
                    groupId, filter, List.of()));
            case "TriState" -> Optional.of(stateful(
                    id, label, Type.TRI_STATE, List.of(),
                    triState(number(ExtensionOperationDispatcher.invokeAny(filter, "getState")).intValue()),
                    groupId, filter, List.of()));
            case "Select" -> Optional.of(select(id, label, groupId, filter));
            case "Sort" -> Optional.of(sort(id, label, groupId, filter));
            default -> Optional.empty();
        };
    }

    private static Projected select(String id, String label, String groupId, Object filter) {
        List<String> options = uniqueLabels(arrayValues(ExtensionOperationDispatcher.invokeAny(filter, "getValues")));
        int selected = boundedIndex(
                number(ExtensionOperationDispatcher.invokeAny(filter, "getState")).intValue(), options.size());
        return stateful(id, label, Type.SELECT, options, options.get(selected), groupId, filter, List.of());
    }

    private static Projected sort(String id, String label, String groupId, Object filter) {
        List<String> labels = uniqueLabels(arrayValues(ExtensionOperationDispatcher.invokeAny(filter, "getValues")));
        List<SortSelection> selections = new ArrayList<>(labels.size() * 2);
        for (int index = 0; index < labels.size(); index++) {
            selections.add(new SortSelection(index, true, "↑ " + labels.get(index)));
            selections.add(new SortSelection(index, false, "↓ " + labels.get(index)));
        }
        Object state = ExtensionOperationDispatcher.invokeAny(filter, "getState");
        int selectedIndex = 0;
        if (state != null) {
            int index = boundedIndex(
                    number(ExtensionOperationDispatcher.invokeAny(state, "getIndex")).intValue(), labels.size());
            boolean ascending = booleanValue(ExtensionOperationDispatcher.invokeAny(state, "getAscending"));
            selectedIndex = index * 2 + (ascending ? 0 : 1);
        }
        List<String> options = selections.stream().map(SortSelection::value).toList();
        return stateful(
                id, label, Type.SORT, options, options.get(selectedIndex), groupId, filter, selections);
    }

    private static Projected stateless(String id, String label, Type type, String groupId) {
        return new Projected(new Definition(id, label, type, List.of(), "", groupId), Optional.empty());
    }

    private static Projected stateful(
            String id,
            String label,
            Type type,
            List<String> options,
            String defaultValue,
            String groupId,
            Object filter,
            List<SortSelection> sortSelections) {
        return new Projected(
                new Definition(id, label, type, options, defaultValue, groupId),
                Optional.of(new Binding(filter, type, options, sortSelections)));
    }

    private static String filterKind(Object filter) {
        for (Class<?> current = filter.getClass(); current != null; current = current.getSuperclass()) {
            String name = current.getName();
            for (String marker : List.of("AnimeFilter$", "Filter$")) {
                int position = name.indexOf(marker);
                if (position >= 0) {
                    String nested = name.substring(position + marker.length());
                    int separator = nested.indexOf('$');
                    return separator < 0 ? nested : nested.substring(0, separator);
                }
            }
        }
        return "Unknown";
    }

    private static List<String> arrayValues(Object values) {
        if (values == null || !values.getClass().isArray()) {
            throw new IllegalStateException("Extension option filter values are not an array");
        }
        List<String> result = new ArrayList<>(Array.getLength(values));
        for (int index = 0; index < Array.getLength(values); index++) {
            result.add(String.valueOf(Array.get(values, index)));
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("Extension option filter has no values");
        }
        return List.copyOf(result);
    }

    private static List<String> uniqueLabels(List<String> labels) {
        Map<String, Integer> occurrences = new LinkedHashMap<>();
        List<String> result = new ArrayList<>(labels.size());
        for (String label : labels) {
            int occurrence = occurrences.merge(label, 1, Integer::sum);
            result.add(occurrence == 1 ? label : label + " (" + occurrence + ")");
        }
        return List.copyOf(result);
    }

    private static int boundedIndex(int value, int size) {
        return value >= 0 && value < size ? value : 0;
    }

    private static String triState(int value) {
        return switch (value) {
            case 1 -> "include";
            case 2 -> "exclude";
            default -> "ignore";
        };
    }

    private static Number number(Object value) {
        if (value instanceof Number result) {
            return result;
        }
        throw new IllegalStateException("Extension filter state is not numeric");
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean result) {
            return result;
        }
        throw new IllegalStateException("Extension filter state is not boolean");
    }

    private static String nullableText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    enum Type {
        HEADER,
        SEPARATOR,
        TEXT,
        CHECKBOX,
        TRI_STATE,
        SELECT,
        SORT
    }

    record Definition(
            String id,
            String label,
            Type type,
            List<String> options,
            String defaultValue,
            String groupId) {
        Definition {
            options = List.copyOf(options);
        }
    }

    record FilterSet(Object abiValue, List<Definition> definitions, Map<String, Binding> bindings) {
        FilterSet {
            definitions = List.copyOf(definitions);
            bindings = Map.copyOf(bindings);
        }

        void apply(Map<String, String> values) {
            values.forEach((id, value) -> {
                Binding binding = bindings.get(id);
                if (binding == null) {
                    throw new IllegalArgumentException("Unknown extension source filter: " + id);
                }
                binding.apply(value);
            });
        }
    }

    private record Projected(Definition definition, Optional<Binding> binding) {
    }

    private record SortSelection(int index, boolean ascending, String value) {
    }

    private record Binding(
            Object filter,
            Type type,
            List<String> options,
            List<SortSelection> sortSelections) {
        private void apply(String value) {
            Object state = switch (type) {
                case TEXT -> value;
                case CHECKBOX -> Boolean.valueOf(value);
                case TRI_STATE -> switch (value) {
                    case "include" -> 1;
                    case "exclude" -> 2;
                    default -> 0;
                };
                case SELECT -> optionIndex(value);
                case SORT -> sortSelection(value);
                case HEADER, SEPARATOR -> throw new IllegalStateException("Stateless filter has a binding");
            };
            ExtensionOperationDispatcher.invokeAny(filter, "setState", state);
        }

        private int optionIndex(String value) {
            int index = options.indexOf(value);
            if (index < 0) {
                throw new IllegalArgumentException("Unknown extension filter option: " + value);
            }
            return index;
        }

        private Object sortSelection(String value) {
            SortSelection selection = sortSelections.stream()
                    .filter(candidate -> candidate.value().equals(value))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown extension sort option: " + value));
            if (filter instanceof AnimeFilter.Sort) {
                return new AnimeFilter.Sort.Selection(selection.index(), selection.ascending());
            }
            if (filter instanceof Filter.Sort) {
                return new Filter.Sort.Selection(selection.index(), selection.ascending());
            }
            throw new IllegalStateException("Extension sort filter ABI is unavailable");
        }
    }
}
