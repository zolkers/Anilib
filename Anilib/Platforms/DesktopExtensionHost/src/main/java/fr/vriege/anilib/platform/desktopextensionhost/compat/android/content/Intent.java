package fr.vriege.anilib.platform.desktopextensionhost.compat.android.content;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.net.Uri;
import java.util.HashMap;
import java.util.Map;

public class Intent {
    private final Map<String, Object> extras = new HashMap<>();
    private String action;
    private Uri data;

    public Intent() {
    }

    public Intent(String action) {
        this.action = action;
    }

    public Intent setAction(String action) {
        this.action = action;
        return this;
    }

    public String getAction() {
        return action;
    }

    public Intent setData(Uri data) {
        this.data = data;
        return this;
    }

    public Uri getData() {
        return data;
    }

    public Intent putExtra(String name, String value) {
        extras.put(name, value);
        return this;
    }

    public String getStringExtra(String name) {
        Object value = extras.get(name);
        return value instanceof String text ? text : null;
    }
}
