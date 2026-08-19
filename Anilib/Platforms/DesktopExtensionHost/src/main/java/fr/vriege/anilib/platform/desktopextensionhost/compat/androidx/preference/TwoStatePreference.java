package fr.vriege.anilib.platform.desktopextensionhost.compat.androidx.preference;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.content.Context;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.util.AttributeSet;

public class TwoStatePreference extends Preference {
    private boolean checked;
    private CharSequence summaryOn;
    private CharSequence summaryOff;
    public TwoStatePreference(Context context, AttributeSet attributes) { super(context, attributes); }
    public TwoStatePreference(Context context) { super(context); }
    public boolean isChecked() { return checked; }
    public void setChecked(boolean value) { checked = value; }
    public CharSequence getSummaryOn() { return summaryOn; }
    public void setSummaryOn(CharSequence value) { summaryOn = value; }
    public CharSequence getSummaryOff() { return summaryOff; }
    public void setSummaryOff(CharSequence value) { summaryOff = value; }
}
