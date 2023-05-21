package org.idp.server.userinfo;

import java.util.List;
import java.util.Map;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.identity.IndividualClaimsCreatable;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.identity.UserinfoClaims;
import org.idp.server.oauth.identity.UserinfoIndividualClaimsDecider;
import org.idp.server.type.oauth.Scopes;

public class UserinfoClaimsCreator implements IndividualClaimsCreatable {

  User user;
  AuthorizationGrant authorizationGrant;
  List<String> supportedClaims;

  public UserinfoClaimsCreator(
      User user, AuthorizationGrant authorizationGrant, List<String> supportedClaims) {
    this.user = user;
    this.authorizationGrant = authorizationGrant;
    this.supportedClaims = supportedClaims;
  }

  public Map<String, Object> createClaims() {
    Scopes scopes = authorizationGrant.scopes();
    UserinfoClaims userinfo = authorizationGrant.claimsPayload().userinfo();
    UserinfoIndividualClaimsDecider userinfoIndividualClaimsDecider =
        new UserinfoIndividualClaimsDecider(scopes, userinfo, supportedClaims);
    return createIndividualClaims(user, userinfoIndividualClaimsDecider);
  }
}
