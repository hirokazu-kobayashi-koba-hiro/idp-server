package org.idp.server.core.identity.event;

import java.util.Objects;

public class UserLifecycleEventResultIdentifier {

  String value;

  public UserLifecycleEventResultIdentifier() {}

  public UserLifecycleEventResultIdentifier(String value) {
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
    UserLifecycleEventResultIdentifier that = (UserLifecycleEventResultIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
