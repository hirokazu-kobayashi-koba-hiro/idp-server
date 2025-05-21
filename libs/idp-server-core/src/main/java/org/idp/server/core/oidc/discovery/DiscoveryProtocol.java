package org.idp.server.core.oidc.discovery;

import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.discovery.handler.io.JwksRequestResponse;
import org.idp.server.core.oidc.discovery.handler.io.ServerConfigurationRequestResponse;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;

public interface DiscoveryProtocol {

  AuthorizationProvider authorizationProtocolProvider();

  ServerConfigurationRequestResponse getConfiguration(Tenant tenant);

  JwksRequestResponse getJwks(Tenant tenant);
}
