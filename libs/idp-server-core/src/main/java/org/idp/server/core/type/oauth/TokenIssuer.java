package org.idp.server.core.type.oauth;

import java.net.URI;
import java.util.Objects;

/** TokenIssuer */
public class TokenIssuer {
  String value;

  public TokenIssuer() {}

  public TokenIssuer(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TokenIssuer that = (TokenIssuer) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  public String extractDomain() {
    return URI.create(value).getHost();
  }
}
