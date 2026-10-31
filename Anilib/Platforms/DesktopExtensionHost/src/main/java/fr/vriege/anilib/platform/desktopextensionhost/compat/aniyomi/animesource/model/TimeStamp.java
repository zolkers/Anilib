package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model;

import kotlin.jvm.internal.DefaultConstructorMarker;

public record TimeStamp(double start, double end, String name, ChapterType type) {
    public TimeStamp(
            double start,
            double end,
            String name,
            ChapterType type,
            int mask,
            DefaultConstructorMarker marker) {
        this(start, end, (mask & 4) != 0 ? "" : name, (mask & 8) != 0 ? ChapterType.Other : type);
    }

    public TimeStamp(double start, double end, String name) {
        this(start, end, name, ChapterType.Other);
    }

    public double getStart() {
        return start;
    }

    public double getEnd() {
        return end;
    }

    public String getName() {
        return name;
    }

    public ChapterType getType() {
        return type;
    }

    public double component1() {
        return start;
    }

    public double component2() {
        return end;
    }

    public String component3() {
        return name;
    }

    public ChapterType component4() {
        return type;
    }

    public TimeStamp copy(double start, double end, String name, ChapterType type) {
        return new TimeStamp(start, end, name, type);
    }

    public static TimeStamp copy$default(
            TimeStamp source,
            double start,
            double end,
            String name,
            ChapterType type,
            int mask,
            Object marker) {
        return source.copy(
                (mask & 1) == 0 ? start : source.start,
                (mask & 2) == 0 ? end : source.end,
                (mask & 4) == 0 ? name : source.name,
                (mask & 8) == 0 ? type : source.type);
    }
}
