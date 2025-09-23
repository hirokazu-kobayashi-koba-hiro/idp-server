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

package org.idp.server;

import java.net.http.HttpClient;
import java.util.Map;
import org.idp.server.authentication.interactors.device.AuthenticationDeviceNotifiers;
import org.idp.server.authentication.interactors.fidouaf.AuthenticationMetaDataApi;
import org.idp.server.authentication.interactors.fidouaf.plugin.FidoUafAdditionalRequestResolvers;
import org.idp.server.authentication.interactors.plugin.AuthenticationDeviceNotifiersPluginLoader;
import org.idp.server.authentication.interactors.plugin.FidoUafAdditionalRequestResolverPluginLoader;
import org.idp.server.authentication.interactors.plugin.WebAuthnExecutorPluginLoader;
import org.idp.server.authentication.interactors.webauthn.WebAuthnExecutors;
import org.idp.server.control_plane.admin.operation.IdpServerOperationApi;
import org.idp.server.control_plane.admin.organization.OrganizationInitializationApi;
import org.idp.server.control_plane.admin.starter.IdpServerStarterApi;
import org.idp.server.control_plane.base.AdminDashboardUrl;
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
import org.idp.server.control_plane.management.onboarding.OnboardingApi;
import org.idp.server.control_plane.management.permission.OrgPermissionManagementApi;
import org.idp.server.control_plane.management.permission.PermissionManagementApi;
import org.idp.server.control_plane.management.role.OrgRoleManagementApi;
import org.idp.server.control_plane.management.role.RoleManagementApi;
import org.idp.server.control_plane.management.security.event.OrgSecurityEventManagementApi;
import org.idp.server.control_plane.management.security.event.SecurityEventManagementApi;
import org.idp.server.control_plane.management.security.hook.OrgSecurityEventHookConfigManagementApi;
import org.idp.server.control_plane.management.security.hook.SecurityEventHookConfigurationManagementApi;
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
import org.idp.server.core.openid.identity.*;
import org.idp.server.core.openid.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.openid.identity.authentication.PasswordVerificationDelegation;
import org.idp.server.core.openid.identity.authentication.UserPasswordAuthenticator;
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
import org.idp.server.core.openid.token.*;
import org.idp.server.core.openid.token.repository.OAuthTokenOperationCommandRepository;
import org.idp.server.core.openid.userinfo.UserinfoApi;
import org.idp.server.core.openid.userinfo.UserinfoProtocol;
import org.idp.server.core.openid.userinfo.UserinfoProtocols;
import org.idp.server.federation.sso.oidc.OidcSsoExecutorPluginLoader;
import org.idp.server.federation.sso.oidc.OidcSsoExecutors;
import org.idp.server.platform.audit.AuditLogQueryRepository;
import org.idp.server.platform.audit.AuditLogWriters;
import org.idp.server.platform.crypto.AesCipher;
import org.idp.server.platform.crypto.HmacHasher;
import org.idp.server.platform.datasource.*;
import org.idp.server.platform.datasource.DatabaseTypeConfiguration;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.protocol.ProtocolContainer;
import org.idp.server.platform.http.HttpClientFactory;
import org.idp.server.platform.http.HttpRequestExecutor;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.*;
import org.idp.server.platform.notification.email.EmailSenders;
import org.idp.server.platform.notification.sms.SmsSenders;
import org.idp.server.platform.oauth.OAuthAuthorizationResolvers;
import org.idp.server.platform.plugin.*;
import org.idp.server.platform.proxy.OrganizationAwareEntryServiceProxy;
import org.idp.server.platform.proxy.TenantAwareEntryServiceProxy;
import org.idp.server.platform.security.SecurityEventApi;
import org.idp.server.platform.security.SecurityEventPublisher;
import org.idp.server.platform.security.hook.SecurityEventHooks;
import org.idp.server.platform.security.repository.*;
import org.idp.server.security.event.hook.ssf.SharedSignalsFrameworkMetaDataApi;
import org.idp.server.usecases.application.enduser.*;
import org.idp.server.usecases.application.identity_verification_service.IdentityVerificationCallbackEntryService;
import org.idp.server.usecases.application.identity_verification_service.IdentityVerificationEntryService;
import org.idp.server.usecases.application.relying_party.OidcMetaDataEntryService;
import org.idp.server.usecases.application.relying_party.SharedSignalsFrameworkMetaDataEntryService;
import org.idp.server.usecases.application.system.*;
import org.idp.server.usecases.application.tenant_invitator.TenantInvitationMetaDataEntryService;
import org.idp.server.usecases.control_plane.organization_manager.OrgAuditLogManagementEntryService;
import org.idp.server.usecases.control_plane.organization_manager.OrgAuthenticationConfigManagementEntryService;
import org.idp.server.usecases.control_plane.organization_manager.OrgAuthenticationInteractionManagementEntryService;
import org.idp.server.usecases.control_plane.organization_manager.OrgAuthenticationPolicyConfigManagementEntryService;
import org.idp.server.usecases.control_plane.organization_manager.OrgAuthenticationTransactionManagementEntryService;
import org.idp.server.usecases.control_plane.organization_manager.OrgAuthorizationServerManagementEntryService;
import org.idp.server.usecases.control_plane.organization_manager.OrgClientManagementEntryService;
import org.idp.server.usecases.control_plane.organization_manager.OrgFederationConfigManagementEntryService;
import org.idp.server.usecases.control_plane.organization_manager.OrgIdentityVerificationConfigManagementEntryService;
import org.idp.server.usecases.control_plane.organization_manager.OrgPermissionManagementEntryService;
import org.idp.server.usecases.control_plane.organization_manager.OrgRoleManagementEntryService;
import org.idp.server.usecases.control_plane.organization_manager.OrgSecurityEventHookConfigManagementEntryService;
import org.idp.server.usecases.control_plane.organization_manager.OrgSecurityEventManagementEntryService;
import org.idp.server.usecases.control_plane.organization_manager.OrgTenantManagementEntryService;
import org.idp.server.usecases.control_plane.organization_manager.OrgUserManagementEntryService;
import org.idp.server.usecases.control_plane.system_administrator.IdpServerOperationEntryService;
import org.idp.server.usecases.control_plane.system_administrator.IdpServerStarterEntryService;
import org.idp.server.usecases.control_plane.system_administrator.OrganizationInitializationEntryService;
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
  SharedSignalsFrameworkMetaDataApi sharedSignalsFrameworkMetaDataApi;
  TenantMetaDataApi tenantMetaDataApi;
  TenantInvitationMetaDataApi tenantInvitationMetaDataApi;
  UserOperationApi userOperationApi;
  UserLifecycleEventApi userLifecycleEventApi;
  OnboardingApi onboardingApi;
  OrganizationInitializationApi organizationInitializationApi;
  TenantManagementApi tenantManagementApi;

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
  AuditLogManagementApi auditLogManagementApi;
  AuthenticationInteractionManagementApi authenticationInteractionManagementApi;
  AuthenticationTransactionManagementApi authenticationTransactionManagementApi;
  PermissionManagementApi permissionManagementApi;
  RoleManagementApi roleManagementApi;
  UserAuthenticationApi userAuthenticationApi;

  OrgTenantManagementApi orgTenantManagementApi;
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

  public IdpServerApplication(
      String adminTenantId,
      ApplicationDatabaseTypeProvider databaseTypeProvider,
      AdminDashboardUrl adminDashboardUrl,
      DbConnectionProvider dbConnectionProvider,
      String encryptionKey,
      String databaseType,
      CacheStore cacheStore,
      OAuthSessionDelegate oAuthSessionDelegate,
      PasswordEncodeDelegation passwordEncodeDelegation,
      PasswordVerificationDelegation passwordVerificationDelegation,
      SecurityEventPublisher securityEventPublisher,
      UserLifecycleEventPublisher userLifecycleEventPublisher) {

    AdminTenantContext.configure(adminTenantId);
    TransactionManager.configure(dbConnectionProvider);

    ApplicationComponentDependencyContainer dependencyContainer =
        new ApplicationComponentDependencyContainer();
    dependencyContainer.register(ApplicationDatabaseTypeProvider.class, databaseTypeProvider);
    AesCipher aesCipher = new AesCipher(encryptionKey);
    HmacHasher hmacHasher = new HmacHasher(encryptionKey);
    dependencyContainer.register(AesCipher.class, aesCipher);
    dependencyContainer.register(HmacHasher.class, hmacHasher);
    dependencyContainer.register(CacheStore.class, cacheStore);
    DatabaseTypeConfiguration databaseTypeConfig = new DatabaseTypeConfiguration(databaseType);
    dependencyContainer.register(DatabaseTypeConfiguration.class, databaseTypeConfig);
    ApplicationComponentContainer applicationComponentContainer =
        ApplicationComponentContainerPluginLoader.load(dependencyContainer);
    applicationComponentContainer.register(OAuthSessionDelegate.class, oAuthSessionDelegate);
    OAuthAuthorizationResolvers oAuthAuthorizationResolvers =
        applicationComponentContainer.resolve(OAuthAuthorizationResolvers.class);
    dependencyContainer.register(OAuthAuthorizationResolvers.class, oAuthAuthorizationResolvers);

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
    AuditLogQueryRepository auditLogQueryRepository =
        applicationComponentContainer.resolve(AuditLogQueryRepository.class);
    AuthenticationInteractionQueryRepository authenticationInteractionQueryRepository =
        applicationComponentContainer.resolve(AuthenticationInteractionQueryRepository.class);

    SmsSenders smsSenders = SmsSenderPluginLoader.load(dependencyContainer);
    applicationComponentContainer.register(SmsSenders.class, smsSenders);
    EmailSenders emailSenders = EmailSenderPluginLoader.load(dependencyContainer);
    applicationComponentContainer.register(EmailSenders.class, emailSenders);

    HttpClient httpClient = HttpClientFactory.defaultClient();
    HttpRequestExecutor httpRequestExecutor =
        new HttpRequestExecutor(httpClient, oAuthAuthorizationResolvers);
    applicationComponentContainer.register(HttpRequestExecutor.class, httpRequestExecutor);

    applicationComponentContainer.register(
        PasswordCredentialsGrantDelegate.class,
        new UserPasswordAuthenticator(userQueryRepository, passwordVerificationDelegation));

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
    WebAuthnExecutors webAuthnExecutors =
        WebAuthnExecutorPluginLoader.load(authenticationDependencyContainer);
    authenticationDependencyContainer.register(WebAuthnExecutors.class, webAuthnExecutors);
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
                oAuthSessionDelegate,
                authenticationInteractors,
                federationInteractors,
                userQueryRepository,
                userCommandRepository,
                tenantQueryRepository,
                authenticationTransactionCommandRepository,
                authenticationTransactionQueryRepository,
                authenticationPolicyConfigurationQueryRepository,
                oAuthFLowEventPublisher,
                userLifecycleEventPublisher),
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

    this.authenticationTransactionApi =
        TenantAwareEntryServiceProxy.createProxy(
            new AuthenticationTransactionEntryService(
                tenantQueryRepository,
                authenticationTransactionCommandRepository,
                authenticationTransactionQueryRepository),
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
                oAuthAuthorizationResolvers),
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
                oAuthAuthorizationResolvers),
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
                tenantQueryRepository),
            SecurityEventApi.class,
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
                userLifecycleEventPublisher),
            UserOperationApi.class,
            databaseTypeProvider);

    this.userLifecycleEventApi =
        TenantAwareEntryServiceProxy.createProxy(
            new UserLifecycleEventEntryService(
                userLifecycleEventExecutorsMap, userLifecycleEventResultCommandRepository),
            UserLifecycleEventApi.class,
            databaseTypeProvider);

    this.onboardingApi =
        TenantAwareEntryServiceProxy.createProxy(
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
                clientConfigurationQueryRepository,
                passwordEncodeDelegation),
            OnboardingApi.class,
            databaseTypeProvider);

    this.organizationInitializationApi =
        TenantAwareEntryServiceProxy.createProxy(
            new OrganizationInitializationEntryService(
                tenantCommandRepository,
                tenantQueryRepository,
                organizationRepository,
                userQueryRepository,
                userCommandRepository,
                authorizationServerConfigurationCommandRepository,
                clientConfigurationCommandRepository,
                clientConfigurationQueryRepository,
                passwordEncodeDelegation),
            OrganizationInitializationApi.class,
            databaseTypeProvider);

    this.tenantManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new TenantManagementEntryService(
                tenantCommandRepository,
                tenantQueryRepository,
                organizationRepository,
                authorizationServerConfigurationCommandRepository,
                userCommandRepository,
                auditLogWriters),
            TenantManagementApi.class,
            databaseTypeProvider);

    this.tenantInvitationManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new TenantInvitationManagementEntryService(
                tenantInvitationCommandRepository,
                tenantInvitationQueryRepository,
                tenantQueryRepository,
                emailSenders,
                adminDashboardUrl),
            TenantInvitationManagementApi.class,
            databaseTypeProvider);

    this.authorizationServerManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new AuthorizationServerManagementEntryService(
                tenantQueryRepository,
                authorizationServerConfigurationQueryRepository,
                authorizationServerConfigurationCommandRepository,
                auditLogWriters),
            AuthorizationServerManagementApi.class,
            databaseTypeProvider);

    this.clientManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new ClientManagementEntryService(
                tenantQueryRepository,
                clientConfigurationCommandRepository,
                clientConfigurationQueryRepository,
                auditLogWriters),
            ClientManagementApi.class,
            databaseTypeProvider);

    this.userManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new UserManagementEntryService(
                tenantQueryRepository,
                userQueryRepository,
                userCommandRepository,
                roleQueryRepository,
                organizationRepository,
                passwordEncodeDelegation,
                userLifecycleEventPublisher,
                auditLogWriters),
            UserManagementApi.class,
            databaseTypeProvider);

    this.authenticationConfigurationManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new AuthenticationConfigurationManagementEntryService(
                authenticationConfigurationCommandRepository,
                authenticationConfigurationQueryRepository,
                tenantQueryRepository,
                auditLogWriters),
            AuthenticationConfigurationManagementApi.class,
            databaseTypeProvider);

    this.authenticationPolicyConfigurationManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new AuthenticationPolicyConfigurationManagementEntryService(
                authenticationPolicyConfigurationCommandRepository,
                authenticationPolicyConfigurationQueryRepository,
                tenantQueryRepository,
                auditLogWriters),
            AuthenticationPolicyConfigurationManagementApi.class,
            databaseTypeProvider);

    this.federationConfigurationManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new FederationConfigurationManagementEntryService(
                federationConfigurationQueryRepository,
                federationConfigurationCommandRepository,
                tenantQueryRepository,
                auditLogWriters),
            FederationConfigurationManagementApi.class,
            databaseTypeProvider);

    this.securityEventHookConfigurationManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new SecurityEventHookConfigurationManagementEntryService(
                securityEventHookConfigurationCommandRepository,
                securityEventHookConfigurationQueryRepository,
                tenantQueryRepository,
                auditLogWriters),
            SecurityEventHookConfigurationManagementApi.class,
            databaseTypeProvider);

    this.identityVerificationConfigManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new IdentityVerificationConfigManagementEntryService(
                identityVerificationConfigurationCommandRepository,
                identityVerificationConfigurationQueryRepository,
                tenantQueryRepository,
                auditLogWriters),
            IdentityVerificationConfigManagementApi.class,
            databaseTypeProvider);

    this.securityEventManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new SecurityEventManagementEntryService(
                securityEventQueryRepository, tenantQueryRepository, auditLogWriters),
            SecurityEventManagementApi.class,
            databaseTypeProvider);

    this.auditLogManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new AuditLogManagementEntryService(
                auditLogQueryRepository, tenantQueryRepository, auditLogWriters),
            AuditLogManagementApi.class,
            databaseTypeProvider);

    this.authenticationInteractionManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new AuthenticationInteractionManagementEntryService(
                authenticationInteractionQueryRepository, tenantQueryRepository, auditLogWriters),
            AuthenticationInteractionManagementApi.class,
            databaseTypeProvider);

    this.authenticationTransactionManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new AuthenticationTransactionManagementEntryService(
                authenticationTransactionQueryRepository, tenantQueryRepository, auditLogWriters),
            AuthenticationTransactionManagementApi.class,
            databaseTypeProvider);

    this.permissionManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new PermissionManagementEntryService(
                tenantQueryRepository,
                permissionQueryRepository,
                permissionCommandRepository,
                auditLogWriters),
            PermissionManagementApi.class,
            databaseTypeProvider);

    this.roleManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new RoleManagementEntryService(
                tenantQueryRepository,
                roleQueryRepository,
                roleCommandRepository,
                permissionQueryRepository,
                auditLogWriters),
            RoleManagementApi.class,
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

    // organization
    this.organizationUserAuthenticationApi =
        OrganizationAwareEntryServiceProxy.createProxy(
            new OrganizationUserAuthenticationEntryService(
                new TokenProtocols(protocolContainer.resolveAll(TokenProtocol.class)),
                tenantQueryRepository,
                userQueryRepository,
                organizationRepository),
            OrganizationUserAuthenticationApi.class,
            databaseTypeProvider);

    this.orgTenantManagementApi =
        OrganizationAwareEntryServiceProxy.createProxy(
            new OrgTenantManagementEntryService(
                tenantCommandRepository,
                tenantQueryRepository,
                organizationRepository,
                authorizationServerConfigurationCommandRepository,
                userCommandRepository,
                auditLogWriters),
            OrgTenantManagementApi.class,
            databaseTypeProvider);

    this.orgClientManagementApi =
        OrganizationAwareEntryServiceProxy.createProxy(
            new OrgClientManagementEntryService(
                tenantQueryRepository,
                organizationRepository,
                clientConfigurationCommandRepository,
                clientConfigurationQueryRepository,
                auditLogWriters),
            OrgClientManagementApi.class,
            databaseTypeProvider);

    this.orgUserManagementApi =
        OrganizationAwareEntryServiceProxy.createProxy(
            new OrgUserManagementEntryService(
                tenantQueryRepository,
                organizationRepository,
                userQueryRepository,
                userCommandRepository,
                roleQueryRepository,
                passwordEncodeDelegation,
                userLifecycleEventPublisher,
                auditLogWriters),
            OrgUserManagementApi.class,
            databaseTypeProvider);

    this.orgSecurityEventManagementApi =
        OrganizationAwareEntryServiceProxy.createProxy(
            new OrgSecurityEventManagementEntryService(
                tenantQueryRepository,
                organizationRepository,
                securityEventQueryRepository,
                auditLogWriters),
            OrgSecurityEventManagementApi.class,
            databaseTypeProvider);

    this.orgAuthenticationConfigManagementApi =
        OrganizationAwareEntryServiceProxy.createProxy(
            new OrgAuthenticationConfigManagementEntryService(
                tenantQueryRepository,
                organizationRepository,
                authenticationConfigurationCommandRepository,
                authenticationConfigurationQueryRepository,
                auditLogWriters),
            OrgAuthenticationConfigManagementApi.class,
            databaseTypeProvider);

    this.orgFederationConfigManagementApi =
        OrganizationAwareEntryServiceProxy.createProxy(
            new OrgFederationConfigManagementEntryService(
                tenantQueryRepository,
                organizationRepository,
                federationConfigurationCommandRepository,
                federationConfigurationQueryRepository,
                auditLogWriters),
            OrgFederationConfigManagementApi.class,
            databaseTypeProvider);

    this.orgSecurityEventHookConfigManagementApi =
        OrganizationAwareEntryServiceProxy.createProxy(
            new OrgSecurityEventHookConfigManagementEntryService(
                tenantQueryRepository,
                organizationRepository,
                securityEventHookConfigurationCommandRepository,
                securityEventHookConfigurationQueryRepository,
                auditLogWriters),
            OrgSecurityEventHookConfigManagementApi.class,
            databaseTypeProvider);

    this.orgAuthenticationInteractionManagementApi =
        OrganizationAwareEntryServiceProxy.createProxy(
            new OrgAuthenticationInteractionManagementEntryService(
                tenantQueryRepository,
                organizationRepository,
                authenticationInteractionQueryRepository,
                auditLogWriters),
            OrgAuthenticationInteractionManagementApi.class,
            databaseTypeProvider);

    this.orgAuthenticationTransactionManagementApi =
        OrganizationAwareEntryServiceProxy.createProxy(
            new OrgAuthenticationTransactionManagementEntryService(
                tenantQueryRepository,
                organizationRepository,
                authenticationTransactionQueryRepository,
                auditLogWriters),
            OrgAuthenticationTransactionManagementApi.class,
            databaseTypeProvider);

    this.orgAuthorizationServerManagementApi =
        OrganizationAwareEntryServiceProxy.createProxy(
            new OrgAuthorizationServerManagementEntryService(
                tenantQueryRepository,
                organizationRepository,
                authorizationServerConfigurationQueryRepository,
                authorizationServerConfigurationCommandRepository,
                auditLogWriters),
            OrgAuthorizationServerManagementApi.class,
            databaseTypeProvider);

    this.orgPermissionManagementApi =
        OrganizationAwareEntryServiceProxy.createProxy(
            new OrgPermissionManagementEntryService(
                tenantQueryRepository,
                organizationRepository,
                permissionQueryRepository,
                permissionCommandRepository,
                auditLogWriters),
            OrgPermissionManagementApi.class,
            databaseTypeProvider);

    this.orgRoleManagementApi =
        OrganizationAwareEntryServiceProxy.createProxy(
            new OrgRoleManagementEntryService(
                tenantQueryRepository,
                organizationRepository,
                roleQueryRepository,
                roleCommandRepository,
                permissionQueryRepository,
                auditLogWriters),
            OrgRoleManagementApi.class,
            databaseTypeProvider);

    this.orgAuthenticationPolicyConfigManagementApi =
        OrganizationAwareEntryServiceProxy.createProxy(
            new OrgAuthenticationPolicyConfigManagementEntryService(
                tenantQueryRepository,
                organizationRepository,
                authenticationPolicyConfigurationCommandRepository,
                authenticationPolicyConfigurationQueryRepository,
                auditLogWriters),
            OrgAuthenticationPolicyConfigManagementApi.class,
            databaseTypeProvider);

    this.orgIdentityVerificationConfigManagementApi =
        OrganizationAwareEntryServiceProxy.createProxy(
            new OrgIdentityVerificationConfigManagementEntryService(
                tenantQueryRepository,
                organizationRepository,
                identityVerificationConfigurationCommandRepository,
                identityVerificationConfigurationQueryRepository,
                auditLogWriters),
            OrgIdentityVerificationConfigManagementApi.class,
            databaseTypeProvider);

    this.orgAuditLogManagementApi =
        OrganizationAwareEntryServiceProxy.createProxy(
            new OrgAuditLogManagementEntryService(
                tenantQueryRepository,
                organizationRepository,
                auditLogQueryRepository,
                auditLogWriters),
            OrgAuditLogManagementApi.class,
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

  public TenantMetaDataApi tenantMetadataApi() {
    return tenantMetaDataApi;
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

  public OnboardingApi onboardingApi() {
    return onboardingApi;
  }

  public OrganizationInitializationApi tenantInitializationApi() {
    return organizationInitializationApi;
  }

  public TenantManagementApi tenantManagementApi() {
    return tenantManagementApi;
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

  public AuthenticationTransactionManagementApi authenticationTransactionManagementApi() {
    return authenticationTransactionManagementApi;
  }

  public OrganizationUserAuthenticationApi organizationUserAuthenticationApi() {
    return organizationUserAuthenticationApi;
  }

  public OrgTenantManagementApi orgTenantManagementApi() {
    return orgTenantManagementApi;
  }

  public OrgClientManagementApi orgClientManagementApi() {
    return orgClientManagementApi;
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
}
