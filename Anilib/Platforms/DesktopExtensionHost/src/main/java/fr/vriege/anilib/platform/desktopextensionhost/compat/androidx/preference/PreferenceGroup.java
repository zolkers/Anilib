package fr.vriege.anilib.platform.desktopextensionhost.compat.androidx.preference;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.content.Context;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.util.AttributeSet;

import java.util.ArrayList;
import java.util.List;

public class PreferenceGroup extends Preference {
    private final List<Preference> children = new ArrayList<>();

    public PreferenceGroup(Context context, AttributeSet attributes) { super(context, attributes); }
    public PreferenceGroup(Context context) { super(context); }
    public boolean addPreference(Preference preference) { return children.add(preference); }
    public boolean removePreference(Preference preference) { return children.remove(preference); }
    public int getPreferenceCount() { return children.size(); }
    public Preference getPreference(int index) {
        return index >= 0 && index < children.size() ? children.get(index) : null;
    }
    public boolean isOnSameScreenAsChildren() { return false; }
    public void setInitialExpandedChildrenCount(int count) { }
    public void setOrderingAsAdded(boolean orderingAsAdded) { }
    public List<Preference> getPreferences() { return List.copyOf(children); }
}
