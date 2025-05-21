package org.idp.server.platform.multi_tenancy.tenant;

import java.util.Objects;

public class TenantIdentifier {

  String value;

  public TenantIdentifier() {}

  public TenantIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    TenantIdentifier that = (TenantIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }
}
