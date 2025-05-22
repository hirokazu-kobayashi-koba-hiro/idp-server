/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.ciba.user;

import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JoseHandler;
import org.idp.server.basic.jose.JsonWebTokenClaims;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.UserIdentifier;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
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

      return userQueryRepository.get(tenant, userIdentifier);
    } catch (Exception e) {
      log.error("invalid id_token_hint: " + e.getMessage());
      return User.notFound();
    }
  }
}
