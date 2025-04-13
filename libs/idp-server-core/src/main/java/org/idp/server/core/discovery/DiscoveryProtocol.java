package org.idp.server.core.discovery;

import org.idp.server.core.basic.dependency.protcol.AuthorizationProtocolProvider;
import org.idp.server.core.discovery.handler.io.JwksRequestResponse;
import org.idp.server.core.discovery.handler.io.ServerConfigurationRequestResponse;
import org.idp.server.core.tenant.Tenant;

public interface DiscoveryProtocol {

  AuthorizationProtocolProvider authorizationProtocolProvider();

  ServerConfigurationRequestResponse getConfiguration(Tenant tenant);

  JwksRequestResponse getJwks(Tenant tenant);
}
