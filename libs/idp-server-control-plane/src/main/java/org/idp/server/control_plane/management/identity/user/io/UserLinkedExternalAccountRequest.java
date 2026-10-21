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
import org.idp.server.account_linking.AccountAlias;
import org.idp.server.control_plane.management.identity.user.handler.UserManagementRequest;
import org.idp.server.core.openid.identity.UserIdentifier;

/** Names one linked external account of a user, for reading it or removing it. */
public class UserLinkedExternalAccountRequest implements UserManagementRequest {

  UserIdentifier userIdentifier;
  AccountAlias alias;

  public UserLinkedExternalAccountRequest(UserIdentifier userIdentifier, AccountAlias alias) {
    this.userIdentifier = userIdentifier;
    this.alias = alias;
  }

  public UserIdentifier userIdentifier() {
    return userIdentifier;
  }

  public AccountAlias alias() {
    return alias;
  }

  @Override
  public Map<String, Object> toMap() {
    return Map.of("user_id", userIdentifier.value(), "alias", alias.value());
  }
}
