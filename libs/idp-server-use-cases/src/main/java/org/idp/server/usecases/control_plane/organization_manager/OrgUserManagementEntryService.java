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
import org.idp.server.control_plane.base.OrganizationAccessVerifier;
import org.idp.server.control_plane.base.OrganizationAuthenticationContext;
import org.idp.server.control_plane.base.verifier.UserVerifier;
import org.idp.server.control_plane.management.identity.user.*;
import org.idp.server.control_plane.management.identity.user.ManagementEventPublisher;
import org.idp.server.control_plane.management.identity.user.handler.*;
import org.idp.server.control_plane.management.identity.user.io.*;
import org.idp.server.control_plane.management.identity.user.verifier.UserRegistrationRelatedDataVerifier;
import org.idp.server.control_plane.management.identity.user.verifier.UserRegistrationVerifier;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.UserQueries;
import org.idp.server.core.openid.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.openid.identity.event.UserLifecycleEventPublisher;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.identity.role.RoleQueryRepository;
import org.idp.server.core.openid.session.OPSessionIdentifier;
import org.idp.server.core.openid.session.repository.OPSessionRepository;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
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
      OPSessionRepository opSessionRepository,
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
            userQueryRepository,
            userCommandRepository,
            roleQueryRepository,
            opSessionRepository,
            passwordEncodeDelegation,
            verifier,
            relatedDataVerifier,
            userLifecycleEventPublisher,
            managementEventPublisher);
  }

  private OrgUserManagementHandler createHandler(
      TenantQueryRepository tenantQueryRepository,
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      RoleQueryRepository roleQueryRepository,
      OPSessionRepository opSessionRepository,
      PasswordEncodeDelegation passwordEncodeDelegation,
      UserRegistrationVerifier verifier,
      UserRegistrationRelatedDataVerifier relatedDataVerifier,
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
        new UserRolesUpdateService(
            userQueryRepository, userCommandRepository, roleQueryRepository, relatedDataVerifier));
    services.put(
        "updateTenantAssignments",
        new UserTenantAssignmentsUpdateService(
            userQueryRepository, userCommandRepository, relatedDataVerifier));
    services.put(
        "updateOrganizationAssignments",
        new UserOrganizationAssignmentsUpdateService(
            userQueryRepository, userCommandRepository, relatedDataVerifier));
    services.put("findSessions", new UserSessionsFindService(opSessionRepository));
    services.put("deleteSession", new UserSessionDeleteService(opSessionRepository));
    services.put("deleteSessions", new UserSessionsDeleteService(opSessionRepository));

    return new OrgUserManagementHandler(
        services, this, tenantQueryRepository, new OrganizationAccessVerifier());
  }

  @Override
  public UserManagementResponse create(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern
    UserManagementResult result =
        handler.handle(
            "create", authenticationContext, tenantIdentifier, request, requestAttributes, dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  @Transaction(readOnly = true)
  public UserManagementResponse findList(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserQueries queries,
      RequestAttributes requestAttributes) {

    // Delegate to Handler/Service pattern
    UserManagementResult result =
        handler.handle(
            "findList",
            authenticationContext,
            tenantIdentifier,
            new UserFindListRequest(queries),
            requestAttributes,
            false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  @Transaction(readOnly = true)
  public UserManagementResponse get(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes) {

    // Delegate to Handler/Service pattern
    UserManagementResult result =
        handler.handle(
            "get",
            authenticationContext,
            tenantIdentifier,
            new UserFindRequest(userIdentifier),
            requestAttributes,
            false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  public UserManagementResponse update(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern
    UserUpdateRequest updateRequest = new UserUpdateRequest(userIdentifier, request);
    UserManagementResult result =
        handler.handle(
            "update",
            authenticationContext,
            tenantIdentifier,
            updateRequest,
            requestAttributes,
            dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public UserManagementResponse delete(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern
    UserManagementResult result =
        handler.handle(
            "delete",
            authenticationContext,
            tenantIdentifier,
            new UserDeleteRequest(userIdentifier),
            requestAttributes,
            dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public UserManagementResponse patch(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern
    UserUpdateRequest updateRequest = new UserUpdateRequest(userIdentifier, request);
    UserManagementResult result =
        handler.handle(
            "patch",
            authenticationContext,
            tenantIdentifier,
            updateRequest,
            requestAttributes,
            dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public UserManagementResponse updatePassword(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern
    UserUpdateRequest updateRequest = new UserUpdateRequest(userIdentifier, request);
    UserManagementResult result =
        handler.handle(
            "updatePassword",
            authenticationContext,
            tenantIdentifier,
            updateRequest,
            requestAttributes,
            dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public UserManagementResponse updateRoles(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern
    UserUpdateRequest updateRequest = new UserUpdateRequest(userIdentifier, request);
    UserManagementResult result =
        handler.handle(
            "updateRoles",
            authenticationContext,
            tenantIdentifier,
            updateRequest,
            requestAttributes,
            dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public UserManagementResponse updateTenantAssignments(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern
    UserUpdateRequest updateRequest = new UserUpdateRequest(userIdentifier, request);
    UserManagementResult result =
        handler.handle(
            "updateTenantAssignments",
            authenticationContext,
            tenantIdentifier,
            updateRequest,
            requestAttributes,
            dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public UserManagementResponse updateOrganizationAssignments(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern
    UserUpdateRequest updateRequest = new UserUpdateRequest(userIdentifier, request);
    UserManagementResult result =
        handler.handle(
            "updateOrganizationAssignments",
            authenticationContext,
            tenantIdentifier,
            updateRequest,
            requestAttributes,
            dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  @Transaction(readOnly = true)
  public UserManagementResponse findSessions(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes) {

    UserManagementResult result =
        handler.handle(
            "findSessions",
            authenticationContext,
            tenantIdentifier,
            new UserSessionsFindRequest(userIdentifier),
            requestAttributes,
            false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  public UserManagementResponse deleteSession(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      OPSessionIdentifier sessionIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    UserManagementResult result =
        handler.handle(
            "deleteSession",
            authenticationContext,
            tenantIdentifier,
            new UserSessionDeleteRequest(userIdentifier, sessionIdentifier),
            requestAttributes,
            dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public UserManagementResponse deleteSessions(
      OrganizationAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    UserManagementResult result =
        handler.handle(
            "deleteSessions",
            authenticationContext,
            tenantIdentifier,
            new UserSessionsDeleteRequest(userIdentifier),
            requestAttributes,
            dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }
}
