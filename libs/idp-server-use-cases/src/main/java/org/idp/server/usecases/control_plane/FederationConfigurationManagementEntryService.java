package org.idp.server.usecases.control_plane;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.datasource.Transaction;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.federation.*;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementResponse;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementStatus;
import org.idp.server.control_plane.management.federation.io.FederationConfigRequest;
import org.idp.server.core.federation.FederationConfiguration;
import org.idp.server.core.federation.FederationConfigurationIdentifier;
import org.idp.server.core.federation.repository.FederationConfigurationCommandRepository;
import org.idp.server.core.federation.repository.FederationConfigurationQueryRepository;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.core.token.OAuthToken;

@Transaction
public class FederationConfigurationManagementEntryService
    implements FederationConfigurationManagementApi {

  FederationConfigurationQueryRepository federationConfigurationQueryRepository;
  FederationConfigurationCommandRepository federationConfigurationCommandRepository;
  TenantQueryRepository tenantQueryRepository;

  public FederationConfigurationManagementEntryService(
      FederationConfigurationQueryRepository federationConfigurationQueryRepository,
      FederationConfigurationCommandRepository federationConfigurationCommandRepository,
      TenantQueryRepository tenantQueryRepository) {
    this.federationConfigurationQueryRepository = federationConfigurationQueryRepository;
    this.federationConfigurationCommandRepository = federationConfigurationCommandRepository;
    this.tenantQueryRepository = tenantQueryRepository;
  }

  @Override
  public FederationConfigManagementResponse create(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    AdminPermissions permissions = getRequiredPermissions("create");
    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      return new FederationConfigManagementResponse(
          FederationConfigManagementStatus.FORBIDDEN, response);
    }

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    FederationConfigRegistrationContextCreator contextCreator =
        new FederationConfigRegistrationContextCreator(tenant, request, dryRun);
    FederationConfigRegistrationContext context = contextCreator.create();

    if (context.isDryRun()) {
      return context.toResponse();
    }

    federationConfigurationCommandRepository.register(tenant, context.configuration());

    return context.toResponse();
  }

  @Transaction(readOnly = true)
  @Override
  public FederationConfigManagementResponse findList(
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
      return new FederationConfigManagementResponse(
          FederationConfigManagementStatus.FORBIDDEN, response);
    }

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    List<FederationConfiguration> configurations =
        federationConfigurationQueryRepository.findList(tenant, limit, offset);
    Map<String, Object> response = new HashMap<>();
    response.put("list", configurations.stream().map(FederationConfiguration::payload).toList());

    return new FederationConfigManagementResponse(FederationConfigManagementStatus.OK, response);
  }

  @Transaction(readOnly = true)
  @Override
  public FederationConfigManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigurationIdentifier identifier,
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
      return new FederationConfigManagementResponse(
          FederationConfigManagementStatus.FORBIDDEN, response);
    }

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    FederationConfiguration configuration =
        federationConfigurationQueryRepository.find(tenant, identifier);

    if (!configuration.exists()) {
      return new FederationConfigManagementResponse(
          FederationConfigManagementStatus.NOT_FOUND, Map.of());
    }

    return new FederationConfigManagementResponse(
        FederationConfigManagementStatus.OK, configuration.payload());
  }

  @Override
  public FederationConfigManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigurationIdentifier identifier,
      FederationConfigRequest request,
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
      return new FederationConfigManagementResponse(
          FederationConfigManagementStatus.FORBIDDEN, response);
    }

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    FederationConfiguration before =
        federationConfigurationQueryRepository.find(tenant, identifier);

    if (!before.exists()) {
      return new FederationConfigManagementResponse(
          FederationConfigManagementStatus.NOT_FOUND, Map.of());
    }

    FederationConfigUpdateContextCreator contextCreator =
        new FederationConfigUpdateContextCreator(tenant, before, request, dryRun);
    FederationConfigUpdateContext context = contextCreator.create();

    if (context.isDryRun()) {
      return context.toResponse();
    }

    federationConfigurationCommandRepository.update(tenant, context.after());

    return context.toResponse();
  }

  @Override
  public FederationConfigManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigurationIdentifier identifier,
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
      return new FederationConfigManagementResponse(
          FederationConfigManagementStatus.FORBIDDEN, response);
    }

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    FederationConfiguration configuration =
        federationConfigurationQueryRepository.find(tenant, identifier);

    if (!configuration.exists()) {
      return new FederationConfigManagementResponse(
          FederationConfigManagementStatus.NOT_FOUND, Map.of());
    }

    federationConfigurationCommandRepository.delete(tenant, configuration);

    return new FederationConfigManagementResponse(
        FederationConfigManagementStatus.OK, configuration.payload());
  }
}
