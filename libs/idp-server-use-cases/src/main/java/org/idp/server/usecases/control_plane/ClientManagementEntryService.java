package org.idp.server.usecases.control_plane;

import org.idp.server.basic.datasource.Transaction;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.management.client.ClientManagementApi;
import org.idp.server.control_plane.management.client.ClientRegistrationContext;
import org.idp.server.control_plane.management.client.ClientRegistrationContextCreator;
import org.idp.server.control_plane.management.client.io.ClientManagementResponse;
import org.idp.server.control_plane.management.client.io.ClientRegistrationRequest;
import org.idp.server.control_plane.management.client.validator.ClientRegistrationRequestValidationResult;
import org.idp.server.control_plane.management.client.validator.ClientRegistrationRequestValidator;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationCommandRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.configuration.handler.io.ClientConfigurationManagementListResponse;
import org.idp.server.core.oidc.configuration.handler.io.ClientConfigurationManagementResponse;
import org.idp.server.core.token.OAuthToken;

@Transaction
public class ClientManagementEntryService implements ClientManagementApi {

  TenantQueryRepository tenantQueryRepository;
  ClientConfigurationCommandRepository clientConfigurationCommandRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;

  public ClientManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      ClientConfigurationCommandRepository clientConfigurationCommandRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.clientConfigurationCommandRepository = clientConfigurationCommandRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
  }

  public ClientManagementResponse register(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientRegistrationRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    ClientRegistrationRequestValidator validator = new ClientRegistrationRequestValidator(request);
    ClientRegistrationRequestValidationResult validate = validator.validate();

    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    ClientRegistrationContextCreator contextCreator =
        new ClientRegistrationContextCreator(tenant, request);
    ClientRegistrationContext context = contextCreator.create();
    if (context.isDryRun()) {
      return context.toResponse();
    }

    clientConfigurationCommandRepository.register(tenant, context.clientConfiguration());

    return context.toResponse();
  }

  @Override
  public ClientManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientRegistrationRequest request,
      RequestAttributes requestAttributes) {
    return null;
  }

  @Override
  public ClientConfigurationManagementListResponse find(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes) {
    return null;
  }

  @Override
  public ClientConfigurationManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RequestedClientId requestedClientId,
      RequestAttributes requestAttributes) {
    return null;
  }

  @Override
  public ClientConfigurationManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RequestedClientId requestedClientId,
      RequestAttributes requestAttributes) {
    return null;
  }
}
