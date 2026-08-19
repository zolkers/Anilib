package fr.vriege.anilib.platform.desktopextensionhost.compat.androidx.preference;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.content.Context;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.util.AttributeSet;

public class ListPreference extends Preference {
    private CharSequence[] entries;
    private CharSequence[] entryValues;
    private String value;
    public ListPreference(Context context, AttributeSet attributes) { super(context, attributes); }
    public ListPreference(Context context) { super(context); }
    public CharSequence[] getEntries() { return entries == null ? null : entries.clone(); }
    public void setEntries(CharSequence[] values) { entries = values == null ? null : values.clone(); }
    public CharSequence[] getEntryValues() { return entryValues == null ? null : entryValues.clone(); }
    public void setEntryValues(CharSequence[] values) { entryValues = values == null ? null : values.clone(); }
    public String getValue() { return value; }
    public void setValue(String newValue) { value = newValue; }
    public int findIndexOfValue(String searchedValue) {
        if (searchedValue == null || entryValues == null) return -1;
        for (int index = entryValues.length - 1; index >= 0; index--) {
            if (searchedValue.contentEquals(entryValues[index])) return index;
        }
        return -1;
    }
}
