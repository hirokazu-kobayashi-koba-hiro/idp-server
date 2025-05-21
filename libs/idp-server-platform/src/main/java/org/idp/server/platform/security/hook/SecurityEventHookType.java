package org.idp.server.platform.security.hook;

import java.util.Objects;

public class SecurityEventHookType {
  String name;

  public SecurityEventHookType() {}

  public SecurityEventHookType(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    SecurityEventHookType hookType = (SecurityEventHookType) o;
    return Objects.equals(name, hookType.name);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name);
  }
}
