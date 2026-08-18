package fr.vriege.anilib.feature.applicationupdate.ui;

import fr.vriege.anilib.feature.applicationupdate.ApplicationUpdateSnapshot;

public interface ApplicationUpdatePresentation {
    ApplicationUpdateSnapshot snapshot();

    ApplicationUpdateSnapshot checkNow();
}
