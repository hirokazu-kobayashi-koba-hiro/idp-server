package org.idp.server.core.userinfo;

import java.util.Map;
import org.idp.server.core.identity.User;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.identity.IndividualClaimsCreatable;

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
