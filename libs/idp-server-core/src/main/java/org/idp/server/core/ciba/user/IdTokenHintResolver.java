package org.idp.server.core.ciba.user;

import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JoseHandler;
import org.idp.server.basic.jose.JsonWebTokenClaims;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserIdentifier;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class IdTokenHintResolver implements UserHintResolver {

  JoseHandler joseHandler = new JoseHandler();

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

      return User.notFound();
    }
  }
}
