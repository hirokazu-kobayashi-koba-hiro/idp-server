/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
