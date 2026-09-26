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

package org.idp.server.core.openid.token.verifier;

import org.idp.server.core.openid.clientinstance.ClientInstance;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;

/**
 * A Client Instance bound to a user receives that user's tokens only.
 *
 * <p>With {@code client_instance_registration_policy = user_bound} an instance was registered by a
 * login of one user, and stands for that user's install of the application. The instances of one
 * application share a {@code client_id}, so client authentication alone does not stop the instance
 * of one user from redeeming what was granted to another: an authorization code, a CIBA {@code
 * auth_req_id}, a refresh token.
 *
 * <p>A grant without a user ({@code client_credentials}) and an instance without one (registered
 * through the management API) have nothing to compare, and pass.
 */
public class ClientInstanceUserBindingVerifier {

  AuthorizationGrant authorizationGrant;
  ClientInstance clientInstance;

  public ClientInstanceUserBindingVerifier(
      AuthorizationGrant authorizationGrant, ClientInstance clientInstance) {
    this.authorizationGrant = authorizationGrant;
    this.clientInstance = clientInstance;
  }

  public void verify() {
    if (!clientInstance.hasUserId() || !authorizationGrant.hasUser()) {
      return;
    }
    if (!clientInstance.userId().equals(authorizationGrant.user().sub())) {
      throw new TokenBadRequestException(
          "invalid_grant",
          "the grant belongs to a user other than the one of this client instance");
    }
  }
}
