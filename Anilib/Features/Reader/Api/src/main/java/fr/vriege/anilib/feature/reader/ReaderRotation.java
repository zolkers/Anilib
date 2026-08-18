package fr.vriege.anilib.feature.reader;

public enum ReaderRotation {
    NONE(0),
    CLOCKWISE_90(90),
    UPSIDE_DOWN(180),
    COUNTERCLOCKWISE_90(270);

    private final int degrees;

    ReaderRotation(int degrees) {
        this.degrees = degrees;
    }

    public int degrees() {
        return degrees;
    }
}
