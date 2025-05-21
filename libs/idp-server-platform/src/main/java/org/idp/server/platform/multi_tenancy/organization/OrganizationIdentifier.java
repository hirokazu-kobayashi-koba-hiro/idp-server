package org.idp.server.platform.multi_tenancy.organization;

import java.util.Objects;

/** OrganizationIdentifier is organization identity. */
public class OrganizationIdentifier {

  String value;

  public OrganizationIdentifier() {}

  public OrganizationIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    OrganizationIdentifier that = (OrganizationIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
