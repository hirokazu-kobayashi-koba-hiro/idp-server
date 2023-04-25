package org.idp.server.userinfo;

import java.util.Map;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.identity.IndividualClaimsCreatable;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.identity.UserinfoClaims;
import org.idp.server.oauth.identity.UserinfoIndividualClaimsDecider;
import org.idp.server.type.oauth.Scopes;

public class UserinfoClaimsCreator implements IndividualClaimsCreatable {
  public Map<String, Object> createClaims(User user, AuthorizationGrant authorizationGrant) {
    Scopes scopes = authorizationGrant.scopes();
    UserinfoClaims userinfo = authorizationGrant.claimsPayload().userinfo();
    UserinfoIndividualClaimsDecider userinfoIndividualClaimsDecider =
        new UserinfoIndividualClaimsDecider(scopes, userinfo);
    return createIndividualClaims(user, userinfoIndividualClaimsDecider);
  }
}
