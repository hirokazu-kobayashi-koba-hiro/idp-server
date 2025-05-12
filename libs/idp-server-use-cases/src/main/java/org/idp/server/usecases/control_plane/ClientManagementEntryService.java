package org.idp.server.usecases.control_plane;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.datasource.Transaction;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.management.oidc.client.ClientManagementApi;
import org.idp.server.control_plane.management.oidc.client.ClientRegistrationContext;
import org.idp.server.control_plane.management.oidc.client.ClientRegistrationContextCreator;
import org.idp.server.control_plane.management.oidc.client.io.ClientConfigurationManagementResponse;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementStatus;
import org.idp.server.control_plane.management.oidc.client.io.ClientRegistrationRequest;
import org.idp.server.control_plane.management.oidc.client.validator.ClientRegistrationRequestValidationResult;
import org.idp.server.control_plane.management.oidc.client.validator.ClientRegistrationRequestValidator;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.core.oidc.client.ClientIdentifier;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationCommandRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
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

  public ClientConfigurationManagementResponse register(
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
  public ClientConfigurationManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientIdentifier clientIdentifier,
      ClientRegistrationRequest request,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, clientIdentifier);

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

    clientConfigurationCommandRepository.update(tenant, context.clientConfiguration());

    return context.toResponse();
  }

  @Override
  public ClientConfigurationManagementResponse find(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    List<ClientConfiguration> clientConfigurations =
        clientConfigurationQueryRepository.find(tenant, limit, offset);
    Map<String, Object> response = new HashMap<>();
    response.put("list", clientConfigurations.stream().map(ClientConfiguration::toMap).toList());

    return new ClientConfigurationManagementResponse(ClientManagementStatus.OK, response);
  }

  @Override
  public ClientConfigurationManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientIdentifier clientIdentifier,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, clientIdentifier);
    Map<String, Object> response = new HashMap<>();
    response.put("client", clientConfiguration.toMap());

    return new ClientConfigurationManagementResponse(ClientManagementStatus.OK, response);
  }

  @Override
  public ClientConfigurationManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientIdentifier clientIdentifier,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, clientIdentifier);
    clientConfigurationCommandRepository.delete(tenant, clientConfiguration);

    return new ClientConfigurationManagementResponse(ClientManagementStatus.NO_CONTENT, Map.of());
  }
}
