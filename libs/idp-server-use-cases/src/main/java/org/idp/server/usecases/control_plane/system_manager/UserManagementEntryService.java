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

package org.idp.server.usecases.control_plane.system_manager;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AdminAuthenticationContext;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.base.verifier.UserVerifier;
import org.idp.server.control_plane.management.identity.user.*;
import org.idp.server.control_plane.management.identity.user.ManagementEventPublisher;
import org.idp.server.control_plane.management.identity.user.handler.*;
import org.idp.server.control_plane.management.identity.user.handler.UserCreationService;
import org.idp.server.control_plane.management.identity.user.handler.UserDeletionService;
import org.idp.server.control_plane.management.identity.user.handler.UserFindListService;
import org.idp.server.control_plane.management.identity.user.handler.UserFindService;
import org.idp.server.control_plane.management.identity.user.handler.UserManagementHandler;
import org.idp.server.control_plane.management.identity.user.handler.UserManagementResult;
import org.idp.server.control_plane.management.identity.user.handler.UserManagementService;
import org.idp.server.control_plane.management.identity.user.handler.UserPasswordUpdateService;
import org.idp.server.control_plane.management.identity.user.handler.UserPatchService;
import org.idp.server.control_plane.management.identity.user.handler.UserUpdateService;
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
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.SecurityEventPublisher;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class UserManagementEntryService implements UserManagementApi {

  AuditLogPublisher auditLogPublisher;

  LoggerWrapper log = LoggerWrapper.getLogger(UserManagementEntryService.class);

  // Handler/Service pattern
  private UserManagementHandler handler;

  public UserManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      RoleQueryRepository roleQueryRepository,
      OrganizationRepository organizationRepository,
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
            organizationRepository,
            passwordEncodeDelegation,
            verifier,
            userLifecycleEventPublisher,
            managementEventPublisher);
  }

  private UserManagementHandler createHandler(
      TenantQueryRepository tenantQueryRepository,
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      RoleQueryRepository roleQueryRepository,
      OrganizationRepository organizationRepository,
      PasswordEncodeDelegation passwordEncodeDelegation,
      UserRegistrationVerifier verifier,
      UserLifecycleEventPublisher userLifecycleEventPublisher,
      ManagementEventPublisher managementEventPublisher) {

    UserRegistrationRelatedDataVerifier updateVerifier =
        new UserRegistrationRelatedDataVerifier(
            roleQueryRepository, tenantQueryRepository, organizationRepository);

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

    return new UserManagementHandler(services, this, tenantQueryRepository);
  }

  @Override
  public UserManagementResponse create(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

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
      AdminAuthenticationContext authenticationContext,
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
      AdminAuthenticationContext authenticationContext,
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
      AdminAuthenticationContext authenticationContext,
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
  public UserManagementResponse patch(
      AdminAuthenticationContext authenticationContext,
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
      AdminAuthenticationContext authenticationContext,
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
  public UserManagementResponse delete(
      AdminAuthenticationContext authenticationContext,
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
  public UserManagementResponse updateRoles(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

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
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

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
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      UserIdentifier userIdentifier,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

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
}
