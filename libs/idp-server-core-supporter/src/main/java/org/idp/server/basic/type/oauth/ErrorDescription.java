package org.idp.server.basic.type.oauth;

import java.util.Objects;

/**
 * error_description OPTIONAL.
 *
 * <p>Human-readable ASCII [USASCII] text providing additional information, used to assist the
 * client developer in understanding the error that occurred. Values for the "error_description"
 * parameter MUST NOT include characters outside the set %x20-21 / %x23-5B / %x5D-7E.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.1.2.1">4.1.2.1. Error Response</a>
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
