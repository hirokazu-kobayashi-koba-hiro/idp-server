package org.idp.server.core.authentication;

import java.util.Map;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.tenant.Tenant;

public interface MfaInteractor {

  MfaInteractionResult interact(
      Tenant tenant,
      OAuthSession oAuthSession,
      MfaInteractionType type,
      Map<String, Object> request,
      UserRepository userRepository);
}
