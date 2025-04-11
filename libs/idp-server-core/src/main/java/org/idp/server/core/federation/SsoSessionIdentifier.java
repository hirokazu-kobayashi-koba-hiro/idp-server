package org.idp.server.core.federation;

import java.util.Objects;

public class SsoSessionIdentifier {

    String value;

    public SsoSessionIdentifier() {}

    public SsoSessionIdentifier(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SsoSessionIdentifier that = (SsoSessionIdentifier) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    public boolean exists() {
        return value != null && !value.isEmpty();
    }
}
