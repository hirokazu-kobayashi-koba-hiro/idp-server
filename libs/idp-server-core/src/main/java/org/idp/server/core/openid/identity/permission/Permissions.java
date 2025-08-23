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

package org.idp.server.core.openid.identity.permission;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class Permissions implements Iterable<Permission> {

  List<Permission> values;

  public Permissions() {
    this.values = new ArrayList<>();
  }

  public Permissions(List<Permission> values) {
    this.values = values;
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }

  public boolean contains(Permission permission) {
    return values.stream().anyMatch(value -> value.match((permission)));
  }

  @Override
  public Iterator<Permission> iterator() {
    return values.iterator();
  }

  public Permissions filter(List<String> permissionNames) {
    return new Permissions(
        values.stream().filter(permission -> permissionNames.contains(permission.name())).toList());
  }

  public List<Permission> toList() {
    return values;
  }

  public Permissions filterNoneMatch(List<String> permissionNames) {

    return new Permissions(
        values.stream()
            .filter(permission -> !permissionNames.contains(permission.name()))
            .toList());
  }

  public String permissionNamesAsString() {
    return values.stream().map(Permission::name).collect(Collectors.joining(" "));
  }

  public int size() {
    return values.size();
  }
}
