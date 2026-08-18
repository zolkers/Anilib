package fr.vriege.anilib.feature.extensionrepository.runtime;

import fr.vriege.anilib.feature.source.SourceFilterDefinition;
import fr.vriege.anilib.feature.source.SourceFilterType;
import fr.vriege.anilib.feature.source.SourceFilterValue;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Projects Aniyomi filter objects without linking their ABI into the shared runtime. */
final class AniyomiAnimeFilterAdapter {
    private AniyomiAnimeFilterAdapter() {
    }

    static ReflectedFilters from(Object abiValue) {
        List<SourceFilterDefinition> definitions = new ArrayList<>();
        Map<String, FilterBinding> bindings = new LinkedHashMap<>();
        append(list(abiValue, "anime filter list"), "", "filter", definitions, bindings);
        return new ReflectedFilters(abiValue, List.copyOf(definitions), Map.copyOf(bindings));
    }

    private static void append(
            List<?> filters,
            String groupId,
            String prefix,
            List<SourceFilterDefinition> definitions,
            Map<String, FilterBinding> bindings) {
        for (int index = 0; index < filters.size(); index++) {
            Object filter = filters.get(index);
            String id = prefix + "." + index;
            String kind = filterKind(filter);
            String label = nullableText(invokeOptional(filter, "getName").orElse(""));
            if (kind.equals("Group")) {
                definitions.add(definition(id, label, SourceFilterType.HEADER, List.of(), "", groupId));
                Object children = invoke(filter, "getState");
                append(list(children, "anime filter group"), id, id, definitions, bindings);
                continue;
            }
            filterDefinition(id, label, groupId, kind, filter).ifPresent(projected -> {
                definitions.add(projected.definition());
                projected.binding().ifPresent(binding -> bindings.put(id, binding));
            });
        }
    }

    private static Optional<ProjectedFilter> filterDefinition(
            String id,
            String label,
            String groupId,
            String kind,
            Object filter) {
        return switch (kind) {
            case "Header" -> Optional.of(stateless(id, label, SourceFilterType.HEADER, groupId));
            case "Separator" -> Optional.of(stateless(id, label, SourceFilterType.SEPARATOR, groupId));
            case "Text" -> Optional.of(stateful(
                    id,
                    label,
                    SourceFilterType.TEXT,
                    List.of(),
                    nullableText(invoke(filter, "getState")),
                    groupId,
                    filter,
                    List.of()));
            case "CheckBox" -> Optional.of(stateful(
                    id,
                    label,
                    SourceFilterType.CHECKBOX,
                    List.of(),
                    String.valueOf(bool(invoke(filter, "getState"))),
                    groupId,
                    filter,
                    List.of()));
            case "TriState" -> Optional.of(stateful(
                    id,
                    label,
                    SourceFilterType.TRI_STATE,
                    List.of(),
                    triState(number(invoke(filter, "getState")).intValue()),
                    groupId,
                    filter,
                    List.of()));
            case "Select" -> Optional.of(select(id, label, groupId, filter));
            case "Sort" -> Optional.of(sort(id, label, groupId, filter));
            default -> Optional.empty();
        };
    }

    private static ProjectedFilter select(String id, String label, String groupId, Object filter) {
        List<String> options = uniqueLabels(arrayValues(invoke(filter, "getValues")));
        int selected = boundedIndex(number(invoke(filter, "getState")).intValue(), options.size());
        return stateful(
                id,
                label,
                SourceFilterType.SELECT,
                options,
                options.get(selected),
                groupId,
                filter,
                List.of());
    }

    private static ProjectedFilter sort(String id, String label, String groupId, Object filter) {
        List<String> labels = uniqueLabels(arrayValues(invoke(filter, "getValues")));
        List<SortSelection> selections = new ArrayList<>(labels.size() * 2);
        for (int index = 0; index < labels.size(); index++) {
            selections.add(new SortSelection(index, true, "↑ " + labels.get(index)));
            selections.add(new SortSelection(index, false, "↓ " + labels.get(index)));
        }
        Object state = invoke(filter, "getState");
        int selectedIndex = 0;
        if (state != null) {
            int index = boundedIndex(number(invoke(state, "getIndex")).intValue(), labels.size());
            boolean ascending = bool(invoke(state, "getAscending"));
            selectedIndex = index * 2 + (ascending ? 0 : 1);
        }
        List<String> options = selections.stream().map(SortSelection::value).toList();
        return stateful(
                id,
                label,
                SourceFilterType.SORT,
                options,
                options.get(selectedIndex),
                groupId,
                filter,
                selections);
    }

