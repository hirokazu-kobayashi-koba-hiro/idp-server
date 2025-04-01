package org.idp.server.core.hook;

import java.util.Objects;

public class HookType {
  String name;

  public HookType() {}

  public HookType(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    HookType hookType = (HookType) o;
    return Objects.equals(name, hookType.name);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name);
  }
}
