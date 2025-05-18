package org.idp.server.core.multi_tenancy.tenant.invitation;

import java.util.Objects;

public class TenantInvitationIdentifier {
  String value;

  public TenantInvitationIdentifier() {}

  public TenantInvitationIdentifier(String value) {
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
    TenantInvitationIdentifier that = (TenantInvitationIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
