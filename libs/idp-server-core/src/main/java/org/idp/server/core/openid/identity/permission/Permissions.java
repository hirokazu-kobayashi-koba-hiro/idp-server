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

  /**
   * Checks if the collection contains the given permission, with backward compatibility.
   *
   * @param permission the permission to check
   * @return true if contained
   */
  public boolean contains(Permission permission) {
    String normalizedTarget = PermissionMatcher.normalize(permission.name());
    return values.stream()
        .anyMatch(value -> PermissionMatcher.normalize(value.name()).equals(normalizedTarget));
  }

  @Override
  public Iterator<Permission> iterator() {
    return values.iterator();
  }

  /**
   * Filters permissions by name with backward compatibility.
   *
   * <p>Uses normalized comparison to support both legacy format (organization:create) and new
   * format (idp:organization:create).
   *
   * @param permissionNames list of permission names to filter by
   * @return filtered permissions
   */
  public Permissions filterByName(List<String> permissionNames) {
    List<String> normalizedNames =
        permissionNames.stream().map(PermissionMatcher::normalize).toList();
    return new Permissions(
        values.stream()
            .filter(
                permission ->
                    normalizedNames.contains(PermissionMatcher.normalize(permission.name())))
            .toList());
  }

  public Permissions filterById(List<String> permissionIds) {
    return new Permissions(
        values.stream().filter(permission -> permissionIds.contains(permission.id())).toList());
  }

  public List<Permission> toList() {
    return values;
  }

  /**
   * Filters permissions that do NOT match the given names, with backward compatibility.
   *
   * @param permissionNames list of permission names to exclude
   * @return filtered permissions
   */
  public Permissions filterNoneMatch(List<String> permissionNames) {
    List<String> normalizedNames =
        permissionNames.stream().map(PermissionMatcher::normalize).toList();
    return new Permissions(
        values.stream()
            .filter(
                permission ->
                    !normalizedNames.contains(PermissionMatcher.normalize(permission.name())))
            .toList());
  }

  public Permissions filterNoneMatchById(List<String> permissionIds) {
    return new Permissions(
        values.stream().filter(permission -> !permissionIds.contains(permission.id())).toList());
  }

  public String permissionNamesAsString() {
    return values.stream().map(Permission::name).collect(Collectors.joining(" "));
  }

  public String permissionIdsAsString() {
    return values.stream().map(Permission::id).collect(Collectors.joining(" "));
  }

  public int size() {
    return values.size();
  }
}
