package org.idp.server.core.oauth.interaction;

import java.util.Map;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.user.UserService;

public interface OAuthUserInteractor {
  OAuthUserInteractionResult interact(
      Tenant tenant,
      OAuthSession oAuthSession,
      OAuthUserInteractionType type,
      Map<String, Object> request,
      UserService userService);
}
