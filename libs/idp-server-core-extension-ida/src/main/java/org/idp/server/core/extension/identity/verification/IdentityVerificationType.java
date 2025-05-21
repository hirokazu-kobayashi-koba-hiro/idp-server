package org.idp.server.core.extension.identity.verification;

import java.util.Objects;

public class IdentityVerificationType {

  String name;

  public IdentityVerificationType() {}

  public IdentityVerificationType(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    IdentityVerificationType that = (IdentityVerificationType) o;
    return Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name);
  }

  public boolean exists() {
    return name != null && !name.isEmpty();
  }

  public boolean isContinuousCustomerDueDiligence() {
    return "continuous-customer-due-diligence".equals(name);
  }
}