    private static ProjectedFilter stateless(
            String id,
            String label,
            SourceFilterType type,
            String groupId) {
        return new ProjectedFilter(definition(id, label, type, List.of(), "", groupId), Optional.empty());
    }

    private static ProjectedFilter stateful(
            String id,
            String label,
            SourceFilterType type,
            List<String> options,
            String defaultValue,
            String groupId,
            Object filter,
            List<SortSelection> sortSelections) {
        return new ProjectedFilter(
                definition(id, label, type, options, defaultValue, groupId),
                Optional.of(new FilterBinding(filter, type, options, sortSelections)));
    }

    private static SourceFilterDefinition definition(
            String id,
            String label,
            SourceFilterType type,
            List<String> options,
            String defaultValue,
            String groupId) {
        return new SourceFilterDefinition(id, label, type, options, defaultValue, groupId);
    }

    private static String filterKind(Object filter) {
        Class<?> current = filter.getClass();
        while (current != null && !current.equals(Object.class)) {
            String name = current.getName();
            int marker = name.indexOf("AnimeFilter$");
            if (marker >= 0) {
                String nested = name.substring(marker + "AnimeFilter$".length());
                int next = nested.indexOf('$');
                return next < 0 ? nested : nested.substring(0, next);
            }
            current = current.getSuperclass();
        }
        return "Unknown";
    }

    private static List<String> arrayValues(Object values) {
        if (values == null || !values.getClass().isArray()) {
            throw new IllegalStateException("Aniyomi filter values must be an array");
        }
        List<String> result = new ArrayList<>(Array.getLength(values));
        for (int index = 0; index < Array.getLength(values); index++) {
            result.add(String.valueOf(Array.get(values, index)));
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("Aniyomi option filter must not be empty");
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

    private static Object invoke(Object target, String methodName, Object... arguments) {
        return AniyomiAnimeSourceAdapter.invoke(target, methodName, arguments);
    }

    private static Optional<Object> invokeOptional(Object target, String methodName) {
        return AniyomiAnimeSourceAdapter.invokeOptional(target, methodName);
    }

    private static List<?> list(Object value, String label) {
        return AniyomiAnimeSourceAdapter.list(value, label);
    }

    private static Number number(Object value) {
        return AniyomiAnimeSourceAdapter.number(value);
    }

    private static boolean bool(Object value) {
        return AniyomiAnimeSourceAdapter.bool(value);
    }

    private static String nullableText(Object value) {
        return AniyomiAnimeSourceAdapter.nullableText(value);
    }

    static final class ReflectedFilters {
        private final Object abiValue;
        private final List<SourceFilterDefinition> definitions;
        private final Map<String, FilterBinding> bindings;

        private ReflectedFilters(
                Object abiValue,
                List<SourceFilterDefinition> definitions,
                Map<String, FilterBinding> bindings) {
            this.abiValue = abiValue;
            this.definitions = definitions;
            this.bindings = bindings;
        }

        Object abiValue() {
            return abiValue;
        }

        List<SourceFilterDefinition> definitions() {
            return definitions;
        }

        void apply(List<SourceFilterValue> values) {
            for (SourceFilterValue value : values) {
                FilterBinding binding = bindings.get(value.filterId());
                if (binding == null) {
                    throw new IllegalArgumentException("Unknown Aniyomi source filter: " + value.filterId());
                }
                binding.apply(value.value());
            }
        }
    }

    private record ProjectedFilter(
            SourceFilterDefinition definition,
            Optional<FilterBinding> binding) {
    }

    private record SortSelection(int index, boolean ascending, String value) {
    }

    private record FilterBinding(
            Object filter,
            SourceFilterType type,
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
            invoke(filter, "setState", state);
        }

        private int optionIndex(String value) {
            int index = options.indexOf(value);
            if (index < 0) {
                throw new IllegalArgumentException("Unknown Aniyomi filter option: " + value);
            }
            return index;
        }

        private Object sortSelection(String value) {
            SortSelection selection = sortSelections.stream()
                    .filter(candidate -> candidate.value().equals(value))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown Aniyomi sort option: " + value));
            try {
                Class<?> type = Class.forName(
                        "eu.kanade.tachiyomi.animesource.model.AnimeFilter$Sort$Selection",
                        false,
                        filter.getClass().getClassLoader());
                Constructor<?> constructor = type.getConstructor(int.class, boolean.class);
                return constructor.newInstance(selection.index(), selection.ascending());
            } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException
                     | IllegalAccessException exception) {
                throw new IllegalStateException("Aniyomi sort selection ABI is unavailable", exception);
            } catch (InvocationTargetException exception) {
                throw new IllegalStateException("Unable to create Aniyomi sort selection", exception.getCause());
            }
        }
    }
}
