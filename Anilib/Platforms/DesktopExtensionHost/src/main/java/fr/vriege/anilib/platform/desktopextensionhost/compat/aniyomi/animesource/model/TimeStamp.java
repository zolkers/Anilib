package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model;

public record TimeStamp(double start, double end, String name, ChapterType type) {
    public TimeStamp(double start, double end, String name) {
        this(start, end, name, ChapterType.Other);
    }
    public double getStart() { return start; }
    public double getEnd() { return end; }
    public String getName() { return name; }
    public ChapterType getType() { return type; }
}
