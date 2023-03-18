package org.idp.server.core.oauth;

import java.util.Objects;

/** OAuthRequestIdentifier */
public class OAuthRequestIdentifier {
  String value;

  public OAuthRequestIdentifier() {}

  public OAuthRequestIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    OAuthRequestIdentifier that = (OAuthRequestIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
