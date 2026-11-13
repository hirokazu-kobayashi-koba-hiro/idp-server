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

package org.idp.server.account_linking;

import org.idp.server.core.openid.identity.User;

/**
 * Which external account granted access, and the name it is shown under.
 *
 * <p>Both are optional. A plain OAuth 2.0 delegation grants access to a resource without saying who
 * the resource owner is, and a link does not need it to be keyed.
 */
public class ExternalAccountIdentity {

  String federatedUserId;
  String federatedUsername;

  ExternalAccountIdentity(String federatedUserId, String federatedUsername) {
    this.federatedUserId = federatedUserId;
    this.federatedUsername = federatedUsername;
  }

  /**
   * The identity the provider asserted at the callback.
   *
   * <p>The account is the id_token's verified {@code sub} when there is one: it is the only value
   * the provider asserts about which account granted access. Otherwise it is the {@code
   * external_user_id} the userinfo mapping reads, for a provider that answers in its own shape.
   *
   * <p>The name is what the userinfo mapping puts in {@code preferred_username}, otherwise in
   * {@code email}. It lets the user tell several links of one provider apart, and the mapping rules
   * can fill it with any path, static value or function they allow.
   *
   * @param verifiedSubject the id_token's verified {@code sub}, or {@code null} without an id_token
   * @param federatedUser the mapped userinfo, or {@code null} when the provider has no userinfo
   */
  public static ExternalAccountIdentity of(String verifiedSubject, User federatedUser) {
    if (federatedUser == null) {
      return new ExternalAccountIdentity(verifiedSubject, null);
    }
    String federatedUserId =
        verifiedSubject != null ? verifiedSubject : federatedUser.externalUserId();
    return new ExternalAccountIdentity(federatedUserId, displayName(federatedUser));
  }

  private static String displayName(User federatedUser) {
    String preferredUsername = federatedUser.preferredUsername();
    if (preferredUsername != null && !preferredUsername.isBlank()) {
      return preferredUsername;
    }
    return federatedUser.email();
  }

  public String federatedUserId() {
    return federatedUserId;
  }

  public String federatedUsername() {
    return federatedUsername;
  }
}
