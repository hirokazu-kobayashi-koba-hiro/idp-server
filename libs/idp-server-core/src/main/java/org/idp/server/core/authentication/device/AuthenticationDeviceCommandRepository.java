package org.idp.server.core.authentication.device;

import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.tenant.Tenant;

public interface AuthenticationDeviceCommandRepository {

  void register(Tenant tenant, User user, AuthenticationDevice authenticationDevice);
}
