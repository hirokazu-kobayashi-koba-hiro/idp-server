package org.idp.server.usecases.control_plane;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.datasource.Transaction;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.management.authentication.*;
import org.idp.server.control_plane.management.authentication.io.AuthenticationConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.io.AuthenticationConfigManagementStatus;
import org.idp.server.control_plane.management.authentication.io.AuthenticationConfigRegistrationRequest;
import org.idp.server.core.authentication.AuthenticationConfiguration;
import org.idp.server.core.authentication.AuthenticationConfigurationIdentifier;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationCommandRepository;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.core.token.OAuthToken;

@Transaction
public class AuthenticationConfigurationManagementEntryService
    implements AuthenticationConfigurationManagementApi {

  AuthenticationConfigurationCommandRepository authenticationConfigurationCommandRepository;
  AuthenticationConfigurationQueryRepository authenticationConfigurationQueryRepository;
  TenantQueryRepository tenantQueryRepository;

  public AuthenticationConfigurationManagementEntryService(
      AuthenticationConfigurationCommandRepository authenticationConfigurationCommandRepository,
      AuthenticationConfigurationQueryRepository authenticationConfigurationQueryRepository,
      TenantQueryRepository tenantQueryRepository) {
    this.authenticationConfigurationCommandRepository =
        authenticationConfigurationCommandRepository;
    this.authenticationConfigurationQueryRepository = authenticationConfigurationQueryRepository;
    this.tenantQueryRepository = tenantQueryRepository;
  }

  @Override
  public AuthenticationConfigManagementResponse register(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationConfigRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    AuthenticationConfigRegistrationContextCreator contextCreator =
        new AuthenticationConfigRegistrationContextCreator(tenant, request, dryRun);
    AuthenticationConfigRegistrationContext context = contextCreator.create();

    if (context.isDryRun()) {
      return context.toResponse();
    }

    authenticationConfigurationCommandRepository.register(
        tenant, context.authenticationConfiguration());

    return context.toResponse();
  }

  @Override
  public AuthenticationConfigManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    List<AuthenticationConfiguration> configurations =
        authenticationConfigurationQueryRepository.findList(tenant, limit, offset);
    Map<String, Object> response = new HashMap<>();
    response.put(
        "list", configurations.stream().map(AuthenticationConfiguration::payload).toList());

    return new AuthenticationConfigManagementResponse(
        AuthenticationConfigManagementStatus.OK, response);
  }

  @Override
  public AuthenticationConfigManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    AuthenticationConfiguration configuration =
        authenticationConfigurationQueryRepository.get(tenant, identifier);

    return new AuthenticationConfigManagementResponse(
        AuthenticationConfigManagementStatus.OK, configuration.payload());
  }

  @Override
  public AuthenticationConfigManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationConfigurationIdentifier identifier,
      AuthenticationConfigRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    AuthenticationConfiguration before =
        authenticationConfigurationQueryRepository.get(tenant, identifier);

    AuthenticationConfigUpdateContextCreator contextCreator =
        new AuthenticationConfigUpdateContextCreator(tenant, before, request, dryRun);
    AuthenticationConfigUpdateContext context = contextCreator.create();

    if (context.isDryRun()) {
      return context.toResponse();
    }

    authenticationConfigurationCommandRepository.update(tenant, context.after());

    return context.toResponse();
  }

  @Override
  public AuthenticationConfigManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    AuthenticationConfiguration configuration =
        authenticationConfigurationQueryRepository.get(tenant, identifier);

    return new AuthenticationConfigManagementResponse(
        AuthenticationConfigManagementStatus.OK, configuration.payload());
  }
}
