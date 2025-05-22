/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.userinfo;

import java.util.Map;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.id_token.IndividualClaimsCreatable;
import org.idp.server.core.oidc.identity.User;

public class UserinfoClaimsCreator implements IndividualClaimsCreatable {

  User user;
  AuthorizationGrant authorizationGrant;

  public UserinfoClaimsCreator(User user, AuthorizationGrant authorizationGrant) {
    this.user = user;
    this.authorizationGrant = authorizationGrant;
  }

  public Map<String, Object> createClaims() {

    return createIndividualClaims(user, authorizationGrant.userinfoClaims());
  }
}
