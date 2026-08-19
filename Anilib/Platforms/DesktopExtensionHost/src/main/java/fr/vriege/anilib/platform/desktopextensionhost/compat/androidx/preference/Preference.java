package fr.vriege.anilib.platform.desktopextensionhost.compat.androidx.preference;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.content.Context;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.content.SharedPreferences;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.util.AttributeSet;

public class Preference {
    private final Context context;
    private String key;
    private CharSequence title;
    private CharSequence summary;
    private boolean visible = true;
    private boolean enabled = true;
    private boolean selectable = true;
    private boolean persistent;
    private int order;
    private Object defaultValue;
    private Object summaryProvider;
    private boolean iconSpaceReserved = true;
    private boolean singleLineTitle = true;
    private boolean recycleEnabled = true;
    private OnPreferenceChangeListener changeListener;
    private OnPreferenceClickListener clickListener;

    public Preference(Context context, AttributeSet attributes) { this.context = context; }
    public Preference(Context context) { this(context, null); }
    public Context getContext() { return context; }
    public String getKey() { return key; }
    public void setKey(String value) { key = value; }
    public CharSequence getTitle() { return title; }
    public void setTitle(CharSequence value) { title = value; }
    public CharSequence getSummary() { return summary; }
    public void setSummary(CharSequence value) { summary = value; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean value) { visible = value; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public boolean isSelectable() { return selectable; }
    public void setSelectable(boolean value) { selectable = value; }
    public boolean isPersistent() { return persistent; }
    public void setPersistent(boolean value) { persistent = value; }
    public int getOrder() { return order; }
    public void setOrder(int value) { order = value; }
    public Object getDefaultValue() { return defaultValue; }
    public void setDefaultValue(Object value) { defaultValue = value; }
    public Object getSummaryProvider() { return summaryProvider; }
    public void setSummaryProvider(Object value) { summaryProvider = value; }
    public boolean isIconSpaceReserved() { return iconSpaceReserved; }
    public void setIconSpaceReserved(boolean value) { iconSpaceReserved = value; }
    public boolean isSingleLineTitle() { return singleLineTitle; }
    public void setSingleLineTitle(boolean value) { singleLineTitle = value; }
    public boolean isRecycleEnabled() { return recycleEnabled; }
    public void setRecycleEnabled(boolean value) { recycleEnabled = value; }
    public OnPreferenceChangeListener getOnPreferenceChangeListener() { return changeListener; }
    public void setOnPreferenceChangeListener(OnPreferenceChangeListener value) { changeListener = value; }
    public OnPreferenceClickListener getOnPreferenceClickListener() { return clickListener; }
    public void setOnPreferenceClickListener(OnPreferenceClickListener value) { clickListener = value; }
    public void setTitle(int resourceId) { }
    public void setSummary(int resourceId) { }
    public void setLayoutResource(int resourceId) { }
    public void setWidgetLayoutResource(int resourceId) { }
    public void setDependency(String dependencyKey) { }
    public void setIcon(int resourceId) { }
    public SharedPreferences getSharedPreferences() { return null; }

    public interface OnPreferenceChangeListener {
        boolean onPreferenceChange(Preference preference, Object value);
    }

    public interface OnPreferenceClickListener {
        boolean onPreferenceClick(Preference preference);
    }
}
