package org.idp.server.usecases.control_plane;

import org.idp.server.basic.datasource.Transaction;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.management.authentication.AuthenticationConfigRegistrationContext;
import org.idp.server.control_plane.management.authentication.AuthenticationConfigRegistrationContextCreator;
import org.idp.server.control_plane.management.authentication.AuthenticationConfigurationManagementApi;
import org.idp.server.control_plane.management.authentication.io.AuthenticationConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.io.AuthenticationConfigRegistrationRequest;
import org.idp.server.control_plane.management.identity.io.UserUpdateRequest;
import org.idp.server.core.authentication.AuthenticationConfigurationIdentifier;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationCommandRepository;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.core.token.OAuthToken;

@Transaction
public class AuthenticationConfigurationManagementEntryService
    implements AuthenticationConfigurationManagementApi {

  AuthenticationConfigurationCommandRepository authenticationConfigurationCommandRepository;
  TenantQueryRepository tenantQueryRepository;

  public AuthenticationConfigurationManagementEntryService(
      AuthenticationConfigurationCommandRepository authenticationConfigurationCommandRepository,
      TenantQueryRepository tenantQueryRepository) {
    this.authenticationConfigurationCommandRepository =
        authenticationConfigurationCommandRepository;
    this.tenantQueryRepository = tenantQueryRepository;
  }

  @Override
  public AuthenticationConfigManagementResponse register(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationConfigRegistrationRequest request,
      RequestAttributes requestAttributes) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    AuthenticationConfigRegistrationContextCreator contextCreator =
        new AuthenticationConfigRegistrationContextCreator(tenant, request);
    AuthenticationConfigRegistrationContext context = contextCreator.create();

    if (context.isDryRun()) {
      return context.toResponse();
    }

    authenticationConfigurationCommandRepository.register(
        tenant, context.authenticationConfiguration());

    return context.toResponse();
  }

  @Override
  public AuthenticationConfigManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes) {
    return null;
  }

  @Override
  public AuthenticationConfigManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserUpdateRequest request,
      RequestAttributes requestAttributes) {
    return null;
  }

  @Override
  public AuthenticationConfigManagementResponse find(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes) {
    return null;
  }
}
