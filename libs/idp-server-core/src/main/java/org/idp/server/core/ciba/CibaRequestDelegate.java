package org.idp.server.core.ciba;

import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.ciba.UserCode;

public interface CibaRequestDelegate {
  User find(TenantIdentifier tenantIdentifier, UserCriteria criteria);

  boolean authenticate(TenantIdentifier tenantIdentifier, User user, UserCode userCode);

  void notify(
      TenantIdentifier tenantIdentifier, User user, BackchannelAuthenticationRequest request);
}
