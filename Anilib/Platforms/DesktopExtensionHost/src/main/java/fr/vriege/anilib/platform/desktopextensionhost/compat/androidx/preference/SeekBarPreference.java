package fr.vriege.anilib.platform.desktopextensionhost.compat.androidx.preference;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.content.Context;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.util.AttributeSet;

public final class SeekBarPreference extends Preference {
    private int value;
    private int max = 100;
    private int min;
    private boolean showSeekBarValue = true;
    public SeekBarPreference(Context context, AttributeSet attributes) { super(context, attributes); }
    public SeekBarPreference(Context context) { super(context); }
    public int getValue() { return value; }
    public void setValue(int newValue) { value = newValue; }
    public int getMax() { return max; }
    public void setMax(int newMax) { max = newMax; }
    public int getMin() { return min; }
    public void setMin(int newMin) { min = newMin; }
    public boolean getShowSeekBarValue() { return showSeekBarValue; }
    public void setShowSeekBarValue(boolean show) { showSeekBarValue = show; }
}
