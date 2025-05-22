/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

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
