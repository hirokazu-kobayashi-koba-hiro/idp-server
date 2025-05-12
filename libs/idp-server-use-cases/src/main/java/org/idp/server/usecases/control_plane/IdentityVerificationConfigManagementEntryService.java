package org.idp.server.usecases.control_plane;

import java.util.List;
import java.util.Map;
import org.idp.server.basic.datasource.Transaction;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.management.identity.verification.IdentityVerificationConfigManagementApi;
import org.idp.server.control_plane.management.identity.verification.IdentityVerificationConfigRegistrationContext;
import org.idp.server.control_plane.management.identity.verification.IdentityVerificationConfigRegistrationContextCreator;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigManagementResponse;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigManagementStatus;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigRegistrationRequest;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigUpdateRequest;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfigurationCommandRepository;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfigurationIdentifier;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfigurationQueryRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.core.token.OAuthToken;

@Transaction
public class IdentityVerificationConfigManagementEntryService
    implements IdentityVerificationConfigManagementApi {

  IdentityVerificationConfigurationCommandRepository
      identityVerificationConfigurationCommandRepository;
  IdentityVerificationConfigurationQueryRepository identityVerificationConfigurationQueryRepository;
  TenantQueryRepository tenantQueryRepository;

  public IdentityVerificationConfigManagementEntryService(
      IdentityVerificationConfigurationCommandRepository
          identityVerificationConfigurationCommandRepository,
      IdentityVerificationConfigurationQueryRepository
          identityVerificationConfigurationQueryRepository,
      TenantQueryRepository tenantQueryRepository) {
    this.identityVerificationConfigurationCommandRepository =
        identityVerificationConfigurationCommandRepository;
    this.identityVerificationConfigurationQueryRepository =
        identityVerificationConfigurationQueryRepository;
    this.tenantQueryRepository = tenantQueryRepository;
  }

  @Override
  public IdentityVerificationConfigManagementResponse register(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationConfigRegistrationRequest request,
      RequestAttributes requestAttributes) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    IdentityVerificationConfigRegistrationContextCreator contextCreator =
        new IdentityVerificationConfigRegistrationContextCreator(tenant, request);
    IdentityVerificationConfigRegistrationContext context = contextCreator.create();
    if (context.isDryRun()) {
      return context.toResponse();
    }

    identityVerificationConfigurationCommandRepository.register(
        tenant, context.type(), context.identityVerificationConfiguration());

    return context.toResponse();
  }

  @Override
  public IdentityVerificationConfigManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    List<IdentityVerificationConfiguration> configurations =
        identityVerificationConfigurationQueryRepository.findList(tenant, limit, offset);
    Map<String, Object> response =
        Map.of(
            "list", configurations.stream().map(IdentityVerificationConfiguration::toMap).toList());

    return new IdentityVerificationConfigManagementResponse(
        IdentityVerificationConfigManagementStatus.OK, response);
  }

  @Override
  public IdentityVerificationConfigManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    IdentityVerificationConfiguration configuration =
        identityVerificationConfigurationQueryRepository.get(tenant, identifier);

    return new IdentityVerificationConfigManagementResponse(
        IdentityVerificationConfigManagementStatus.OK, configuration.toMap());
  }

  @Override
  public IdentityVerificationConfigManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationConfigurationIdentifier identifier,
      IdentityVerificationConfigUpdateRequest request,
      RequestAttributes requestAttributes) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    IdentityVerificationConfiguration configuration =
        identityVerificationConfigurationQueryRepository.get(tenant, identifier);

    IdentityVerificationConfigRegistrationContextCreator contextCreator =
        new IdentityVerificationConfigRegistrationContextCreator(tenant, request);
    IdentityVerificationConfigRegistrationContext context = contextCreator.create();
    if (context.isDryRun()) {
      return context.toResponse();
    }

    identityVerificationConfigurationCommandRepository.update(
        tenant, context.type(), context.identityVerificationConfiguration());

    return context.toResponse();
  }

  @Override
  public IdentityVerificationConfigManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    IdentityVerificationConfiguration configuration =
        identityVerificationConfigurationQueryRepository.get(tenant, identifier);

    identityVerificationConfigurationCommandRepository.delete(
        tenant, configuration.type(), configuration);

    return new IdentityVerificationConfigManagementResponse(
        IdentityVerificationConfigManagementStatus.NO_CONTENT, Map.of());
  }
}
