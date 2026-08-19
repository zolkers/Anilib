package fr.vriege.anilib.platform.desktopextensionhost.compat.android.app;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.content.ContextThemeWrapper;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.content.Intent;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.os.Bundle;

public class Activity extends ContextThemeWrapper {
    private Intent intent = new Intent();

    public Activity() {
    }

    protected void onCreate(Bundle state) {
    }

    public Intent getIntent() {
        return intent;
    }

    public void setIntent(Intent intent) {
        this.intent = intent == null ? new Intent() : intent;
    }

    public void startActivity(Intent intent) {
    }

    public void finish() {
    }
}
