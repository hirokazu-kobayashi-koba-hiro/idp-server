package org.idp.server.core.sharedsignal;

import java.util.Objects;

public class EventIdentifier {
    String value;

    public EventIdentifier() {}

    public EventIdentifier(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public boolean exists() {
        return Objects.nonNull(value) && !value.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        EventIdentifier that = (EventIdentifier) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
