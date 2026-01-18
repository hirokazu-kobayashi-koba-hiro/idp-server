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

/**
 * Thrown when attempting to create a permission with a reserved namespace.
 *
 * <p>Reserved namespaces like {@code idp:} are controlled by the system and cannot be used for
 * custom permissions.
 */
public class ReservedNamespaceException extends RuntimeException {

  private final String permissionName;
  private final String namespace;

  public ReservedNamespaceException(String permissionName, String namespace) {
    super(
        String.format(
            "Permission name '%s' uses reserved namespace '%s'. "
                + "Custom permissions cannot use reserved namespaces.",
            permissionName, namespace));
    this.permissionName = permissionName;
    this.namespace = namespace;
  }

  public String permissionName() {
    return permissionName;
  }

  public String namespace() {
    return namespace;
  }
}
