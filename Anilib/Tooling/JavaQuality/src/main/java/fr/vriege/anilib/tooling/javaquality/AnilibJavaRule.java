package fr.vriege.anilib.tooling.javaquality;

import java.util.List;

/** One focused deterministic repository rule. */
public interface AnilibJavaRule {
    String name();

    List<Diagnostic> analyze(RepositorySnapshot repository);
}
