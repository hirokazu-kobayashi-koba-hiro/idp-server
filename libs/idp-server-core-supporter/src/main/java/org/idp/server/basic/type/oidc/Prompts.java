/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.type.oidc;

import java.util.*;
import java.util.stream.Collectors;

/**
 * prompt OPTIONAL. Space delimited,
 *
 * <p>case sensitive list of ASCII string values that specifies whether the Authorization Server
 * prompts the End-User for reauthentication and consent.
 */
public class Prompts implements Iterable<Prompt> {

  List<Prompt> values;

  public Prompts() {
    this.values = new ArrayList<>();
  }

  public Prompts(List<Prompt> values) {
    this.values = values;
  }

  @Override
  public Iterator<Prompt> iterator() {
    return values.iterator();
  }

  public static Prompts of(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      return new Prompts();
    }
    List<Prompt> values = Arrays.stream(value.split(" ")).map(Prompt::of).toList();
    return new Prompts(values);
  }

  public boolean hasNone() {
    return values.contains(Prompt.none);
  }

  public boolean hasUnknown() {
    return values.contains(Prompt.unknown);
  }

  public boolean isMultiValue() {
    return values.size() >= 2;
  }

  public String toStringValues() {
    if (values.isEmpty()) {
      return "";
    }
    Set<String> setValues = values.stream().map(Enum::name).collect(Collectors.toSet());
    return String.join(" ", setValues);
  }

  public boolean hasLogin() {
    return values.contains(Prompt.login);
  }

  public boolean exists() {
    return !values.isEmpty();
  }

  public boolean hasCreate() {
    return values.contains(Prompt.create);
  }
}
