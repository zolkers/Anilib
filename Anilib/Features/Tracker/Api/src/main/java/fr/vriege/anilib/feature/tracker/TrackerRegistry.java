package fr.vriege.anilib.feature.tracker;

import java.util.List;
import java.util.Optional;

public interface TrackerRegistry {
    List<Tracker> trackers();

    List<InstalledTrackerExtension> extensions();

    Optional<Tracker> find(TrackerId id);
}
