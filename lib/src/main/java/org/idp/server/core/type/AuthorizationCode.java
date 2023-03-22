package org.idp.server.core.type;

import java.util.Objects;

/**
 * AuthorizationCode
 *
 * <p>authorization grant
 */
public class AuthorizationCode {
    String value;

    public AuthorizationCode() {}

    public AuthorizationCode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthorizationCode that = (AuthorizationCode) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
