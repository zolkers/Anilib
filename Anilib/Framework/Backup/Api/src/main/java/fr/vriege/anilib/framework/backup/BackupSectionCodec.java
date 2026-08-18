package fr.vriege.anilib.framework.backup;

public interface BackupSectionCodec {
    BackupSectionId sectionId();

    String displayName();

    int currentVersion();

    BackupSectionData exportSection();

    BackupSectionDetails inspect(int version, byte[] payload);

    PreparedBackupRestore prepareRestore(int version, byte[] payload);
}
