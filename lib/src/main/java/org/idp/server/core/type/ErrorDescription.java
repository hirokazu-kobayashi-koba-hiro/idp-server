package org.idp.server.core.type;

import java.util.Objects;

/**
 * ErrorDescription
 */
public class ErrorDescription {
    String value;

    public ErrorDescription() {}

    public ErrorDescription(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ErrorDescription that = (ErrorDescription) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
