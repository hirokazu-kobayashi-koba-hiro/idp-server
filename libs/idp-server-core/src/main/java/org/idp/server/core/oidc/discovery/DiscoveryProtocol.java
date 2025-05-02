package org.idp.server.core.oidc.discovery;

import org.idp.server.basic.dependency.protocol.AuthorizationProtocolProvider;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.discovery.handler.io.JwksRequestResponse;
import org.idp.server.core.oidc.discovery.handler.io.ServerConfigurationRequestResponse;

public interface DiscoveryProtocol {

  AuthorizationProtocolProvider authorizationProtocolProvider();

  ServerConfigurationRequestResponse getConfiguration(Tenant tenant);

  JwksRequestResponse getJwks(Tenant tenant);
}
