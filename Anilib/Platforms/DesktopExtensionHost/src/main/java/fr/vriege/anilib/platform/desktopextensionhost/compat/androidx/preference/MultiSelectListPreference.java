package fr.vriege.anilib.platform.desktopextensionhost.compat.androidx.preference;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.content.Context;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.util.AttributeSet;

import java.util.HashSet;
import java.util.Set;

public final class MultiSelectListPreference extends Preference {
    private CharSequence[] entries;
    private CharSequence[] entryValues;
    private Set<String> values = new HashSet<>();
    public MultiSelectListPreference(Context context, AttributeSet attributes) { super(context, attributes); }
    public MultiSelectListPreference(Context context) { super(context); }
    public CharSequence[] getEntries() { return entries == null ? null : entries.clone(); }
    public void setEntries(CharSequence[] value) { entries = value == null ? null : value.clone(); }
    public CharSequence[] getEntryValues() { return entryValues == null ? null : entryValues.clone(); }
    public void setEntryValues(CharSequence[] value) { entryValues = value == null ? null : value.clone(); }
    public Set<String> getValues() { return Set.copyOf(values); }
    public void setValues(Set<String> value) { values = new HashSet<>(value); }
}
