package fr.vriege.anilib.tooling.javaquality;

import java.util.List;

public interface AnilibJavaRule {
    String name();

    List<Diagnostic> analyze(RepositorySnapshot repository);
}
