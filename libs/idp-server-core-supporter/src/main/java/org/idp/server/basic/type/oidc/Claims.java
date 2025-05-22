/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.type.oidc;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Claims */
public class Claims {
  Set<String> values;

  public Claims() {
    this.values = new HashSet<>();
  }

  public Claims(Set<String> values) {
    this.values = values;
  }

  public Claims(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      this.values = new HashSet<>();
      return;
    }
    this.values = Arrays.stream(value.split(" ")).collect(Collectors.toSet());
  }

  public boolean exists() {
    return !values.isEmpty();
  }

  public String toStringValues() {
    return String.join(" ", values);
  }

  public boolean contains(String claim) {
    return values.contains(claim);
  }
}
