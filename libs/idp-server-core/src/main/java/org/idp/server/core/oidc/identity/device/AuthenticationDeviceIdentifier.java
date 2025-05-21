package org.idp.server.core.oidc.identity.device;

import java.util.Objects;
import java.util.UUID;

public class AuthenticationDeviceIdentifier {
  String value;

  public static AuthenticationDeviceIdentifier generate() {
    return new AuthenticationDeviceIdentifier(UUID.randomUUID().toString());
  }

  public AuthenticationDeviceIdentifier() {}

  public AuthenticationDeviceIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    AuthenticationDeviceIdentifier that = (AuthenticationDeviceIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }

  public boolean exists() {
    return value != null && !value.isEmpty();
  }
}
