package org.idp.server.core.userinfo;

import java.util.Map;
import org.idp.server.core.oauth.grant.AuthorizationGrant;
import org.idp.server.core.oauth.identity.IndividualClaimsCreatable;
import org.idp.server.core.oauth.identity.User;

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
