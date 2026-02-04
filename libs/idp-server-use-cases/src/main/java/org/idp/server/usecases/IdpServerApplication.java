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

package org.idp.server.usecases;

import java.net.http.HttpClient;
import java.util.Map;
import org.idp.server.authentication.interactors.device.AuthenticationDeviceNotifiers;
import org.idp.server.authentication.interactors.fidouaf.AuthenticationMetaDataApi;
import org.idp.server.authentication.interactors.fidouaf.plugin.FidoUafAdditionalRequestResolvers;
import org.idp.server.authentication.interactors.plugin.AuthenticationDeviceNotifiersPluginLoader;
import org.idp.server.authentication.interactors.plugin.FidoUafAdditionalRequestResolverPluginLoader;
import org.idp.server.authenticators.webauthn4j.WebAuthn4jCredentialRepository;
import org.idp.server.authenticators.webauthn4j.mds.MdsConfiguration;
import org.idp.server.authenticators.webauthn4j.mds.MdsResolver;
import org.idp.server.authenticators.webauthn4j.mds.MdsResolverFactory;
import org.idp.server.control_plane.admin.operation.IdpServerOperationApi;
import org.idp.server.control_plane.admin.starter.IdpServerStarterApi;
import org.idp.server.control_plane.base.AdminUserAuthenticationApi;
import org.idp.server.control_plane.base.OrganizationUserAuthenticationApi;
import org.idp.server.control_plane.base.schema.ControlPlaneV1SchemaReader;
import org.idp.server.control_plane.management.audit.AuditLogManagementApi;
import org.idp.server.control_plane.management.audit.OrgAuditLogManagementApi;
import org.idp.server.control_plane.management.authentication.configuration.AuthenticationConfigurationManagementApi;
import org.idp.server.control_plane.management.authentication.configuration.OrgAuthenticationConfigManagementApi;
import org.idp.server.control_plane.management.authentication.interaction.AuthenticationInteractionManagementApi;
import org.idp.server.control_plane.management.authentication.interaction.OrgAuthenticationInteractionManagementApi;
import org.idp.server.control_plane.management.authentication.policy.AuthenticationPolicyConfigurationManagementApi;
import org.idp.server.control_plane.management.authentication.policy.OrgAuthenticationPolicyConfigManagementApi;
import org.idp.server.control_plane.management.authentication.transaction.AuthenticationTransactionManagementApi;
import org.idp.server.control_plane.management.authentication.transaction.OrgAuthenticationTransactionManagementApi;
import org.idp.server.control_plane.management.federation.FederationConfigurationManagementApi;
import org.idp.server.control_plane.management.federation.OrgFederationConfigManagementApi;
import org.idp.server.control_plane.management.identity.user.OrgUserManagementApi;
import org.idp.server.control_plane.management.identity.user.UserManagementApi;
import org.idp.server.control_plane.management.identity.verification.IdentityVerificationConfigManagementApi;
import org.idp.server.control_plane.management.identity.verification.OrgIdentityVerificationConfigManagementApi;
import org.idp.server.control_plane.management.oidc.authorization.AuthorizationServerManagementApi;
import org.idp.server.control_plane.management.oidc.authorization.OrgAuthorizationServerManagementApi;
import org.idp.server.control_plane.management.oidc.client.ClientManagementApi;
import org.idp.server.control_plane.management.oidc.client.OrgClientManagementApi;
import org.idp.server.control_plane.management.oidc.grant.OrgGrantManagementApi;
import org.idp.server.control_plane.management.onboarding.OnboardingApi;
import org.idp.server.control_plane.management.organization.OrganizationManagementApi;
import org.idp.server.control_plane.management.permission.OrgPermissionManagementApi;
import org.idp.server.control_plane.management.permission.PermissionManagementApi;
import org.idp.server.control_plane.management.role.OrgRoleManagementApi;
import org.idp.server.control_plane.management.role.RoleManagementApi;
import org.idp.server.control_plane.management.security.event.OrgSecurityEventManagementApi;
import org.idp.server.control_plane.management.security.event.SecurityEventManagementApi;
import org.idp.server.control_plane.management.security.hook.OrgSecurityEventHookConfigManagementApi;
import org.idp.server.control_plane.management.security.hook.SecurityEventHookConfigurationManagementApi;
import org.idp.server.control_plane.management.security.hook_result.OrgSecurityEventHookManagementApi;
import org.idp.server.control_plane.management.security.hook_result.SecurityEventHookManagementApi;
import org.idp.server.control_plane.management.statistics.OrgTenantStatisticsApi;
import org.idp.server.control_plane.management.statistics.TenantStatisticsApi;
import org.idp.server.control_plane.management.system.SystemConfigurationManagementApi;
import org.idp.server.control_plane.management.tenant.OrgTenantManagementApi;
import org.idp.server.control_plane.management.tenant.TenantManagementApi;
import org.idp.server.control_plane.management.tenant.invitation.TenantInvitationManagementApi;
import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitationCommandRepository;
import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitationMetaDataApi;
import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitationQueryRepository;
import org.idp.server.core.extension.ciba.CibaFlowApi;
import org.idp.server.core.extension.ciba.CibaFlowEventPublisher;
import org.idp.server.core.extension.ciba.CibaProtocol;
import org.idp.server.core.extension.ciba.CibaProtocols;
import org.idp.server.core.extension.ciba.repository.BackchannelAuthenticationRequestOperationCommandRepository;
import org.idp.server.core.extension.ciba.repository.CibaGrantOperationCommandRepository;
import org.idp.server.core.extension.identity.plugin.IdentityVerificationRequestAdditionalParameterPluginLoader;
import org.idp.server.core.extension.identity.verification.IdentityVerificationApi;
import org.idp.server.core.extension.identity.verification.IdentityVerificationApplicationApi;
import org.idp.server.core.extension.identity.verification.IdentityVerificationCallbackApi;
import org.idp.server.core.extension.identity.verification.application.pre_hook.additional_parameter.AdditionalRequestParameterResolver;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationApplicationCommandRepository;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationApplicationQueryRepository;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationConfigurationCommandRepository;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationConfigurationQueryRepository;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationResultCommandRepository;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationResultQueryRepository;
import org.idp.server.core.openid.authentication.AuthenticationInteractors;
import org.idp.server.core.openid.authentication.AuthenticationTransactionApi;
import org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutors;
import org.idp.server.core.openid.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.core.openid.authentication.repository.*;
import org.idp.server.core.openid.discovery.*;
import org.idp.server.core.openid.federation.FederationInteractors;
import org.idp.server.core.openid.federation.plugin.FederationDependencyContainer;
import org.idp.server.core.openid.federation.repository.FederationConfigurationCommandRepository;
import org.idp.server.core.openid.federation.repository.FederationConfigurationQueryRepository;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedQueryRepository;
import org.idp.server.core.openid.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.openid.identity.*;
import org.idp.server.core.openid.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.openid.identity.authentication.PasswordVerificationDelegation;
import org.idp.server.core.openid.identity.authentication.UserPasswordAuthenticator;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceLogApi;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceLogEventPublisher;
import org.idp.server.core.openid.identity.event.*;
import org.idp.server.core.openid.identity.permission.PermissionCommandRepository;
import org.idp.server.core.openid.identity.permission.PermissionQueryRepository;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.identity.role.RoleCommandRepository;
import org.idp.server.core.openid.identity.role.RoleQueryRepository;
import org.idp.server.core.openid.oauth.*;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationCommandRepository;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationCommandRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.repository.AuthorizationCodeGrantOperationCommandRepository;
import org.idp.server.core.openid.oauth.repository.AuthorizationRequestOperationCommandRepository;
import org.idp.server.core.openid.plugin.AuthenticationDependencyContainerPluginLoader;
import org.idp.server.core.openid.plugin.FederationDependencyContainerPluginLoader;
import org.idp.server.core.openid.plugin.UserLifecycleEventExecutorPluginLoader;
import org.idp.server.core.openid.plugin.authentication.AuthenticationExecutorPluginLoader;
import org.idp.server.core.openid.plugin.authentication.AuthenticationInteractorPluginLoader;
import org.idp.server.core.openid.plugin.authentication.FederationInteractorPluginLoader;
import org.idp.server.core.openid.session.AuthSessionCookieDelegate;
import org.idp.server.core.openid.session.OIDCSessionHandler;
import org.idp.server.core.openid.session.OIDCSessionService;
import org.idp.server.core.openid.session.SessionCookieDelegate;
import org.idp.server.core.openid.session.repository.ClientSessionRepository;
import org.idp.server.core.openid.session.repository.OPSessionRepository;
import org.idp.server.core.openid.token.*;
import org.idp.server.core.openid.token.JwtBearerUserFinder;
import org.idp.server.core.openid.token.JwtBearerUserFindingDelegate;
import org.idp.server.core.openid.token.repository.OAuthTokenCommandRepository;
import org.idp.server.core.openid.token.repository.OAuthTokenOperationCommandRepository;
import org.idp.server.core.openid.token.repository.OAuthTokenQueryRepository;
import org.idp.server.core.openid.userinfo.UserinfoApi;
import org.idp.server.core.openid.userinfo.UserinfoProtocol;
import org.idp.server.core.openid.userinfo.UserinfoProtocols;
import org.idp.server.federation.sso.oidc.OidcSsoExecutorPluginLoader;
import org.idp.server.federation.sso.oidc.OidcSsoExecutors;
import org.idp.server.platform.audit.AuditLogApi;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.audit.AuditLogQueryRepository;
import org.idp.server.platform.audit.AuditLogWriters;
import org.idp.server.platform.crypto.AesCipher;
import org.idp.server.platform.crypto.HmacHasher;
import org.idp.server.platform.datasource.*;
import org.idp.server.platform.datasource.DatabaseTypeConfiguration;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.datasource.session.SessionStore;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.date.TimeConfig;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.protocol.ProtocolContainer;
import org.idp.server.platform.health.HealthCheckApi;
import org.idp.server.platform.http.HttpClientFactory;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.http.SsrfProtectedHttpClient;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.organization.OrganizationTenantResolverApi;
import org.idp.server.platform.multi_tenancy.tenant.*;
import org.idp.server.platform.notification.email.EmailSenders;
import org.idp.server.platform.notification.sms.SmsSenders;
import org.idp.server.platform.oauth.OAuthAuthorizationResolvers;
import org.idp.server.platform.plugin.*;
import org.idp.server.platform.security.SecurityEventApi;
import org.idp.server.platform.security.SecurityEventPublisher;
import org.idp.server.platform.security.hook.SecurityEventHooks;
import org.idp.server.platform.security.repository.*;
import org.idp.server.platform.statistics.repository.DailyActiveUserCommandRepository;
import org.idp.server.platform.statistics.repository.MonthlyActiveUserCommandRepository;
import org.idp.server.platform.statistics.repository.StatisticsEventsCommandRepository;
import org.idp.server.platform.statistics.repository.TenantStatisticsQueryRepository;
import org.idp.server.platform.statistics.repository.TenantYearlyStatisticsQueryRepository;
import org.idp.server.platform.statistics.repository.YearlyActiveUserCommandRepository;
import org.idp.server.platform.system.CachedSystemConfigurationResolver;
import org.idp.server.platform.system.SystemConfigurationApi;
import org.idp.server.platform.system.SystemConfigurationRepository;
import org.idp.server.platform.system.SystemConfigurationResolver;
import org.idp.server.security.event.hook.ssf.SharedSignalsFrameworkMetaDataApi;
import org.idp.server.usecases.application.enduser.*;
import org.idp.server.usecases.application.enduser.AuthenticationDeviceLogEntryService;
import org.idp.server.usecases.application.identity_verification_service.IdentityVerificationCallbackEntryService;
import org.idp.server.usecases.application.identity_verification_service.IdentityVerificationEntryService;
import org.idp.server.usecases.application.relying_party.OidcMetaDataEntryService;
import org.idp.server.usecases.application.relying_party.SharedSignalsFrameworkMetaDataEntryService;
import org.idp.server.usecases.application.system.*;
import org.idp.server.usecases.application.tenant_invitator.TenantInvitationMetaDataEntryService;
import org.idp.server.usecases.control_plane.organization_manager.*;
import org.idp.server.usecases.control_plane.system_administrator.IdpServerOperationEntryService;
import org.idp.server.usecases.control_plane.system_administrator.IdpServerStarterEntryService;
import org.idp.server.usecases.control_plane.system_administrator.SystemConfigurationManagementEntryService;
import org.idp.server.usecases.control_plane.system_manager.*;

