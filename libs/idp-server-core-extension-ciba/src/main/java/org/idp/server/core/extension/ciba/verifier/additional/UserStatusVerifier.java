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

package org.idp.server.core.extension.ciba.verifier.additional;

import org.idp.server.core.extension.ciba.CibaRequestContext;
import org.idp.server.core.extension.ciba.exception.BackchannelAuthenticationForbiddenException;
import org.idp.server.core.openid.identity.User;

/**
 * Verifies that the user has an active status before proceeding with CIBA authentication.
 *
 * <p>This verifier checks whether the user's status allows authentication. Only users with active
 * statuses (INITIALIZED, FEDERATED, REGISTERED, IDENTITY_VERIFIED, or
 * IDENTITY_VERIFICATION_REQUIRED) are permitted to authenticate via CIBA.
 *
 * <p>Users with inactive statuses (LOCKED, DISABLED, SUSPENDED, DEACTIVATED, DELETED_PENDING, or
 * DELETED) will be rejected with an "unauthorized_client" error.
 *
 * @see org.idp.server.core.openid.identity.UserStatus#isActive()
 */
public class UserStatusVerifier implements CibaRequestAdditionalVerifier {

  @Override
  public boolean shouldVerify(CibaRequestContext context, User user) {
    return true;
  }

  @Override
  public void verify(CibaRequestContext context, User user) {
    if (!user.isActive()) {
      throw new BackchannelAuthenticationForbiddenException(
          "access_denied",
          String.format(
              "User authentication is not permitted due to user status: %s. Only users with active statuses (INITIALIZED, FEDERATED, REGISTERED, IDENTITY_VERIFIED, IDENTITY_VERIFICATION_REQUIRED) can authenticate.",
              user.statusName()));
    }
  }
}
