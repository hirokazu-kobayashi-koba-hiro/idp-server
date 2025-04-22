package org.idp.server.core.ciba.user;

import org.idp.server.core.basic.jose.JoseContext;
import org.idp.server.core.basic.jose.JoseHandler;
import org.idp.server.core.basic.jose.JsonWebTokenClaims;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.tenant.Tenant;

public class IdTokenHintResolver implements UserHintResolver {

  JoseHandler joseHandler = new JoseHandler();

  @Override
  public User resolve(
      Tenant tenant,
      UserHint userHint,
      UserHintRelatedParams userHintRelatedParams,
      UserRepository userRepository) {

    try {
      String idToken = userHint.value();
      String serverJwks = userHintRelatedParams.optValueAsString("serverJwks", "");
      String clientSecret = userHintRelatedParams.optValueAsString("clientSecret", "");
      JoseContext joseContext = joseHandler.handle(idToken, serverJwks, serverJwks, clientSecret);

      joseContext.verifySignature();

      JsonWebTokenClaims claims = joseContext.claims();
      String sub = claims.getSub();

      return userRepository.get(tenant, sub);
    } catch (Exception e) {

      return User.notFound();
    }
  }
}