/** IdpServerApplication */
public class IdpServerApplication {

  IdpServerStarterApi idpServerStarterApi;
  IdpServerOperationApi idpServerOperationApi;
  OAuthFlowApi oAuthFlowApi;
  TokenApi tokenApi;
  OidcMetaDataApi oidcMetaDataApi;
  UserinfoApi userinfoApi;
  CibaFlowApi cibaFlowApi;
  AuthenticationMetaDataApi authenticationMetaDataApi;
  AuthenticationTransactionApi authenticationTransactionApi;
  IdentityVerificationApplicationApi identityVerificationApplicationApi;
  IdentityVerificationCallbackApi identityVerificationCallbackApi;
  IdentityVerificationApi identityVerificationApi;
  SecurityEventApi securityEventApi;
  AuditLogApi auditLogApi;
  SharedSignalsFrameworkMetaDataApi sharedSignalsFrameworkMetaDataApi;
  TenantMetaDataApi tenantMetaDataApi;
  OrganizationTenantResolverApi organizationTenantResolverApi;
  TenantInvitationMetaDataApi tenantInvitationMetaDataApi;
  UserOperationApi userOperationApi;
  UserLifecycleEventApi userLifecycleEventApi;
  AuthenticationDeviceLogApi authenticationDeviceLogApi;
  OnboardingApi onboardingApi;
  TenantManagementApi tenantManagementApi;
  TenantStatisticsApi tenantStatisticsApi;
  OrganizationManagementApi organizationManagementApi;

  HealthCheckApi healthCheckApi;

  TenantInvitationManagementApi tenantInvitationManagementApi;
  AuthorizationServerManagementApi authorizationServerManagementApi;
  ClientManagementApi clientManagementApi;
  UserManagementApi userManagementApi;
  AuthenticationConfigurationManagementApi authenticationConfigurationManagementApi;
  AuthenticationPolicyConfigurationManagementApi authenticationPolicyConfigurationManagementApi;
  FederationConfigurationManagementApi federationConfigurationManagementApi;
  IdentityVerificationConfigManagementApi identityVerificationConfigManagementApi;
  SecurityEventHookConfigurationManagementApi securityEventHookConfigurationManagementApi;
  SecurityEventManagementApi securityEventManagementApi;
  SecurityEventHookManagementApi securityEventHookManagementApi;
  AuditLogManagementApi auditLogManagementApi;
  AuthenticationInteractionManagementApi authenticationInteractionManagementApi;
  AuthenticationTransactionManagementApi authenticationTransactionManagementApi;
  PermissionManagementApi permissionManagementApi;
  RoleManagementApi roleManagementApi;
  UserAuthenticationApi userAuthenticationApi;
  SystemConfigurationManagementApi systemConfigurationManagementApi;
  SystemConfigurationApi systemConfigurationApi;

  AdminUserAuthenticationApi adminUserAuthenticationApi;

  OrgTenantManagementApi orgTenantManagementApi;
  OrgTenantStatisticsApi orgTenantStatisticsApi;
  OrgClientManagementApi orgClientManagementApi;
  OrgUserManagementApi orgUserManagementApi;
  OrgAuthenticationConfigManagementApi orgAuthenticationConfigManagementApi;
  OrgAuthenticationPolicyConfigManagementApi orgAuthenticationPolicyConfigManagementApi;
  OrgIdentityVerificationConfigManagementApi orgIdentityVerificationConfigManagementApi;
  OrgFederationConfigManagementApi orgFederationConfigManagementApi;
  OrgSecurityEventHookConfigManagementApi orgSecurityEventHookConfigManagementApi;
  OrgAuthenticationInteractionManagementApi orgAuthenticationInteractionManagementApi;
  OrgAuthenticationTransactionManagementApi orgAuthenticationTransactionManagementApi;
  OrgAuthorizationServerManagementApi orgAuthorizationServerManagementApi;
  OrgPermissionManagementApi orgPermissionManagementApi;
  OrgRoleManagementApi orgRoleManagementApi;
  OrgSecurityEventManagementApi orgSecurityEventManagementApi;
  OrgAuditLogManagementApi orgAuditLogManagementApi;
  OrganizationUserAuthenticationApi organizationUserAuthenticationApi;
  OrgSecurityEventHookManagementApi orgSecurityEventHookManagementApi;
  OrgGrantManagementApi orgGrantManagementApi;

