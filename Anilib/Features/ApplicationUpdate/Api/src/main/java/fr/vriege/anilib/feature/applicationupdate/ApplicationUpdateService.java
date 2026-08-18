package fr.vriege.anilib.feature.applicationupdate;

public interface ApplicationUpdateService {
    ApplicationUpdateSnapshot snapshot();

    ApplicationUpdateSnapshot checkNow();
}
