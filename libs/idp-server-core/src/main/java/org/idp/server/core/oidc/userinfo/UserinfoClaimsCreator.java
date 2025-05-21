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
