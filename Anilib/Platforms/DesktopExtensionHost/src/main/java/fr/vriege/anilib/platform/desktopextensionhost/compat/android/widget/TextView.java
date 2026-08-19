package fr.vriege.anilib.platform.desktopextensionhost.compat.android.widget;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.text.TextWatcher;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.view.View;
import java.util.ArrayList;
import java.util.List;

public class TextView extends View {
    private final List<TextWatcher> watchers = new ArrayList<>();
    private CharSequence text = "";
    private CharSequence error;
    private int inputType;

    public TextView() {
    }

    public CharSequence getText() {
        return text;
    }

    public void setText(CharSequence text) {
        this.text = text == null ? "" : text;
    }

    public void setError(CharSequence error) {
        this.error = error;
    }

    public CharSequence getError() {
        return error;
    }

    public void setInputType(int inputType) {
        this.inputType = inputType;
    }

    public int getInputType() {
        return inputType;
    }

    public void addTextChangedListener(TextWatcher watcher) {
        if (watcher != null) {
            watchers.add(watcher);
        }
    }
}
