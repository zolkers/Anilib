package fr.vriege.anilib.platform.desktopextensionhost.compat.androidx.preference;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.content.Context;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.util.AttributeSet;

public final class SwitchPreference extends TwoStatePreference {
    public SwitchPreference(Context context, AttributeSet attributes) { super(context, attributes); }
    public SwitchPreference(Context context) { super(context); }
}
