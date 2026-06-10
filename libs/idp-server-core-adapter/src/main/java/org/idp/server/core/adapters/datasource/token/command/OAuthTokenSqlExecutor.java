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

package org.idp.server.core.adapters.datasource.token.command;

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.crypto.AesCipher;
import org.idp.server.platform.crypto.HmacHasher;

public interface OAuthTokenSqlExecutor {

  /**
   * Insert the token into the DB and return the row representation that is compatible with the
   * cache layer (same key set as {@code query.selectOneByAccessToken}). This avoids an additional
   * SELECT after INSERT to warm the cache, which would otherwise hit the replica or add a round
   * trip on the primary.
   */
  Map<String, String> insert(OAuthToken oAuthToken, AesCipher aesCipher, HmacHasher hmacHasher);

  void delete(OAuthToken oAuthToken, AesCipher aesCipher, HmacHasher hmacHasher);

  List<String> selectHashedAccessTokensByUserAndClient(
      String tenantId, String userId, String clientId);

  void deleteByUserAndClient(String tenantId, String userId, String clientId);
}
