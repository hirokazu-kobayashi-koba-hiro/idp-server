/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.federation;

import java.util.Objects;

public class FederationType {
  String name;

  public FederationType() {}

  public FederationType(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    FederationType federationType = (FederationType) o;
    return Objects.equals(name, federationType.name);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name);
  }
}
