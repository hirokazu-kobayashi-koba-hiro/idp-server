package org.idp.server.core.oidc.discovery;

import org.idp.server.core.oidc.discovery.handler.io.JwksRequestResponse;
import org.idp.server.core.oidc.discovery.handler.io.ServerConfigurationRequestResponse;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface DiscoveryProtocol {

  AuthorizationProvider authorizationProtocolProvider();

  ServerConfigurationRequestResponse getConfiguration(Tenant tenant);

  JwksRequestResponse getJwks(Tenant tenant);
}
