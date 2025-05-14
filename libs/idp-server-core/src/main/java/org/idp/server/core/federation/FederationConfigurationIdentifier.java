package org.idp.server.core.federation;

import java.util.Objects;

public class FederationConfigurationIdentifier {

  String value;

  public FederationConfigurationIdentifier() {}

  public FederationConfigurationIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return value != null && !value.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    FederationConfigurationIdentifier that = (FederationConfigurationIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
