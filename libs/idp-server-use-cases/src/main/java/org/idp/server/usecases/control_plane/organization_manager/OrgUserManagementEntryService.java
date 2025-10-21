/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.usecases.control_plane.organization_manager;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.base.verifier.UserVerifier;
import org.idp.server.control_plane.management.identity.user.*;
import org.idp.server.control_plane.management.identity.user.ManagementEventPublisher;
import org.idp.server.control_plane.management.identity.user.handler.*;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;
import org.idp.server.control_plane.management.identity.user.validator.*;
import org.idp.server.control_plane.management.identity.user.verifier.UserRegistrationRelatedDataVerifier;
import org.idp.server.control_plane.management.identity.user.verifier.UserRegistrationVerifier;
import org.idp.server.control_plane.organization.access.OrganizationAccessVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.UserQueries;
import org.idp.server.core.openid.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.openid.identity.event.UserLifecycleEventPublisher;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.identity.role.RoleQueryRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.SecurityEventPublisher;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level user management entry service.
 *
 * <p>This service implements organization-scoped user management operations that allow organization
 * administrators to manage users within their organization boundaries.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary
 *       DefaultAdminPermission
 * </ol>
 *
 * <p>All operations support dry-run functionality for safe preview of changes and comprehensive
 * audit logging for organization-level user operations.
 *
 * @see OrgUserManagementApi
 * @see OrganizationAccessVerifier
 * @see org.idp.server.usecases.control_plane.system_manager.UserManagementEntryService
 */
@Transaction
public class OrgUserManagementEntryService implements OrgUserManagementApi {

  AuditLogPublisher auditLogPublisher;

  LoggerWrapper log = LoggerWrapper.getLogger(OrgUserManagementEntryService.class);

  // Handler/Service pattern
  private OrgUserManagementHandler handler;

