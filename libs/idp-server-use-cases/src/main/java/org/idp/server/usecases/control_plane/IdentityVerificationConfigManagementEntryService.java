package org.idp.server.usecases.control_plane;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.datasource.Transaction;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.identity.verification.*;
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
  public IdentityVerificationConfigManagementResponse create(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationConfigRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("register");
    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      return new IdentityVerificationConfigManagementResponse(
          IdentityVerificationConfigManagementStatus.FORBIDDEN, response);
    }

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    IdentityVerificationConfigRegistrationContextCreator contextCreator =
        new IdentityVerificationConfigRegistrationContextCreator(tenant, request, dryRun);
    IdentityVerificationConfigRegistrationContext context = contextCreator.create();
    if (dryRun) {
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

    AdminPermissions permissions = getRequiredPermissions("findList");
    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      return new IdentityVerificationConfigManagementResponse(
          IdentityVerificationConfigManagementStatus.FORBIDDEN, response);
    }

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

    AdminPermissions permissions = getRequiredPermissions("get");
    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      return new IdentityVerificationConfigManagementResponse(
          IdentityVerificationConfigManagementStatus.FORBIDDEN, response);
    }

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
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("update");
    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      return new IdentityVerificationConfigManagementResponse(
          IdentityVerificationConfigManagementStatus.FORBIDDEN, response);
    }

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    IdentityVerificationConfiguration configuration =
        identityVerificationConfigurationQueryRepository.get(tenant, identifier);

    IdentityVerificationConfigUpdateContextCreator contextCreator =
        new IdentityVerificationConfigUpdateContextCreator(tenant, request, configuration, dryRun);
    IdentityVerificationConfigUpdateContext context = contextCreator.create();

    if (context.isDryRun()) {
      return context.toResponse();
    }

    identityVerificationConfigurationCommandRepository.update(
        tenant, context.afterType(), context.after());

    return context.toResponse();
  }

  @Override
  public IdentityVerificationConfigManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("delete");
    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      return new IdentityVerificationConfigManagementResponse(
          IdentityVerificationConfigManagementStatus.FORBIDDEN, response);
    }

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    IdentityVerificationConfiguration configuration =
        identityVerificationConfigurationQueryRepository.get(tenant, identifier);

    if (dryRun) {
      return new IdentityVerificationConfigManagementResponse(
          IdentityVerificationConfigManagementStatus.NO_CONTENT, Map.of());
    }

    identityVerificationConfigurationCommandRepository.delete(
        tenant, configuration.type(), configuration);

    return new IdentityVerificationConfigManagementResponse(
        IdentityVerificationConfigManagementStatus.NO_CONTENT, Map.of());
  }
}
