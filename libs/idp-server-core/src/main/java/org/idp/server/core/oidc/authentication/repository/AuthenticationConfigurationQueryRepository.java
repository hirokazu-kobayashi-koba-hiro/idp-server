package org.idp.server.core.oidc.authentication.repository;

import java.util.List;
import org.idp.server.core.oidc.authentication.AuthenticationConfiguration;
import org.idp.server.core.oidc.authentication.AuthenticationConfigurationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AuthenticationConfigurationQueryRepository {
  <T> T get(Tenant tenant, String key, Class<T> clazz);

  AuthenticationConfiguration find(Tenant tenant, AuthenticationConfigurationIdentifier identifier);

  List<AuthenticationConfiguration> findList(Tenant tenant, int limit, int offset);
}
