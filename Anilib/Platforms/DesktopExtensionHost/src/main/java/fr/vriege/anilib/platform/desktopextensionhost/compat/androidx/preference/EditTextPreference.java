package fr.vriege.anilib.platform.desktopextensionhost.compat.androidx.preference;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.content.Context;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.util.AttributeSet;

public final class EditTextPreference extends DialogPreference {
    private String text;
    private OnBindEditTextListener listener;
    public EditTextPreference(Context context, AttributeSet attributes) { super(context, attributes); }
    public EditTextPreference(Context context) { super(context); }
    public String getText() { return text; }
    public void setText(String value) { text = value; }
    public OnBindEditTextListener getOnBindEditTextListener() { return listener; }
    public void setOnBindEditTextListener(OnBindEditTextListener value) { listener = value; }

    public interface OnBindEditTextListener {
        void onBindEditText(Object editText);
    }
}
