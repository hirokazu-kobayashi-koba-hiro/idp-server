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

package org.idp.server.core.openid.identity.role;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Roles implements Iterable<Role> {

  List<Role> values;

  public Roles() {
    this.values = new ArrayList<>();
  }

  public Roles(List<Role> values) {
    this.values = values;
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }

  public boolean contains(Role role) {
    return values.stream().anyMatch(value -> value.match((role)));
  }

  public boolean containsByName(String name) {
    return values.stream().anyMatch(value -> value.name().equals(name));
  }

  public Role getByName(String name) {
    return values.stream().filter(value -> value.name().equals(name)).findFirst().orElse(null);
  }

  @Override
  public Iterator<Role> iterator() {
    return values.iterator();
  }

  public List<String> toStringList() {
    return values.stream().map(Role::name).toList();
  }

  public List<Role> toList() {
    return values;
  }
}
