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

package org.idp.server.control_plane.management.identity.user.io;

import java.util.Map;
import org.idp.server.account_linking.LinkedExternalAccountIdentifier;
import org.idp.server.control_plane.management.identity.user.handler.UserManagementRequest;

/** Names one linked external account by its identifier, whoever's it is. */
public class LinkedExternalAccountByIdRequest implements UserManagementRequest {

  LinkedExternalAccountIdentifier identifier;

  public LinkedExternalAccountByIdRequest(LinkedExternalAccountIdentifier identifier) {
    this.identifier = identifier;
  }

  public LinkedExternalAccountIdentifier identifier() {
    return identifier;
  }

  /** Whether the identifier is a UUID. Anything else names no link. */
  public boolean isWellFormed() {
    try {
      java.util.UUID.fromString(identifier.value());
      return true;
    } catch (IllegalArgumentException | NullPointerException exception) {
      return false;
    }
  }

  @Override
  public Map<String, Object> toMap() {
    return Map.of("id", identifier.value());
  }
}
