package org.idp.server.basic.type.oauth;

import java.util.Objects;

/**
 * error
 *
 * <p>REQUIRED. A single ASCII [USASCII] error code from the following:
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.1.2.1">4.1.2.1. Error Response</a>
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
