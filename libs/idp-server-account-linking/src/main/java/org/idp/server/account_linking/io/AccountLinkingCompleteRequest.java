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

package org.idp.server.account_linking.io;

import org.idp.server.account_linking.AccountLinkingState;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/** Everything the complete phase works from. */
public class AccountLinkingCompleteRequest {

  Tenant tenant;
  AccountLinkingState state;
  User user;
  OAuthToken oAuthToken;

  public AccountLinkingCompleteRequest(
      Tenant tenant, AccountLinkingState state, User user, OAuthToken oAuthToken) {
    this.tenant = tenant;
    this.state = state;
    this.user = user;
    this.oAuthToken = oAuthToken;
  }

  public Tenant tenant() {
    return tenant;
  }

  public AccountLinkingState state() {
    return state;
  }

  public User user() {
    return user;
  }

  public OAuthToken oAuthToken() {
    return oAuthToken;
  }
}
