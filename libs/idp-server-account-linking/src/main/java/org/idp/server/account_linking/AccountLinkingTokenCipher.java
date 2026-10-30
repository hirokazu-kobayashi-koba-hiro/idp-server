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

import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.platform.crypto.datakey.DataKeyService;
import org.idp.server.platform.crypto.datakey.SealingContext;
import org.idp.server.platform.crypto.datakey.TenantDataCipher;

/**
 * The cipher external tokens are stored under.
 *
 * <p>Tokens are encrypted under the tenant's data key for this purpose, which encrypts nothing
 * else. The data key id is stored with each row: external refresh tokens can live for months, and a
 * new data key generation, or a rotation of the key encryption key, must leave them readable.
 */
public class AccountLinkingTokenCipher {

  public static final String PURPOSE = "account-linking-token";

  public static TenantDataCipher from(DataKeyService dataKeyService) {
    return new TenantDataCipher(dataKeyService, PURPOSE);
  }

  /**
   * Binds a stored access token to its user and provider.
   *
   * <p>Both are fixed from the start of the link, so the same context holds for the parked tokens
   * in the session and for the linked account they move to.
   */
  public static SealingContext accessToken(UserIdentifier user, ExternalIdpProvider provider) {
    return owner(user, provider).with("field", "access_token");
  }

  /** As {@link #accessToken}, for the refresh token. The two cannot be swapped within a row. */
  public static SealingContext refreshToken(UserIdentifier user, ExternalIdpProvider provider) {
    return owner(user, provider).with("field", "refresh_token");
  }

  private static SealingContext owner(UserIdentifier user, ExternalIdpProvider provider) {
    return SealingContext.empty().with("user", user.value()).with("provider", provider.value());
  }
}
