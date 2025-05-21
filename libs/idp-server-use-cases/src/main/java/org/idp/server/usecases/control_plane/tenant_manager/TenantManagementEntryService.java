package org.idp.server.usecases.control_plane.tenant_manager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.verifier.TenantVerifier;
import org.idp.server.control_plane.management.tenant.*;
import org.idp.server.control_plane.management.tenant.io.TenantManagementResponse;
import org.idp.server.control_plane.management.tenant.io.TenantManagementStatus;
import org.idp.server.control_plane.management.tenant.io.TenantRequest;
import org.idp.server.control_plane.management.tenant.verifier.TenantManagementVerificationResult;
import org.idp.server.control_plane.management.tenant.verifier.TenantManagementVerifier;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.organization.Organization;
import org.idp.server.core.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.core.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantCommandRepository;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationCommandRepository;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;

@Transaction
public class TenantManagementEntryService implements TenantManagementApi {

  TenantCommandRepository tenantCommandRepository;
  TenantQueryRepository tenantQueryRepository;
  OrganizationRepository organizationRepository;
  AuthorizationServerConfigurationCommandRepository
      authorizationServerConfigurationCommandRepository;
  TenantManagementVerifier tenantManagementVerifier;

  LoggerWrapper log = LoggerWrapper.getLogger(TenantManagementEntryService.class);

  public TenantManagementEntryService(
      TenantCommandRepository tenantCommandRepository,
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      AuthorizationServerConfigurationCommandRepository
          authorizationServerConfigurationCommandRepository) {
    this.tenantCommandRepository = tenantCommandRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.authorizationServerConfigurationCommandRepository =
        authorizationServerConfigurationCommandRepository;
    TenantVerifier tenantVerifier = new TenantVerifier(tenantQueryRepository);
    this.tenantManagementVerifier = new TenantManagementVerifier(tenantVerifier);
  }

  @Override
  public TenantManagementResponse create(
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantRequest request,
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
      log.warn(response.toString());
      return new TenantManagementResponse(TenantManagementStatus.FORBIDDEN, response);
    }

    Tenant adminTenant = tenantQueryRepository.get(adminTenantIdentifier);
    OrganizationIdentifier organizationIdentifier = operator.currentOrganizationIdentifier();
    Organization organization = organizationRepository.get(adminTenant, organizationIdentifier);

    TenantManagementRegistrationContextCreator contextCreator =
        new TenantManagementRegistrationContextCreator(
            adminTenant, request, organization, operator, dryRun);
    TenantManagementRegistrationContext context = contextCreator.create();

    TenantManagementVerificationResult verificationResult =
        tenantManagementVerifier.verify(context);
    if (!verificationResult.isValid()) {
      return verificationResult.errorResponse();
    }

    if (dryRun) {
      return context.toResponse();
    }

    tenantCommandRepository.register(context.newTenant());
    organizationRepository.update(adminTenant, context.organization());
    authorizationServerConfigurationCommandRepository.register(
        context.newTenant(), context.authorizationServerConfiguration());

    return context.toResponse();
  }

  @Transaction(readOnly = true)
  @Override
  public TenantManagementResponse findList(
      TenantIdentifier adminTenantIdentifier,
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
      log.warn(response.toString());
      return new TenantManagementResponse(TenantManagementStatus.FORBIDDEN, response);
    }

    tenantQueryRepository.get(adminTenantIdentifier);

    List<Tenant> tenants =
        tenantQueryRepository.findList(operator.assignedTenantsAsTenantIdentifiers());
    Map<String, Object> response = new HashMap<>();
    response.put("list", tenants.stream().map(Tenant::toMap).toList());

    return new TenantManagementResponse(TenantManagementStatus.OK, response);
  }

  @Transaction(readOnly = true)
  @Override
  public TenantManagementResponse get(
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantIdentifier tenantIdentifier,
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
      log.warn(response.toString());
      return new TenantManagementResponse(TenantManagementStatus.FORBIDDEN, response);
    }

    tenantQueryRepository.get(adminTenantIdentifier);

    Tenant tenant = tenantQueryRepository.find(tenantIdentifier);

    if (!tenant.exists()) {
      return new TenantManagementResponse(TenantManagementStatus.NOT_FOUND, Map.of());
    }

    return new TenantManagementResponse(TenantManagementStatus.OK, tenant.toMap());
  }

  @Override
  public TenantManagementResponse update(
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantIdentifier tenantIdentifier,
      TenantRequest request,
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
      log.warn(response.toString());
      return new TenantManagementResponse(TenantManagementStatus.FORBIDDEN, response);
    }

    Tenant adminTenant = tenantQueryRepository.get(adminTenantIdentifier);
    Tenant before = tenantQueryRepository.find(tenantIdentifier);

    if (!before.exists()) {
      return new TenantManagementResponse(TenantManagementStatus.NOT_FOUND, Map.of());
    }

    TenantManagementUpdateContextCreator contextCreator =
        new TenantManagementUpdateContextCreator(adminTenant, before, request, operator, dryRun);
    TenantManagementUpdateContext context = contextCreator.create();

    if (dryRun) {
      return context.toResponse();
    }

    tenantCommandRepository.update(context.after());

    return context.toResponse();
  }

  @Override
  public TenantManagementResponse delete(
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantIdentifier tenantIdentifier,
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
      log.warn(response.toString());
      return new TenantManagementResponse(TenantManagementStatus.FORBIDDEN, response);
    }

    tenantQueryRepository.get(adminTenantIdentifier);
    Tenant before = tenantQueryRepository.find(tenantIdentifier);

    if (!before.exists()) {
      return new TenantManagementResponse(TenantManagementStatus.NOT_FOUND, Map.of());
    }

    return new TenantManagementResponse(TenantManagementStatus.NO_CONTENT, Map.of());
  }
}
