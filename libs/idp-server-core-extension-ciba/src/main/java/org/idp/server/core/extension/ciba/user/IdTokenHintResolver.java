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

package org.idp.server.core.extension.ciba.user;

import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.jose.JoseContext;
import org.idp.server.platform.jose.JoseHandler;
import org.idp.server.platform.jose.JsonWebTokenClaims;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class IdTokenHintResolver implements UserHintResolver {

  JoseHandler joseHandler = new JoseHandler();
  LoggerWrapper log = LoggerWrapper.getLogger(IdTokenHintResolver.class);

  @Override
  public User resolve(
      Tenant tenant,
      UserHint userHint,
      UserHintRelatedParams userHintRelatedParams,
      UserQueryRepository userQueryRepository) {

    try {
      String idToken = userHint.value();
      String serverJwks = userHintRelatedParams.optValueAsString("serverJwks", "");
      String clientSecret = userHintRelatedParams.optValueAsString("clientSecret", "");
      JoseContext joseContext = joseHandler.handle(idToken, serverJwks, serverJwks, clientSecret);

      joseContext.verifySignature();

      JsonWebTokenClaims claims = joseContext.claims();
      String sub = claims.getSub();
      UserIdentifier userIdentifier = new UserIdentifier(sub);

      return userQueryRepository.findById(tenant, userIdentifier);
    } catch (Exception e) {
      log.error("Invalid id_token_hint: error={}", e.getMessage(), e);
      return User.notFound();
    }
  }
}