  /**
   * Creates a new organization user management entry service.
   *
   * @param tenantQueryRepository the tenant query repository
   * @param organizationRepository the organization repository
   * @param userQueryRepository the user query repository
   * @param userCommandRepository the user command repository
   * @param roleQueryRepository the role query repository
   * @param passwordEncodeDelegation the password encode delegation
   * @param userLifecycleEventPublisher the user lifecycle event publisher
   * @param auditLogPublisher the audit log publisher
   */
  public OrgUserManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      RoleQueryRepository roleQueryRepository,
      PasswordEncodeDelegation passwordEncodeDelegation,
      UserLifecycleEventPublisher userLifecycleEventPublisher,
      AuditLogPublisher auditLogPublisher,
      SecurityEventPublisher securityEventPublisher) {
    this.auditLogPublisher = auditLogPublisher;

    UserRegistrationRelatedDataVerifier relatedDataVerifier =
        new UserRegistrationRelatedDataVerifier(
            roleQueryRepository, tenantQueryRepository, organizationRepository);
    UserVerifier userVerifier = new UserVerifier(userQueryRepository);
    UserRegistrationVerifier verifier =
        new UserRegistrationVerifier(userVerifier, relatedDataVerifier);

    ManagementEventPublisher managementEventPublisher =
        new ManagementEventPublisher(securityEventPublisher);

    this.handler =
        createHandler(
            tenantQueryRepository,
            organizationRepository,
            userQueryRepository,
            userCommandRepository,
            passwordEncodeDelegation,
            verifier,
            relatedDataVerifier,
            userLifecycleEventPublisher,
            managementEventPublisher);
  }

  private OrgUserManagementHandler createHandler(
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      PasswordEncodeDelegation passwordEncodeDelegation,
      UserRegistrationVerifier verifier,
      UserRegistrationRelatedDataVerifier updateVerifier,
      UserLifecycleEventPublisher userLifecycleEventPublisher,
      ManagementEventPublisher managementEventPublisher) {

    Map<String, UserManagementService<?>> services = new HashMap<>();
    services.put(
        "create",
        new UserCreationService(
            userCommandRepository, passwordEncodeDelegation, verifier, managementEventPublisher));
    services.put(
        "update",
        new UserUpdateService(
            userQueryRepository, userCommandRepository, managementEventPublisher));
    services.put(
        "patch",
        new UserPatchService(userQueryRepository, userCommandRepository, managementEventPublisher));
    services.put(
        "updatePassword",
        new UserPasswordUpdateService(
            userQueryRepository,
            userCommandRepository,
            passwordEncodeDelegation,
            managementEventPublisher));
    services.put(
        "delete",
        new UserDeletionService(
            userQueryRepository,
            userCommandRepository,
            userLifecycleEventPublisher,
            managementEventPublisher));
    services.put("findList", new UserFindListService(userQueryRepository));
    services.put("get", new UserFindService(userQueryRepository));
    services.put(
        "updateRoles",
        new UserRolesUpdateService(userQueryRepository, userCommandRepository, updateVerifier));
    services.put(
        "updateTenantAssignments",
        new UserTenantAssignmentsUpdateService(userQueryRepository, userCommandRepository));
    services.put(
        "updateOrganizationAssignments",
        new UserOrganizationAssignmentsUpdateService(userQueryRepository, userCommandRepository));

    return new OrgUserManagementHandler(
        services, this, tenantQueryRepository, organizationRepository);
  }

  @Override
  public UserManagementResponse create(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern
    UserManagementResult result =
        handler.handle(
            "create",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            request,
            requestAttributes,
            dryRun);

    // Record audit log
    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgUserManagementApi.create",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(dryRun);
    }

    // Success case
    AuditLog auditLog =
        AuditLogCreator.create(
            "OrgUserManagementApi.create",
            result.tenant(),
            operator,
            oAuthToken,
            (UserRegistrationContext) result.context(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  @Transaction(readOnly = true)
  public UserManagementResponse findList(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserQueries queries,
      RequestAttributes requestAttributes) {

    // Delegate to Handler/Service pattern
    UserManagementResult result =
        handler.handle(
            "findList",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            queries,
            requestAttributes,
            false);

    // Record audit log
    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgUserManagementApi.findList",
            "findList",
            result.tenant(),
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (result.hasException()) {
      return result.toResponse(false);
    }

    return result.toResponse(false);
  }

  @Override
  @Transaction(readOnly = true)
  public UserManagementResponse get(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes) {

    // Delegate to Handler/Service pattern
    UserManagementResult result =
        handler.handle(
            "get",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            userIdentifier,
            requestAttributes,
            false);

    // Record audit log
    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgUserManagementApi.get",
            "get",
            result.tenant(),
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (result.hasException()) {
      return result.toResponse(false);
    }

    return result.toResponse(false);
  }

  @Override
  public UserManagementResponse update(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern
    UserUpdateRequest updateRequest = new UserUpdateRequest(userIdentifier, request);
    UserManagementResult result =
        handler.handle(
            "update",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            updateRequest,
            requestAttributes,
            dryRun);

    // Record audit log
    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgUserManagementApi.update",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(dryRun);
    }

    // Success case
    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "OrgUserManagementApi.update",
            result.tenant(),
            operator,
            oAuthToken,
            (UserUpdateContext) result.context(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public UserManagementResponse delete(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern
    UserManagementResult result =
        handler.handle(
            "delete",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            userIdentifier,
            requestAttributes,
            dryRun);

    // Record audit log
    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgUserManagementApi.delete",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(dryRun);
    }

    // Success case
    UserDeletionContext context = (UserDeletionContext) result.context();
    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "OrgUserManagementApi.delete",
            "delete",
            result.tenant(),
            operator,
            oAuthToken,
            context.beforePayload(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public UserManagementResponse patch(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern
    UserUpdateRequest updateRequest = new UserUpdateRequest(userIdentifier, request);
    UserManagementResult result =
        handler.handle(
            "patch",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            updateRequest,
            requestAttributes,
            dryRun);

    // Record audit log
    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgUserManagementApi.patch",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(dryRun);
    }

    // Success case
    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "OrgUserManagementApi.patch",
            result.tenant(),
            operator,
            oAuthToken,
            (UserUpdateContext) result.context(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public UserManagementResponse updatePassword(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern
    UserUpdateRequest updateRequest = new UserUpdateRequest(userIdentifier, request);
    UserManagementResult result =
        handler.handle(
            "updatePassword",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            updateRequest,
            requestAttributes,
            dryRun);

    // Record audit log
    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgUserManagementApi.updatePassword",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(dryRun);
    }

    // Success case
    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "OrgUserManagementApi.updatePassword",
            result.tenant(),
            operator,
            oAuthToken,
            (UserUpdateContext) result.context(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public UserManagementResponse updateRoles(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern
    UserUpdateRequest updateRequest = new UserUpdateRequest(userIdentifier, request);
    UserManagementResult result =
        handler.handle(
            "updateRoles",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            updateRequest,
            requestAttributes,
            dryRun);

    // Record audit log
    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgUserManagementApi.updateRoles",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(dryRun);
    }

    // Success case
    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "OrgUserManagementApi.updateRoles",
            result.tenant(),
            operator,
            oAuthToken,
            (UserUpdateContext) result.context(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public UserManagementResponse updateTenantAssignments(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern
    UserUpdateRequest updateRequest = new UserUpdateRequest(userIdentifier, request);
    UserManagementResult result =
        handler.handle(
            "updateTenantAssignments",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            updateRequest,
            requestAttributes,
            dryRun);

    // Record audit log
    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgUserManagementApi.updateTenantAssignments",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(dryRun);
    }

    // Success case
    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "OrgUserManagementApi.updateTenantAssignments",
            result.tenant(),
            operator,
            oAuthToken,
            (UserUpdateContext) result.context(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public UserManagementResponse updateOrganizationAssignments(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern
    UserUpdateRequest updateRequest = new UserUpdateRequest(userIdentifier, request);
    UserManagementResult result =
        handler.handle(
            "updateOrganizationAssignments",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            updateRequest,
            requestAttributes,
            dryRun);

    // Record audit log
    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgUserManagementApi.updateOrganizationAssignments",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(dryRun);
    }

    // Success case
    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "OrgUserManagementApi.updateOrganizationAssignments",
            result.tenant(),
            operator,
            oAuthToken,
            (UserUpdateContext) result.context(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }
}
