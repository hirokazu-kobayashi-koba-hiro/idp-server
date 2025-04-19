package org.idp.server.core.authentication.device;

import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.tenant.Tenant;

public interface AuthenticationDeviceQueryRepository {

  AuthenticationDevices find(Tenant tenant, User user);
}
