package org.idp.server.core.federation.repository;

import java.util.List;
import org.idp.server.core.federation.FederationConfiguration;
import org.idp.server.core.federation.FederationConfigurationIdentifier;
import org.idp.server.core.federation.FederationType;
import org.idp.server.core.federation.sso.SsoProvider;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface FederationConfigurationQueryRepository {

  <T> T get(Tenant tenant, FederationType federationType, SsoProvider ssoProvider, Class<T> clazz);

  FederationConfiguration find(Tenant tenant, FederationConfigurationIdentifier identifier);

  List<FederationConfiguration> findList(Tenant tenant, int limit, int offset);
}