  public IdpServerApplication(
      String adminTenantId,
      ApplicationDatabaseTypeProvider databaseTypeProvider,
      DbConnectionProvider dbConnectionProvider,
      String encryptionKey,
      String databaseType,
      CacheStore cacheStore,
      SessionStore sessionStore,
      SessionCookieDelegate sessionCookieDelegate,
      AuthSessionCookieDelegate authSessionCookieDelegate,
      PasswordEncodeDelegation passwordEncodeDelegation,
      PasswordVerificationDelegation passwordVerificationDelegation,
      SecurityEventPublisher securityEventPublisher,
      AuditLogPublisher auditLogPublisher,
      UserLifecycleEventPublisher userLifecycleEventPublisher,
      TimeConfig timeConfig) {

    AdminTenantContext.configure(adminTenantId);
    TransactionManager.configure(dbConnectionProvider);
    SystemDateTime.configure(java.time.ZoneId.of(timeConfig.zone()));

    ApplicationComponentDependencyContainer dependencyContainer =
        new ApplicationComponentDependencyContainer();
    dependencyContainer.register(ApplicationDatabaseTypeProvider.class, databaseTypeProvider);
    AesCipher aesCipher = new AesCipher(encryptionKey);
    HmacHasher hmacHasher = new HmacHasher(encryptionKey);
    dependencyContainer.register(AesCipher.class, aesCipher);
    dependencyContainer.register(HmacHasher.class, hmacHasher);
    dependencyContainer.register(CacheStore.class, cacheStore);
    dependencyContainer.register(SessionStore.class, sessionStore);
    DatabaseTypeConfiguration databaseTypeConfig = new DatabaseTypeConfiguration(databaseType);
    dependencyContainer.register(DatabaseTypeConfiguration.class, databaseTypeConfig);
    ApplicationComponentContainer applicationComponentContainer =
        ApplicationComponentContainerPluginLoader.load(dependencyContainer);
    applicationComponentContainer.register(SessionCookieDelegate.class, sessionCookieDelegate);
    applicationComponentContainer.register(
        AuthSessionCookieDelegate.class, authSessionCookieDelegate);

    AuthorizationServerConfigurationCommandRepository
        authorizationServerConfigurationCommandRepository =
            applicationComponentContainer.resolve(
                AuthorizationServerConfigurationCommandRepository.class);
    AuthorizationServerConfigurationQueryRepository
        authorizationServerConfigurationQueryRepository =
            applicationComponentContainer.resolve(
                AuthorizationServerConfigurationQueryRepository.class);
    ClientConfigurationCommandRepository clientConfigurationCommandRepository =
        applicationComponentContainer.resolve(ClientConfigurationCommandRepository.class);
    ClientConfigurationQueryRepository clientConfigurationQueryRepository =
        applicationComponentContainer.resolve(ClientConfigurationQueryRepository.class);
    SecurityEventCommandRepository securityEventCommandRepository =
        applicationComponentContainer.resolve(SecurityEventCommandRepository.class);
    SecurityEventHookResultCommandRepository securityEventHookResultCommandRepository =
        applicationComponentContainer.resolve(SecurityEventHookResultCommandRepository.class);
    UserCommandRepository userCommandRepository =
        applicationComponentContainer.resolve(UserCommandRepository.class);
    UserQueryRepository userQueryRepository =
        applicationComponentContainer.resolve(UserQueryRepository.class);
    OrganizationRepository organizationRepository =
        applicationComponentContainer.resolve(OrganizationRepository.class);
    TenantQueryRepository tenantQueryRepository =
        applicationComponentContainer.resolve(TenantQueryRepository.class);
    TenantCommandRepository tenantCommandRepository =
        applicationComponentContainer.resolve(TenantCommandRepository.class);
    AuthenticationConfigurationCommandRepository authenticationConfigurationCommandRepository =
        applicationComponentContainer.resolve(AuthenticationConfigurationCommandRepository.class);
    AuthenticationConfigurationQueryRepository authenticationConfigurationQueryRepository =
        applicationComponentContainer.resolve(AuthenticationConfigurationQueryRepository.class);
    AuthenticationTransactionCommandRepository authenticationTransactionCommandRepository =
        applicationComponentContainer.resolve(AuthenticationTransactionCommandRepository.class);
    AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository =
        applicationComponentContainer.resolve(AuthenticationTransactionQueryRepository.class);
    AuthenticationPolicyConfigurationCommandRepository
        authenticationPolicyConfigurationCommandRepository =
            applicationComponentContainer.resolve(
                AuthenticationPolicyConfigurationCommandRepository.class);
    AuthenticationPolicyConfigurationQueryRepository
        authenticationPolicyConfigurationQueryRepository =
            applicationComponentContainer.resolve(
                AuthenticationPolicyConfigurationQueryRepository.class);
    IdentityVerificationConfigurationCommandRepository
        identityVerificationConfigurationCommandRepository =
            applicationComponentContainer.resolve(
                IdentityVerificationConfigurationCommandRepository.class);
    IdentityVerificationConfigurationQueryRepository
        identityVerificationConfigurationQueryRepository =
            applicationComponentContainer.resolve(
                IdentityVerificationConfigurationQueryRepository.class);
    IdentityVerificationApplicationCommandRepository
        identityVerificationApplicationCommandRepository =
            applicationComponentContainer.resolve(
                IdentityVerificationApplicationCommandRepository.class);
    IdentityVerificationApplicationQueryRepository identityVerificationApplicationQueryRepository =
        applicationComponentContainer.resolve(IdentityVerificationApplicationQueryRepository.class);
    IdentityVerificationResultCommandRepository identityVerificationResultCommandRepository =
        applicationComponentContainer.resolve(IdentityVerificationResultCommandRepository.class);
    IdentityVerificationResultQueryRepository identityVerificationResultQueryRepository =
        applicationComponentContainer.resolve(IdentityVerificationResultQueryRepository.class);
    FederationConfigurationCommandRepository federationConfigurationCommandRepository =
        applicationComponentContainer.resolve(FederationConfigurationCommandRepository.class);
    FederationConfigurationQueryRepository federationConfigurationQueryRepository =
        applicationComponentContainer.resolve(FederationConfigurationQueryRepository.class);
    SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository =
        applicationComponentContainer.resolve(SecurityEventHookConfigurationQueryRepository.class);
    SecurityEventHookConfigurationCommandRepository
        securityEventHookConfigurationCommandRepository =
            applicationComponentContainer.resolve(
                SecurityEventHookConfigurationCommandRepository.class);
    TenantInvitationCommandRepository tenantInvitationCommandRepository =
        applicationComponentContainer.resolve(TenantInvitationCommandRepository.class);
    TenantInvitationQueryRepository tenantInvitationQueryRepository =
        applicationComponentContainer.resolve(TenantInvitationQueryRepository.class);

    RoleCommandRepository roleCommandRepository =
        applicationComponentContainer.resolve(RoleCommandRepository.class);
    RoleQueryRepository roleQueryRepository =
        applicationComponentContainer.resolve(RoleQueryRepository.class);
    PermissionCommandRepository permissionCommandRepository =
        applicationComponentContainer.resolve(PermissionCommandRepository.class);
    PermissionQueryRepository permissionQueryRepository =
        applicationComponentContainer.resolve(PermissionQueryRepository.class);
    SecurityEventHookConfigurationQueryRepository hookQueryRepository =
        applicationComponentContainer.resolve(SecurityEventHookConfigurationQueryRepository.class);
    UserLifecycleEventResultCommandRepository userLifecycleEventResultCommandRepository =
        applicationComponentContainer.resolve(UserLifecycleEventResultCommandRepository.class);
    OAuthTokenOperationCommandRepository oAuthTokenOperationCommandRepository =
        applicationComponentContainer.resolve(OAuthTokenOperationCommandRepository.class);
    AuthenticationTransactionOperationCommandRepository
        authenticationTransactionOperationCommandRepository =
            applicationComponentContainer.resolve(
                AuthenticationTransactionOperationCommandRepository.class);
    AuthorizationRequestOperationCommandRepository authorizationRequestOperationCommandRepository =
        applicationComponentContainer.resolve(AuthorizationRequestOperationCommandRepository.class);
    AuthorizationCodeGrantOperationCommandRepository
        authorizationCodeGrantOperationCommandRepository =
            applicationComponentContainer.resolve(
                AuthorizationCodeGrantOperationCommandRepository.class);
    BackchannelAuthenticationRequestOperationCommandRepository
        backchannelAuthenticationRequestOperationCommandRepository =
            applicationComponentContainer.resolve(
                BackchannelAuthenticationRequestOperationCommandRepository.class);
    CibaGrantOperationCommandRepository cibaGrantOperationCommandRepository =
        applicationComponentContainer.resolve(CibaGrantOperationCommandRepository.class);
    SecurityEventQueryRepository securityEventQueryRepository =
        applicationComponentContainer.resolve(SecurityEventQueryRepository.class);
    SecurityEventHookResultQueryRepository securityEventHookResultQueryRepository =
        applicationComponentContainer.resolve(SecurityEventHookResultQueryRepository.class);
    AuditLogQueryRepository auditLogQueryRepository =
        applicationComponentContainer.resolve(AuditLogQueryRepository.class);
    AuthenticationInteractionQueryRepository authenticationInteractionQueryRepository =
        applicationComponentContainer.resolve(AuthenticationInteractionQueryRepository.class);
    WebAuthn4jCredentialRepository webAuthn4jCredentialRepository =
        applicationComponentContainer.resolve(WebAuthn4jCredentialRepository.class);
    TenantStatisticsQueryRepository tenantStatisticsQueryRepository =
        applicationComponentContainer.resolve(TenantStatisticsQueryRepository.class);
    TenantYearlyStatisticsQueryRepository tenantYearlyStatisticsQueryRepository =
        applicationComponentContainer.resolve(TenantYearlyStatisticsQueryRepository.class);
    StatisticsEventsCommandRepository statisticsEventsCommandRepository =
        applicationComponentContainer.resolve(StatisticsEventsCommandRepository.class);
    DailyActiveUserCommandRepository dailyActiveUserCommandRepository =
        applicationComponentContainer.resolve(DailyActiveUserCommandRepository.class);
    MonthlyActiveUserCommandRepository monthlyActiveUserCommandRepository =
        applicationComponentContainer.resolve(MonthlyActiveUserCommandRepository.class);
    YearlyActiveUserCommandRepository yearlyActiveUserCommandRepository =
        applicationComponentContainer.resolve(YearlyActiveUserCommandRepository.class);
    AuthorizationGrantedQueryRepository authorizationGrantedQueryRepository =
        applicationComponentContainer.resolve(AuthorizationGrantedQueryRepository.class);
    AuthorizationGrantedRepository authorizationGrantedRepository =
        applicationComponentContainer.resolve(AuthorizationGrantedRepository.class);
    OAuthTokenCommandRepository oAuthTokenCommandRepository =
        applicationComponentContainer.resolve(OAuthTokenCommandRepository.class);

    // System configuration resolver for SSRF protection and trusted proxy settings
    SystemConfigurationRepository systemConfigurationRepository =
        applicationComponentContainer.resolve(SystemConfigurationRepository.class);
    SystemConfigurationResolver systemConfigurationResolver =
        new CachedSystemConfigurationResolver(systemConfigurationRepository, cacheStore);
    applicationComponentContainer.register(
        SystemConfigurationResolver.class, systemConfigurationResolver);
    dependencyContainer.register(SystemConfigurationResolver.class, systemConfigurationResolver);

    HttpClient httpClient = HttpClientFactory.defaultClient();
    SsrfProtectedHttpClient ssrfProtectedHttpClient =
        new SsrfProtectedHttpClient(httpClient, systemConfigurationResolver);
    applicationComponentContainer.register(SsrfProtectedHttpClient.class, ssrfProtectedHttpClient);
    dependencyContainer.register(SsrfProtectedHttpClient.class, ssrfProtectedHttpClient);

    OAuthAuthorizationResolvers oAuthAuthorizationResolvers =
        new OAuthAuthorizationResolvers(cacheStore, 60, 3600, ssrfProtectedHttpClient);
    applicationComponentContainer.register(
        OAuthAuthorizationResolvers.class, oAuthAuthorizationResolvers);
    dependencyContainer.register(OAuthAuthorizationResolvers.class, oAuthAuthorizationResolvers);

    HttpRequestExecutor httpRequestExecutor =
        new HttpRequestExecutor(ssrfProtectedHttpClient, oAuthAuthorizationResolvers);
    applicationComponentContainer.register(HttpRequestExecutor.class, httpRequestExecutor);
    dependencyContainer.register(HttpRequestExecutor.class, httpRequestExecutor);

    SmsSenders smsSenders = SmsSenderPluginLoader.load(dependencyContainer);
    applicationComponentContainer.register(SmsSenders.class, smsSenders);
    EmailSenders emailSenders = EmailSenderPluginLoader.load(dependencyContainer);
    applicationComponentContainer.register(EmailSenders.class, emailSenders);

    applicationComponentContainer.register(
        PasswordCredentialsGrantDelegate.class,
        new UserPasswordAuthenticator(userQueryRepository, passwordVerificationDelegation));

    applicationComponentContainer.register(
        JwtBearerUserFindingDelegate.class, new JwtBearerUserFinder(userQueryRepository));

    // OIDC Session Management
    // Must be registered before ProtocolContainerPluginLoader.load() as
    // DefaultOAuthProtocolProvider
    // needs it
    OPSessionRepository opSessionRepository =
        applicationComponentContainer.resolve(OPSessionRepository.class);
    ClientSessionRepository clientSessionRepository =
        applicationComponentContainer.resolve(ClientSessionRepository.class);

    OIDCSessionService sessionService =
        new OIDCSessionService(opSessionRepository, clientSessionRepository);
    OIDCSessionHandler oidcSessionHandler = new OIDCSessionHandler(sessionService);
    applicationComponentContainer.register(OIDCSessionHandler.class, oidcSessionHandler);

    ProtocolContainer protocolContainer =
        ProtocolContainerPluginLoader.load(applicationComponentContainer);

    SecurityEventHooks securityEventHooks =
        SecurityEventHooksPluginLoader.load(applicationComponentContainer);

    // create authentication-interactor instance
    AuthenticationDependencyContainer authenticationDependencyContainer =
        AuthenticationDependencyContainerPluginLoader.load(dependencyContainer);
    authenticationDependencyContainer.register(
        PasswordEncodeDelegation.class, passwordEncodeDelegation);
    authenticationDependencyContainer.register(
        PasswordVerificationDelegation.class, passwordVerificationDelegation);
    authenticationDependencyContainer.register(EmailSenders.class, emailSenders);

    authenticationDependencyContainer.register(SmsSenders.class, smsSenders);

    AuthenticationDeviceNotifiers authenticationDeviceNotifiers =
        AuthenticationDeviceNotifiersPluginLoader.load(dependencyContainer);
    authenticationDependencyContainer.register(
        AuthenticationDeviceNotifiers.class, authenticationDeviceNotifiers);

    Map<String, AdditionalRequestParameterResolver> additionalRequestParameterResolvers =
        IdentityVerificationRequestAdditionalParameterPluginLoader.load(
            applicationComponentContainer);

    FidoUafAdditionalRequestResolvers fidoUafAdditionalRequestResolvers =
        FidoUafAdditionalRequestResolverPluginLoader.load();
    authenticationDependencyContainer.register(
        FidoUafAdditionalRequestResolvers.class, fidoUafAdditionalRequestResolvers);

    authenticationDependencyContainer.register(
        OAuthAuthorizationResolvers.class, oAuthAuthorizationResolvers);
    authenticationDependencyContainer.register(HttpRequestExecutor.class, httpRequestExecutor);

    authenticationDependencyContainer.register(
        WebAuthn4jCredentialRepository.class, webAuthn4jCredentialRepository);

    // Register MDS resolver for FIDO Metadata Service integration
    MdsConfiguration mdsConfiguration =
        new MdsConfiguration(true); // Enable MDS with default 24h cache
    MdsResolver mdsResolver = MdsResolverFactory.create(mdsConfiguration, cacheStore);
    authenticationDependencyContainer.register(MdsResolver.class, mdsResolver);

    AuthenticationExecutors authenticationExecutors =
        AuthenticationExecutorPluginLoader.load(authenticationDependencyContainer);
    authenticationDependencyContainer.register(
        AuthenticationExecutors.class, authenticationExecutors);

    AuthenticationInteractors authenticationInteractors =
        AuthenticationInteractorPluginLoader.load(authenticationDependencyContainer);

    UserLifecycleEventExecutorsMap userLifecycleEventExecutorsMap =
        UserLifecycleEventExecutorPluginLoader.load(
            applicationComponentContainer, authenticationDependencyContainer);

    this.idpServerStarterApi =
        TenantAwareEntryServiceProxy.createProxy(
            new IdpServerStarterEntryService(
                organizationRepository,
                tenantQueryRepository,
                tenantCommandRepository,
                userCommandRepository,
                permissionCommandRepository,
                roleCommandRepository,
                authorizationServerConfigurationCommandRepository,
                clientConfigurationCommandRepository,
                clientConfigurationQueryRepository,
                passwordEncodeDelegation),
            IdpServerStarterApi.class,
            databaseTypeProvider);

    this.idpServerOperationApi =
        TenantAwareEntryServiceProxy.createProxy(
            new IdpServerOperationEntryService(
                tenantQueryRepository,
                oAuthTokenOperationCommandRepository,
                authenticationTransactionOperationCommandRepository,
                authorizationRequestOperationCommandRepository,
                authorizationCodeGrantOperationCommandRepository,
                backchannelAuthenticationRequestOperationCommandRepository,
                cibaGrantOperationCommandRepository),
            IdpServerOperationApi.class,
            databaseTypeProvider);

    OAuthFlowEventPublisher oAuthFLowEventPublisher =
        new OAuthFlowEventPublisher(securityEventPublisher);
    CibaFlowEventPublisher cibaFlowEventPublisher =
        new CibaFlowEventPublisher(securityEventPublisher);
    UserEventPublisher userEventPublisher = new UserEventPublisher(securityEventPublisher);
    UserOperationEventPublisher userOperationEventPublisher =
        new UserOperationEventPublisher(securityEventPublisher);

    OidcSsoExecutors oidcSsoExecutors =
        OidcSsoExecutorPluginLoader.load(applicationComponentContainer);
    FederationDependencyContainer federationDependencyContainer =
        FederationDependencyContainerPluginLoader.load(dependencyContainer);
    federationDependencyContainer.register(OidcSsoExecutors.class, oidcSsoExecutors);
    FederationInteractors federationInteractors =
        FederationInteractorPluginLoader.load(federationDependencyContainer);

    ControlPlaneV1SchemaReader.initialValidate();

    AuditLogWriters auditLogWriters =
        AuditLogWriterPluginLoader.load(applicationComponentContainer);

    this.oAuthFlowApi =
        TenantAwareEntryServiceProxy.createProxy(
            new OAuthFlowEntryService(
                new OAuthProtocols(protocolContainer.resolveAll(OAuthProtocol.class)),
                sessionCookieDelegate,
                authSessionCookieDelegate,
                authenticationInteractors,
                federationInteractors,
                userQueryRepository,
                userCommandRepository,
                tenantQueryRepository,
                authenticationTransactionCommandRepository,
                authenticationTransactionQueryRepository,
                authenticationPolicyConfigurationQueryRepository,
                oAuthFLowEventPublisher,
                userLifecycleEventPublisher,
                oidcSessionHandler),
            OAuthFlowApi.class,
            databaseTypeProvider);

    this.tokenApi =
        TenantAwareEntryServiceProxy.createProxy(
            new TokenEntryService(
                new TokenProtocols(protocolContainer.resolveAll(TokenProtocol.class)),
                userQueryRepository,
                tenantQueryRepository,
                userEventPublisher),
            TokenApi.class,
            databaseTypeProvider);

    this.oidcMetaDataApi =
        TenantAwareEntryServiceProxy.createProxy(
            new OidcMetaDataEntryService(
                tenantQueryRepository,
                new DiscoveryProtocols(protocolContainer.resolveAll(DiscoveryProtocol.class))),
            OidcMetaDataApi.class,
            databaseTypeProvider);

    this.userinfoApi =
        TenantAwareEntryServiceProxy.createProxy(
            new UserinfoEntryService(
                new UserinfoProtocols(protocolContainer.resolveAll(UserinfoProtocol.class)),
                userQueryRepository,
                tenantQueryRepository,
                userEventPublisher),
            UserinfoApi.class,
            databaseTypeProvider);

    this.cibaFlowApi =
        TenantAwareEntryServiceProxy.createProxy(
            new CibaFlowEntryService(
                new CibaProtocols(protocolContainer.resolveAll(CibaProtocol.class)),
                authenticationInteractors,
                userQueryRepository,
                tenantQueryRepository,
                authenticationTransactionCommandRepository,
                authenticationTransactionQueryRepository,
                authenticationPolicyConfigurationQueryRepository,
                cibaFlowEventPublisher,
                userLifecycleEventPublisher,
                passwordVerificationDelegation),
            CibaFlowApi.class,
            databaseTypeProvider);

    this.authenticationMetaDataApi =
        TenantAwareEntryServiceProxy.createProxy(
            new AuthenticationMetaDataEntryService(
                authenticationDependencyContainer.resolve(
                    AuthenticationConfigurationQueryRepository.class),
                authenticationExecutors,
                tenantQueryRepository),
            AuthenticationMetaDataApi.class,
            databaseTypeProvider);

    OAuthTokenQueryRepository oAuthTokenQueryRepository =
        applicationComponentContainer.resolve(OAuthTokenQueryRepository.class);

    this.authenticationTransactionApi =
        TenantAwareEntryServiceProxy.createProxy(
            new AuthenticationTransactionEntryService(
                tenantQueryRepository,
                authenticationTransactionCommandRepository,
                authenticationTransactionQueryRepository,
                oAuthTokenQueryRepository,
                userQueryRepository),
            AuthenticationTransactionApi.class,
            databaseTypeProvider);

    this.identityVerificationApplicationApi =
        TenantAwareEntryServiceProxy.createProxy(
            new IdentityVerificationApplicationEntryService(
                identityVerificationConfigurationQueryRepository,
                identityVerificationApplicationCommandRepository,
                identityVerificationApplicationQueryRepository,
                identityVerificationResultCommandRepository,
                tenantQueryRepository,
                userQueryRepository,
                userCommandRepository,
                userEventPublisher,
                additionalRequestParameterResolvers,
                httpRequestExecutor),
            IdentityVerificationApplicationApi.class,
            databaseTypeProvider);

    this.identityVerificationCallbackApi =
        TenantAwareEntryServiceProxy.createProxy(
            new IdentityVerificationCallbackEntryService(
                identityVerificationConfigurationQueryRepository,
                identityVerificationApplicationCommandRepository,
                identityVerificationApplicationQueryRepository,
                identityVerificationResultCommandRepository,
                tenantQueryRepository,
                userQueryRepository,
                userCommandRepository,
                userEventPublisher,
                additionalRequestParameterResolvers,
                httpRequestExecutor),
            IdentityVerificationCallbackApi.class,
            databaseTypeProvider);

    this.identityVerificationApi =
        TenantAwareEntryServiceProxy.createProxy(
            new IdentityVerificationEntryService(
                identityVerificationConfigurationQueryRepository,
                identityVerificationResultCommandRepository,
                identityVerificationResultQueryRepository,
                tenantQueryRepository,
                userQueryRepository,
                userCommandRepository,
                userEventPublisher),
            IdentityVerificationApi.class,
            databaseTypeProvider);

    this.securityEventApi =
        TenantAwareEntryServiceProxy.createProxy(
            new SecurityEventEntryService(
                securityEventHooks,
                securityEventCommandRepository,
                securityEventHookResultCommandRepository,
                hookQueryRepository,
                tenantQueryRepository,
                statisticsEventsCommandRepository,
                dailyActiveUserCommandRepository,
                monthlyActiveUserCommandRepository,
                yearlyActiveUserCommandRepository),
            SecurityEventApi.class,
            databaseTypeProvider);

    this.auditLogApi =
        TenantAwareEntryServiceProxy.createProxy(
            new AuditLogEntryService(auditLogWriters, tenantQueryRepository),
            AuditLogApi.class,
            databaseTypeProvider);

    this.sharedSignalsFrameworkMetaDataApi =
        TenantAwareEntryServiceProxy.createProxy(
            new SharedSignalsFrameworkMetaDataEntryService(
                securityEventHookConfigurationQueryRepository, tenantQueryRepository),
            SharedSignalsFrameworkMetaDataApi.class,
            databaseTypeProvider);

    this.tenantMetaDataApi =
        TenantAwareEntryServiceProxy.createProxy(
            new TenantMetaDataEntryService(tenantQueryRepository),
            TenantMetaDataApi.class,
            databaseTypeProvider);

    this.systemConfigurationApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new SystemConfigurationEntryService(systemConfigurationResolver),
            SystemConfigurationApi.class,
            databaseTypeProvider);

