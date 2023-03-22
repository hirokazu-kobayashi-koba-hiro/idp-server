package org.idp.server.core.type;

import java.util.Objects;

/**
 * error
 */
public class Error {
    String value;

    public Error() {}

    public Error(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Error that = (Error) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
