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

package org.idp.server.usecases.control_plane.tenant_manager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.base.AdminDashboardUrl;
import org.idp.server.control_plane.management.tenant.invitation.TenantInvitationContext;
import org.idp.server.control_plane.management.tenant.invitation.TenantInvitationContextCreator;
import org.idp.server.control_plane.management.tenant.invitation.TenantInvitationManagementApi;
import org.idp.server.control_plane.management.tenant.invitation.io.TenantInvitationManagementRequest;
import org.idp.server.control_plane.management.tenant.invitation.io.TenantInvitationManagementResponse;
import org.idp.server.control_plane.management.tenant.invitation.io.TenantInvitationManagementStatus;
import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitation;
import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitationCommandRepository;
import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitationIdentifier;
import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitationQueryRepository;
import org.idp.server.control_plane.management.tenant.invitation.validator.OrganizationInvitationRequestValidationResult;
import org.idp.server.control_plane.management.tenant.invitation.validator.OrganizationInvitationRequestValidator;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.notification.email.EmailSenders;
import org.idp.server.platform.security.type.RequestAttributes;

@Transaction
public class TenantInvitationManagementEntryService implements TenantInvitationManagementApi {

  TenantInvitationCommandRepository tenantInvitationCommandRepository;
  TenantInvitationQueryRepository tenantInvitationQueryRepository;
  TenantQueryRepository tenantQueryRepository;
  EmailSenders emailSenders;
  AdminDashboardUrl adminDashboardUrl;

  public TenantInvitationManagementEntryService(
      TenantInvitationCommandRepository tenantInvitationCommandRepository,
      TenantInvitationQueryRepository tenantInvitationQueryRepository,
      TenantQueryRepository tenantQueryRepository,
      EmailSenders emailSenders,
      AdminDashboardUrl adminDashboardUrl) {
    this.tenantInvitationCommandRepository = tenantInvitationCommandRepository;
    this.tenantInvitationQueryRepository = tenantInvitationQueryRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.emailSenders = emailSenders;
    this.adminDashboardUrl = adminDashboardUrl;
  }

  @Override
  public TenantInvitationManagementResponse create(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantInvitationManagementRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    OrganizationInvitationRequestValidator validator =
        new OrganizationInvitationRequestValidator(request, dryRun);
    OrganizationInvitationRequestValidationResult validate = validator.validate();
    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    TenantInvitationContextCreator contextCreator =
        new TenantInvitationContextCreator(tenant, request, adminDashboardUrl, dryRun);
    TenantInvitationContext context = contextCreator.create();

    if (dryRun) {
      return context.toResponse();
    }

    tenantInvitationCommandRepository.register(tenant, context.tenantInvitation());

    // TODO send email

    return context.toResponse();
  }

  @Transaction(readOnly = true)
  @Override
  public TenantInvitationManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    List<TenantInvitation> tenantInvitations =
        tenantInvitationQueryRepository.findList(tenant, limit, offset);
    Map<String, Object> response = new HashMap<>();
    response.put("list", tenantInvitations.stream().map(TenantInvitation::toMap).toList());

    return new TenantInvitationManagementResponse(TenantInvitationManagementStatus.OK, response);
  }

  @Transaction(readOnly = true)
  @Override
  public TenantInvitationManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantInvitationIdentifier identifier,
      RequestAttributes requestAttributes) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    TenantInvitation tenantInvitation = tenantInvitationQueryRepository.find(tenant, identifier);

    if (!tenantInvitation.exists()) {
      return new TenantInvitationManagementResponse(
          TenantInvitationManagementStatus.NOT_FOUND, Map.of());
    }

    return new TenantInvitationManagementResponse(
        TenantInvitationManagementStatus.OK, tenantInvitation.toMap());
  }

  @Override
  public TenantInvitationManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantInvitationIdentifier identifier,
      TenantInvitationManagementRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    OrganizationInvitationRequestValidator validator =
        new OrganizationInvitationRequestValidator(request, dryRun);
    OrganizationInvitationRequestValidationResult validate = validator.validate();
    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    TenantInvitationContextCreator contextCreator =
        new TenantInvitationContextCreator(tenant, request, adminDashboardUrl, dryRun);
    TenantInvitationContext context = contextCreator.create();

    if (dryRun) {
      return context.toResponse();
    }

    tenantInvitationCommandRepository.update(tenant, context.tenantInvitation());

    return context.toResponse();
  }

  @Override
  public TenantInvitationManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantInvitationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    TenantInvitation tenantInvitation = tenantInvitationQueryRepository.find(tenant, identifier);
    if (!tenantInvitation.exists()) {
      return new TenantInvitationManagementResponse(
          TenantInvitationManagementStatus.NOT_FOUND, Map.of());
    }

    Map<String, Object> response = new HashMap<>();
    response.put("dry_run", dryRun);
    if (dryRun) {
      return new TenantInvitationManagementResponse(TenantInvitationManagementStatus.OK, response);
    }

    tenantInvitationCommandRepository.delete(tenant, tenantInvitation);

    return new TenantInvitationManagementResponse(TenantInvitationManagementStatus.OK, response);
  }
}