    this.organizationTenantResolverApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new OrganizationTenantResolverEntryService(
                organizationRepository, tenantQueryRepository),
            OrganizationTenantResolverApi.class,
            databaseTypeProvider);

    this.tenantInvitationMetaDataApi =
        TenantAwareEntryServiceProxy.createProxy(
            new TenantInvitationMetaDataEntryService(
                tenantInvitationQueryRepository, tenantQueryRepository),
            TenantInvitationMetaDataApi.class,
            databaseTypeProvider);

    this.userOperationApi =
        TenantAwareEntryServiceProxy.createProxy(
            new UserOperationEntryService(
                userQueryRepository,
                userCommandRepository,
                tenantQueryRepository,
                authenticationTransactionCommandRepository,
                authenticationTransactionQueryRepository,
                authenticationPolicyConfigurationQueryRepository,
                authenticationInteractors,
                userEventPublisher,
                userOperationEventPublisher,
                userLifecycleEventPublisher,
                passwordVerificationDelegation,
                passwordEncodeDelegation),
            UserOperationApi.class,
            databaseTypeProvider);

    this.userLifecycleEventApi =
        TenantAwareEntryServiceProxy.createProxy(
            new UserLifecycleEventEntryService(
                userLifecycleEventExecutorsMap, userLifecycleEventResultCommandRepository),
            UserLifecycleEventApi.class,
            databaseTypeProvider);

    AuthenticationDeviceLogEventPublisher authenticationDeviceLogEventPublisher =
        new AuthenticationDeviceLogEventPublisher(securityEventPublisher);

    this.authenticationDeviceLogApi =
        TenantAwareEntryServiceProxy.createProxy(
            new AuthenticationDeviceLogEntryService(
                tenantQueryRepository, userQueryRepository, authenticationDeviceLogEventPublisher),
            AuthenticationDeviceLogApi.class,
            databaseTypeProvider);

    this.adminUserAuthenticationApi =
        TenantAwareEntryServiceProxy.createProxy(
            new AdminUserAuthenticationEntryService(
                new TokenProtocols(protocolContainer.resolveAll(TokenProtocol.class)),
                tenantQueryRepository,
                userQueryRepository,
                organizationRepository),
            AdminUserAuthenticationApi.class,
            databaseTypeProvider);

    this.userAuthenticationApi =
        TenantAwareEntryServiceProxy.createProxy(
            new UserAuthenticationEntryService(
                new TokenProtocols(protocolContainer.resolveAll(TokenProtocol.class)),
                tenantQueryRepository,
                userQueryRepository,
                organizationRepository),
            UserAuthenticationApi.class,
            databaseTypeProvider);

    this.healthCheckApi =
        TenantAwareEntryServiceProxy.createProxy(
            new HealthCheckEntryService(tenantQueryRepository),
            HealthCheckApi.class,
            databaseTypeProvider);

    // admin
    this.onboardingApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new OnboardingEntryService(
                tenantCommandRepository,
                tenantQueryRepository,
                organizationRepository,
                permissionCommandRepository,
                roleCommandRepository,
                userQueryRepository,
                userCommandRepository,
                authorizationServerConfigurationCommandRepository,
                clientConfigurationCommandRepository,
                passwordEncodeDelegation,
                auditLogPublisher),
            OnboardingApi.class,
            databaseTypeProvider);

    this.tenantManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new TenantManagementEntryService(
                tenantCommandRepository,
                tenantQueryRepository,
                organizationRepository,
                authorizationServerConfigurationCommandRepository,
                userCommandRepository,
                auditLogPublisher),
            TenantManagementApi.class,
            databaseTypeProvider);

    this.tenantStatisticsApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new TenantStatisticsEntryService(
                tenantStatisticsQueryRepository,
                tenantYearlyStatisticsQueryRepository,
                tenantQueryRepository,
                auditLogPublisher),
            TenantStatisticsApi.class,
            databaseTypeProvider);

    this.organizationManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new OrganizationManagementEntryService(organizationRepository, auditLogPublisher),
            OrganizationManagementApi.class,
            databaseTypeProvider);

    this.tenantInvitationManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new TenantInvitationManagementEntryService(
                tenantInvitationCommandRepository,
                tenantInvitationQueryRepository,
                tenantQueryRepository,
                emailSenders),
            TenantInvitationManagementApi.class,
            databaseTypeProvider);

    this.authorizationServerManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new AuthorizationServerManagementEntryService(
                tenantQueryRepository,
                authorizationServerConfigurationQueryRepository,
                authorizationServerConfigurationCommandRepository,
                auditLogPublisher),
            AuthorizationServerManagementApi.class,
            databaseTypeProvider);

    this.clientManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new ClientManagementEntryService(
                tenantQueryRepository,
                clientConfigurationCommandRepository,
                clientConfigurationQueryRepository,
                auditLogPublisher),
            ClientManagementApi.class,
            databaseTypeProvider);

    this.userManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new UserManagementEntryService(
                tenantQueryRepository,
                userQueryRepository,
                userCommandRepository,
                roleQueryRepository,
                organizationRepository,
                opSessionRepository,
                passwordEncodeDelegation,
                userLifecycleEventPublisher,
                auditLogPublisher,
                securityEventPublisher),
            UserManagementApi.class,
            databaseTypeProvider);

    this.authenticationConfigurationManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new AuthenticationConfigurationManagementEntryService(
                authenticationConfigurationCommandRepository,
                authenticationConfigurationQueryRepository,
                tenantQueryRepository,
                auditLogPublisher),
            AuthenticationConfigurationManagementApi.class,
            databaseTypeProvider);

    this.authenticationPolicyConfigurationManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new AuthenticationPolicyConfigurationManagementEntryService(
                authenticationPolicyConfigurationCommandRepository,
                authenticationPolicyConfigurationQueryRepository,
                tenantQueryRepository,
                auditLogPublisher),
            AuthenticationPolicyConfigurationManagementApi.class,
            databaseTypeProvider);

    this.federationConfigurationManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new FederationConfigurationManagementEntryService(
                tenantQueryRepository,
                federationConfigurationQueryRepository,
                federationConfigurationCommandRepository,
                auditLogPublisher),
            FederationConfigurationManagementApi.class,
            databaseTypeProvider);

    this.securityEventHookConfigurationManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new SecurityEventHookConfigurationManagementEntryService(
                securityEventHookConfigurationCommandRepository,
                securityEventHookConfigurationQueryRepository,
                tenantQueryRepository,
                auditLogPublisher),
            SecurityEventHookConfigurationManagementApi.class,
            databaseTypeProvider);

    this.identityVerificationConfigManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new IdentityVerificationConfigManagementEntryService(
                identityVerificationConfigurationCommandRepository,
                identityVerificationConfigurationQueryRepository,
                tenantQueryRepository,
                auditLogPublisher),
            IdentityVerificationConfigManagementApi.class,
            databaseTypeProvider);

    this.securityEventManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new SecurityEventManagementEntryService(
                securityEventQueryRepository, tenantQueryRepository, auditLogPublisher),
            SecurityEventManagementApi.class,
            databaseTypeProvider);

    this.securityEventHookManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new SecurityEventHookManagementEntryService(
                tenantQueryRepository,
                securityEventHookResultQueryRepository,
                securityEventHookResultCommandRepository,
                securityEventHooks,
                securityEventHookConfigurationQueryRepository,
                auditLogPublisher),
            SecurityEventHookManagementApi.class,
            databaseTypeProvider);

    this.auditLogManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new AuditLogManagementEntryService(
                auditLogQueryRepository, tenantQueryRepository, auditLogPublisher),
            AuditLogManagementApi.class,
            databaseTypeProvider);

    this.authenticationInteractionManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new AuthenticationInteractionManagementEntryService(
                authenticationInteractionQueryRepository, tenantQueryRepository, auditLogPublisher),
            AuthenticationInteractionManagementApi.class,
            databaseTypeProvider);

    this.authenticationTransactionManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new AuthenticationTransactionManagementEntryService(
                authenticationTransactionQueryRepository, tenantQueryRepository, auditLogPublisher),
            AuthenticationTransactionManagementApi.class,
            databaseTypeProvider);

    this.permissionManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new PermissionManagementEntryService(
                tenantQueryRepository,
                permissionQueryRepository,
                permissionCommandRepository,
                auditLogPublisher),
            PermissionManagementApi.class,
            databaseTypeProvider);

    this.roleManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new RoleManagementEntryService(
                tenantQueryRepository,
                roleQueryRepository,
                roleCommandRepository,
                permissionQueryRepository,
                auditLogPublisher),
            RoleManagementApi.class,
            databaseTypeProvider);

    this.systemConfigurationManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new SystemConfigurationManagementEntryService(
                systemConfigurationRepository, systemConfigurationResolver, auditLogPublisher),
            SystemConfigurationManagementApi.class,
            databaseTypeProvider);

    // organization
    this.organizationUserAuthenticationApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new OrganizationUserAuthenticationEntryService(
                new TokenProtocols(protocolContainer.resolveAll(TokenProtocol.class)),
                tenantQueryRepository,
                userQueryRepository,
                organizationRepository),
            OrganizationUserAuthenticationApi.class,
            databaseTypeProvider);

    this.orgTenantManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new OrgTenantManagementEntryService(
                tenantCommandRepository,
                tenantQueryRepository,
                organizationRepository,
                authorizationServerConfigurationCommandRepository,
                userCommandRepository,
                auditLogPublisher),
            OrgTenantManagementApi.class,
            databaseTypeProvider);

    this.orgTenantStatisticsApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new OrgTenantStatisticsEntryService(
                tenantStatisticsQueryRepository,
                tenantYearlyStatisticsQueryRepository,
                organizationRepository,
                tenantQueryRepository,
                auditLogPublisher),
            OrgTenantStatisticsApi.class,
            databaseTypeProvider);

    this.orgClientManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new OrgClientManagementEntryService(
                tenantQueryRepository,
                organizationRepository,
                clientConfigurationCommandRepository,
                clientConfigurationQueryRepository,
                auditLogPublisher),
            OrgClientManagementApi.class,
            databaseTypeProvider);

    this.orgGrantManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new OrgGrantManagementEntryService(
                tenantQueryRepository,
                authorizationGrantedQueryRepository,
                authorizationGrantedRepository,
                oAuthTokenCommandRepository,
                auditLogPublisher),
            OrgGrantManagementApi.class,
            databaseTypeProvider);

    this.orgUserManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new OrgUserManagementEntryService(
                tenantQueryRepository,
                organizationRepository,
                userQueryRepository,
                userCommandRepository,
                roleQueryRepository,
                opSessionRepository,
                passwordEncodeDelegation,
                userLifecycleEventPublisher,
                auditLogPublisher,
                securityEventPublisher),
            OrgUserManagementApi.class,
            databaseTypeProvider);

    this.orgSecurityEventManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new OrgSecurityEventManagementEntryService(
                tenantQueryRepository,
                organizationRepository,
                securityEventQueryRepository,
                auditLogPublisher),
            OrgSecurityEventManagementApi.class,
            databaseTypeProvider);

    this.orgAuthenticationConfigManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new OrgAuthenticationConfigManagementEntryService(
                tenantQueryRepository,
                authenticationConfigurationCommandRepository,
                authenticationConfigurationQueryRepository,
                auditLogPublisher),
            OrgAuthenticationConfigManagementApi.class,
            databaseTypeProvider);

    this.orgFederationConfigManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new OrgFederationConfigurationManagementEntryService(
                tenantQueryRepository,
                organizationRepository,
                federationConfigurationQueryRepository,
                federationConfigurationCommandRepository,
                auditLogPublisher),
            OrgFederationConfigManagementApi.class,
            databaseTypeProvider);

    this.orgSecurityEventHookConfigManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new OrgSecurityEventHookConfigManagementEntryService(
                tenantQueryRepository,
                organizationRepository,
                securityEventHookConfigurationCommandRepository,
                securityEventHookConfigurationQueryRepository,
                auditLogPublisher),
            OrgSecurityEventHookConfigManagementApi.class,
            databaseTypeProvider);

    this.orgAuthenticationInteractionManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new OrgAuthenticationInteractionManagementEntryService(
                tenantQueryRepository, authenticationInteractionQueryRepository, auditLogPublisher),
            OrgAuthenticationInteractionManagementApi.class,
            databaseTypeProvider);

    this.orgAuthenticationTransactionManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new OrgAuthenticationTransactionManagementEntryService(
                authenticationTransactionQueryRepository, tenantQueryRepository, auditLogPublisher),
            OrgAuthenticationTransactionManagementApi.class,
            databaseTypeProvider);

    this.orgAuthorizationServerManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new OrgAuthorizationServerManagementEntryService(
                tenantQueryRepository,
                authorizationServerConfigurationQueryRepository,
                authorizationServerConfigurationCommandRepository,
                auditLogPublisher),
            OrgAuthorizationServerManagementApi.class,
            databaseTypeProvider);

    this.orgPermissionManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new OrgPermissionManagementEntryService(
                tenantQueryRepository,
                organizationRepository,
                permissionQueryRepository,
                permissionCommandRepository,
                auditLogPublisher),
            OrgPermissionManagementApi.class,
            databaseTypeProvider);

    this.orgRoleManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new OrgRoleManagementEntryService(
                tenantQueryRepository,
                organizationRepository,
                roleQueryRepository,
                roleCommandRepository,
                permissionQueryRepository,
                auditLogPublisher),
            OrgRoleManagementApi.class,
            databaseTypeProvider);

    this.orgAuthenticationPolicyConfigManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new OrgAuthenticationPolicyConfigManagementEntryService(
                tenantQueryRepository,
                organizationRepository,
                authenticationPolicyConfigurationCommandRepository,
                authenticationPolicyConfigurationQueryRepository,
                auditLogPublisher),
            OrgAuthenticationPolicyConfigManagementApi.class,
            databaseTypeProvider);

    this.orgIdentityVerificationConfigManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new OrgIdentityVerificationConfigManagementEntryService(
                tenantQueryRepository,
                organizationRepository,
                identityVerificationConfigurationCommandRepository,
                identityVerificationConfigurationQueryRepository,
                auditLogPublisher),
            OrgIdentityVerificationConfigManagementApi.class,
            databaseTypeProvider);

    this.orgAuditLogManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new OrgAuditLogManagementEntryService(
                tenantQueryRepository, auditLogQueryRepository, auditLogPublisher),
            OrgAuditLogManagementApi.class,
            databaseTypeProvider);

    this.orgSecurityEventHookManagementApi =
        ManagementTypeEntryServiceProxy.createProxy(
            new OrgSecurityEventHookManagementEntryService(
                tenantQueryRepository,
                organizationRepository,
                securityEventHookResultQueryRepository,
                securityEventHookResultCommandRepository,
                securityEventHooks,
                securityEventHookConfigurationQueryRepository,
                auditLogPublisher),
            OrgSecurityEventHookManagementApi.class,
            databaseTypeProvider);
  }

  public IdpServerStarterApi idpServerStarterApi() {
    return idpServerStarterApi;
  }

  public IdpServerOperationApi idpServerOperationApi() {
    return idpServerOperationApi;
  }

  public OAuthFlowApi oAuthFlowApi() {
    return oAuthFlowApi;
  }

  public TokenApi tokenAPi() {
    return tokenApi;
  }

  public OidcMetaDataApi oidcMetaDataApi() {
    return oidcMetaDataApi;
  }

  public UserinfoApi userinfoApi() {
    return userinfoApi;
  }

  public CibaFlowApi cibaFlowApi() {
    return cibaFlowApi;
  }

  public AuthenticationMetaDataApi authenticationMetaDataApi() {
    return authenticationMetaDataApi;
  }

  public AuthenticationTransactionApi authenticationApi() {
    return authenticationTransactionApi;
  }

  public IdentityVerificationApplicationApi identityVerificationApplicationApi() {
    return identityVerificationApplicationApi;
  }

  public IdentityVerificationCallbackApi identityVerificationCallbackApi() {
    return identityVerificationCallbackApi;
  }

  public IdentityVerificationApi identityVerificationApi() {
    return identityVerificationApi;
  }

  public SecurityEventApi securityEventApi() {
    return securityEventApi;
  }

  public AuditLogApi auditLogApi() {
    return auditLogApi;
  }

  public TenantMetaDataApi tenantMetadataApi() {
    return tenantMetaDataApi;
  }

  public OrganizationTenantResolverApi organizationTenantResolverApi() {
    return organizationTenantResolverApi;
  }

  public TenantInvitationMetaDataApi tenantInvitationMetaDataApi() {
    return tenantInvitationMetaDataApi;
  }

  public UserOperationApi userOperationApi() {
    return userOperationApi;
  }

  public SharedSignalsFrameworkMetaDataApi sharedSignalsFrameworkMetaDataApi() {
    return sharedSignalsFrameworkMetaDataApi;
  }

  public UserLifecycleEventApi userLifecycleEventApi() {
    return userLifecycleEventApi;
  }

  public AuthenticationDeviceLogApi authenticationDeviceLogApi() {
    return authenticationDeviceLogApi;
  }

  public OnboardingApi onboardingApi() {
    return onboardingApi;
  }

  public TenantManagementApi tenantManagementApi() {
    return tenantManagementApi;
  }

  public TenantStatisticsApi tenantStatisticsApi() {
    return tenantStatisticsApi;
  }

  public OrganizationManagementApi organizationManagementApi() {
    return organizationManagementApi;
  }

  public TenantInvitationManagementApi tenantInvitationManagementApi() {
    return tenantInvitationManagementApi;
  }

  public AuthorizationServerManagementApi authorizationServerManagementApi() {
    return authorizationServerManagementApi;
  }

  public ClientManagementApi clientManagementApi() {
    return clientManagementApi;
  }

  public UserManagementApi userManagementAPi() {
    return userManagementApi;
  }

  public AuthenticationConfigurationManagementApi authenticationConfigurationManagementApi() {
    return authenticationConfigurationManagementApi;
  }

  public AuthenticationPolicyConfigurationManagementApi
      authenticationPolicyConfigurationManagementApi() {
    return authenticationPolicyConfigurationManagementApi;
  }

  public IdentityVerificationConfigManagementApi identityVerificationConfigManagementApi() {
    return identityVerificationConfigManagementApi;
  }

  public UserAuthenticationApi operatorAuthenticationApi() {
    return userAuthenticationApi;
  }

  public FederationConfigurationManagementApi federationConfigManagementApi() {
    return federationConfigurationManagementApi;
  }

  public SecurityEventHookConfigurationManagementApi securityEventHookConfigurationManagementApi() {
    return securityEventHookConfigurationManagementApi;
  }

  public SecurityEventManagementApi securityEventManagementApi() {
    return securityEventManagementApi;
  }

  public SecurityEventHookManagementApi securityEventHookManagementApi() {
    return securityEventHookManagementApi;
  }

  public AuditLogManagementApi auditLogManagementApi() {
    return auditLogManagementApi;
  }

  public AuthenticationInteractionManagementApi authenticationInteractionManagementApi() {
    return authenticationInteractionManagementApi;
  }

  public PermissionManagementApi permissionManagementApi() {
    return permissionManagementApi;
  }

  public RoleManagementApi roleManagementApi() {
    return roleManagementApi;
  }

  public SystemConfigurationManagementApi systemConfigurationManagementApi() {
    return systemConfigurationManagementApi;
  }

  public SystemConfigurationApi systemConfigurationApi() {
    return systemConfigurationApi;
  }

  public AuthenticationTransactionManagementApi authenticationTransactionManagementApi() {
    return authenticationTransactionManagementApi;
  }

  public AdminUserAuthenticationApi adminUserAuthenticationApi() {
    return adminUserAuthenticationApi;
  }

  public OrganizationUserAuthenticationApi organizationUserAuthenticationApi() {
    return organizationUserAuthenticationApi;
  }

  public OrgTenantManagementApi orgTenantManagementApi() {
    return orgTenantManagementApi;
  }

  public OrgTenantStatisticsApi orgTenantStatisticsApi() {
    return orgTenantStatisticsApi;
  }

  public OrgClientManagementApi orgClientManagementApi() {
    return orgClientManagementApi;
  }

  public OrgGrantManagementApi orgGrantManagementApi() {
    return orgGrantManagementApi;
  }

  public OrgUserManagementApi orgUserManagementApi() {
    return orgUserManagementApi;
  }

  public OrgSecurityEventManagementApi orgSecurityEventManagementApi() {
    return orgSecurityEventManagementApi;
  }

  public OrgAuthenticationConfigManagementApi orgAuthenticationConfigManagementApi() {
    return orgAuthenticationConfigManagementApi;
  }

  public OrgFederationConfigManagementApi orgFederationConfigManagementApi() {
    return orgFederationConfigManagementApi;
  }

  public OrgSecurityEventHookConfigManagementApi orgSecurityEventHookConfigManagementApi() {
    return orgSecurityEventHookConfigManagementApi;
  }

  public OrgAuthenticationInteractionManagementApi orgAuthenticationInteractionManagementApi() {
    return orgAuthenticationInteractionManagementApi;
  }

  public OrgAuthenticationTransactionManagementApi orgAuthenticationTransactionManagementApi() {
    return orgAuthenticationTransactionManagementApi;
  }

  public OrgAuthorizationServerManagementApi orgAuthorizationServerManagementApi() {
    return orgAuthorizationServerManagementApi;
  }

  public OrgPermissionManagementApi orgPermissionManagementApi() {
    return orgPermissionManagementApi;
  }

  public OrgRoleManagementApi orgRoleManagementApi() {
    return orgRoleManagementApi;
  }

  public OrgAuthenticationPolicyConfigManagementApi orgAuthenticationPolicyConfigManagementApi() {
    return orgAuthenticationPolicyConfigManagementApi;
  }

  public OrgIdentityVerificationConfigManagementApi orgIdentityVerificationConfigManagementApi() {
    return orgIdentityVerificationConfigManagementApi;
  }

  public OrgAuditLogManagementApi orgAuditLogManagementApi() {
    return orgAuditLogManagementApi;
  }

  public OrgSecurityEventHookManagementApi orgSecurityEventHookManagementApi() {
    return orgSecurityEventHookManagementApi;
  }

  public HealthCheckApi healthCheckApi() {
    return healthCheckApi;
  }
}
