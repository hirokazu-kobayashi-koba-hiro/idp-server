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

import java.util.Map;
import org.idp.server.account_linking.ExternalIdpProvider;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/** Everything the link start phase works from. */
public class AccountLinkingStartRequest {

  Tenant tenant;
  User user;
  OAuthToken oAuthToken;
  ExternalIdpProvider provider;
  Map<String, Object> body;

  public AccountLinkingStartRequest(
      Tenant tenant,
      User user,
      OAuthToken oAuthToken,
      ExternalIdpProvider provider,
      Map<String, Object> body) {
    this.tenant = tenant;
    this.user = user;
    this.oAuthToken = oAuthToken;
    this.provider = provider;
    this.body = body == null ? Map.of() : body;
  }

  public Tenant tenant() {
    return tenant;
  }

  public User user() {
    return user;
  }

  public OAuthToken oAuthToken() {
    return oAuthToken;
  }

  public ExternalIdpProvider provider() {
    return provider;
  }

  /** Where the browser is sent once the link settles. Checked against the client's allow list. */
  public String redirectUri() {
    return optString("redirect_uri");
  }

  public String scope() {
    return optString("scope");
  }

  private String optString(String key) {
    Object value = body.get(key);
    return value == null ? null : value.toString();
  }
}
