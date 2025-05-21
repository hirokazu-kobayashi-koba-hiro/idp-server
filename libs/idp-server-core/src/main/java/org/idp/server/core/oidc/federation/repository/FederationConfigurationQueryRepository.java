package org.idp.server.core.oidc.federation.repository;

import java.util.List;
import org.idp.server.core.oidc.federation.FederationConfiguration;
import org.idp.server.core.oidc.federation.FederationConfigurationIdentifier;
import org.idp.server.core.oidc.federation.FederationType;
import org.idp.server.core.oidc.federation.sso.SsoProvider;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface FederationConfigurationQueryRepository {

  <T> T get(Tenant tenant, FederationType federationType, SsoProvider ssoProvider, Class<T> clazz);

  FederationConfiguration find(Tenant tenant, FederationConfigurationIdentifier identifier);

  List<FederationConfiguration> findList(Tenant tenant, int limit, int offset);
}
