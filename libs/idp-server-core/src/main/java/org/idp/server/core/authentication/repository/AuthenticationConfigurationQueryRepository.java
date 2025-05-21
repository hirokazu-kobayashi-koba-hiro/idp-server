package org.idp.server.core.authentication.repository;

import java.util.List;
import org.idp.server.core.authentication.AuthenticationConfiguration;
import org.idp.server.core.authentication.AuthenticationConfigurationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AuthenticationConfigurationQueryRepository {
  <T> T get(Tenant tenant, String key, Class<T> clazz);

  AuthenticationConfiguration find(Tenant tenant, AuthenticationConfigurationIdentifier identifier);

  List<AuthenticationConfiguration> findList(Tenant tenant, int limit, int offset);
}
