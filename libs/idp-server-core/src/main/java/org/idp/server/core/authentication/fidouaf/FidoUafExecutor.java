package org.idp.server.core.authentication.fidouaf;

import org.idp.server.core.authentication.AuthorizationIdentifier;
import org.idp.server.core.tenant.Tenant;

public interface FidoUafExecutor {

  FidoUafExecutorType type();

  FidoUafExecutionResult getFidoUafFacets(Tenant tenant, FidoUafConfiguration fidoUafConfiguration);

  FidoUafExecutionResult challengeRegistration(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      FidoUafExecutionRequest request,
      FidoUafConfiguration configuration);

  FidoUafExecutionResult verifyRegistration(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      FidoUafExecutionRequest request,
      FidoUafConfiguration configuration);

  FidoUafExecutionResult challengeAuthentication(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      FidoUafExecutionRequest request,
      FidoUafConfiguration configuration);

  FidoUafExecutionResult verifyAuthentication(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      FidoUafExecutionRequest request,
      FidoUafConfiguration configuration);
}
