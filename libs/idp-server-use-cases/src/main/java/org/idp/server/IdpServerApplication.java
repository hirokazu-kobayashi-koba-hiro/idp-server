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

import org.idp.server.authentication.interactors.device.AuthenticationDeviceNotifiers;
import org.idp.server.authentication.interactors.fidouaf.AuthenticationMetaDataApi;
import org.idp.server.authentication.interactors.fidouaf.FidoUafExecutors;
import org.idp.server.authentication.interactors.fidouaf.plugin.FidoUafAdditionalRequestResolvers;
import org.idp.server.authentication.interactors.plugin.AuthenticationDeviceNotifiersPluginLoader;
import org.idp.server.authentication.interactors.plugin.FidoUafAdditionalRequestResolverPluginLoader;
import org.idp.server.authentication.interactors.plugin.FidoUafExecutorPluginLoader;
import org.idp.server.authentication.interactors.plugin.SmsAuthenticationExecutorPluginLoader;
import org.idp.server.authentication.interactors.plugin.WebAuthnExecutorPluginLoader;
import org.idp.server.authentication.interactors.sms.SmsAuthenticationExecutors;
import org.idp.server.authentication.interactors.webauthn.WebAuthnExecutors;
import org.idp.server.basic.crypto.AesCipher;
import org.idp.server.basic.crypto.HmacHasher;
import org.idp.server.control_plane.admin.starter.IdpServerStarterApi;
import org.idp.server.control_plane.admin.tenant.TenantInitializationApi;
import org.idp.server.control_plane.base.AdminDashboardUrl;
import org.idp.server.control_plane.base.schema.SchemaReader;
import org.idp.server.control_plane.management.authentication.AuthenticationConfigurationManagementApi;
import org.idp.server.control_plane.management.federation.FederationConfigurationManagementApi;
import org.idp.server.control_plane.management.identity.user.UserManagementApi;
import org.idp.server.control_plane.management.identity.verification.IdentityVerificationConfigManagementApi;
import org.idp.server.control_plane.management.oidc.authorization.AuthorizationServerManagementApi;
import org.idp.server.control_plane.management.oidc.client.ClientManagementApi;
import org.idp.server.control_plane.management.onboarding.OnboardingApi;
import org.idp.server.control_plane.management.security.hook.SecurityEventHookConfigurationManagementApi;
import org.idp.server.control_plane.management.tenant.TenantManagementApi;
import org.idp.server.control_plane.management.tenant.invitation.TenantInvitationManagementApi;
import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitationCommandRepository;
import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitationMetaDataApi;
import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitationQueryRepository;
import org.idp.server.core.extension.ciba.CibaFlowApi;
import org.idp.server.core.extension.ciba.CibaFlowEventPublisher;
import org.idp.server.core.extension.ciba.CibaProtocol;
import org.idp.server.core.extension.ciba.CibaProtocols;
import org.idp.server.core.extension.identity.verification.IdentityVerificationApi;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplicationCommandRepository;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplicationQueryRepository;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfigurationCommandRepository;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfigurationQueryRepository;
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationResultCommandRepository;
import org.idp.server.core.oidc.*;
import org.idp.server.core.oidc.authentication.AuthenticationApi;
import org.idp.server.core.oidc.authentication.AuthenticationInteractors;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationCommandRepository;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.oidc.authentication.repository.AuthenticationTransactionCommandRepository;
import org.idp.server.core.oidc.authentication.repository.AuthenticationTransactionQueryRepository;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationCommandRepository;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationCommandRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.discovery.*;
import org.idp.server.core.oidc.federation.FederationInteractors;
import org.idp.server.core.oidc.federation.plugin.FederationDependencyContainer;
import org.idp.server.core.oidc.federation.repository.FederationConfigurationCommandRepository;
import org.idp.server.core.oidc.federation.repository.FederationConfigurationQueryRepository;
import org.idp.server.core.oidc.identity.*;
import org.idp.server.core.oidc.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.oidc.identity.authentication.PasswordVerificationDelegation;
import org.idp.server.core.oidc.identity.authentication.UserPasswordAuthenticator;
import org.idp.server.core.oidc.identity.event.*;
import org.idp.server.core.oidc.identity.permission.PermissionCommandRepository;
import org.idp.server.core.oidc.identity.repository.UserCommandRepository;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.core.oidc.identity.role.RoleCommandRepository;
import org.idp.server.core.oidc.plugin.AuthenticationDependencyContainerPluginLoader;
import org.idp.server.core.oidc.plugin.FederationDependencyContainerPluginLoader;
import org.idp.server.core.oidc.plugin.UserLifecycleEventExecutorPluginLoader;
import org.idp.server.core.oidc.plugin.authentication.AuthenticationInteractorPluginLoader;
import org.idp.server.core.oidc.plugin.authentication.FederationInteractorPluginLoader;
import org.idp.server.core.oidc.token.*;
import org.idp.server.core.oidc.userinfo.UserinfoApi;
import org.idp.server.core.oidc.userinfo.UserinfoProtocol;
import org.idp.server.core.oidc.userinfo.UserinfoProtocols;
import org.idp.server.federation.sso.oidc.OidcSsoExecutorPluginLoader;
import org.idp.server.federation.sso.oidc.OidcSsoExecutors;
import org.idp.server.platform.datasource.*;
import org.idp.server.platform.datasource.cache.CacheStore;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.protocol.ProtocolContainer;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.*;
import org.idp.server.platform.notification.EmailSenders;
import org.idp.server.platform.plugin.ApplicationComponentContainerPluginLoader;
import org.idp.server.platform.plugin.EmailSenderPluginLoader;
import org.idp.server.platform.plugin.ProtocolContainerPluginLoader;
import org.idp.server.platform.plugin.SecurityEventHooksPluginLoader;
import org.idp.server.platform.proxy.TenantAwareEntryServiceProxy;
import org.idp.server.platform.security.SecurityEventApi;
import org.idp.server.platform.security.SecurityEventHooks;
import org.idp.server.platform.security.SecurityEventPublisher;
import org.idp.server.platform.security.repository.SecurityEventCommandRepository;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationCommandRepository;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.platform.security.repository.SecurityEventHookResultCommandRepository;
import org.idp.server.usecases.application.enduser.*;
import org.idp.server.usecases.application.relying_party.OidcMetaDataEntryService;
import org.idp.server.usecases.application.system.*;
import org.idp.server.usecases.application.tenant_invitator.TenantInvitationMetaDataEntryService;
import org.idp.server.usecases.control_plane.system_administrator.IdpServerStarterEntryService;
import org.idp.server.usecases.control_plane.system_administrator.TenantInitializationEntryService;
import org.idp.server.usecases.control_plane.tenant_manager.*;

/** IdpServerApplication */
public class IdpServerApplication {

  IdpServerStarterApi idpServerStarterApi;
  OAuthFlowApi oAuthFlowApi;
  TokenApi tokenApi;
  OidcMetaDataApi oidcMetaDataApi;
  UserinfoApi userinfoApi;
  CibaFlowApi cibaFlowApi;
  AuthenticationMetaDataApi authenticationMetaDataApi;
  AuthenticationApi authenticationApi;
  IdentityVerificationApi identityVerificationApi;
  SecurityEventApi securityEventApi;
  TenantMetaDataApi tenantMetaDataApi;
  TenantInvitationMetaDataApi tenantInvitationMetaDataApi;
  UserOperationApi userOperationApi;
  UserLifecycleEventApi userLifecycleEventApi;
  OnboardingApi onboardingApi;
  TenantInitializationApi tenantInitializationApi;
  TenantManagementApi tenantManagementApi;
  TenantInvitationManagementApi tenantInvitationManagementApi;
  AuthorizationServerManagementApi authorizationServerManagementApi;
  ClientManagementApi clientManagementApi;
  UserManagementApi userManagementApi;
  AuthenticationConfigurationManagementApi authenticationConfigurationManagementApi;
  FederationConfigurationManagementApi federationConfigurationManagementApi;
  IdentityVerificationConfigManagementApi identityVerificationConfigManagementApi;
  SecurityEventHookConfigurationManagementApi securityEventHookConfigurationManagementApi;
  UserAuthenticationApi userAuthenticationApi;

  public IdpServerApplication(
      String adminTenantId,
      AdminDashboardUrl adminDashboardUrl,
      DbConnectionProvider dbConnectionProvider,
      String encryptionKey,
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
    AesCipher aesCipher = new AesCipher(encryptionKey);
    HmacHasher hmacHasher = new HmacHasher(encryptionKey);
    dependencyContainer.register(AesCipher.class, aesCipher);
    dependencyContainer.register(HmacHasher.class, hmacHasher);
    dependencyContainer.register(CacheStore.class, cacheStore);
    ApplicationComponentContainer applicationComponentContainer =
        ApplicationComponentContainerPluginLoader.load(dependencyContainer);
    applicationComponentContainer.register(OAuthSessionDelegate.class, oAuthSessionDelegate);

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
    PermissionCommandRepository permissionCommandRepository =
        applicationComponentContainer.resolve(PermissionCommandRepository.class);
    SecurityEventHookConfigurationQueryRepository hookQueryRepository =
        applicationComponentContainer.resolve(SecurityEventHookConfigurationQueryRepository.class);
    UserLifecycleEventResultCommandRepository userLifecycleEventResultCommandRepository =
        applicationComponentContainer.resolve(UserLifecycleEventResultCommandRepository.class);

    applicationComponentContainer.register(
        PasswordCredentialsGrantDelegate.class,
        new UserPasswordAuthenticator(userQueryRepository, passwordVerificationDelegation));

    ProtocolContainer protocolContainer =
        ProtocolContainerPluginLoader.load(applicationComponentContainer);

    SecurityEventHooks securityEventHooks = SecurityEventHooksPluginLoader.load();

    // create authentication-interactor instance
    AuthenticationDependencyContainer authenticationDependencyContainer =
        AuthenticationDependencyContainerPluginLoader.load();
    authenticationDependencyContainer.register(
        PasswordEncodeDelegation.class, passwordEncodeDelegation);
    authenticationDependencyContainer.register(
        PasswordVerificationDelegation.class, passwordVerificationDelegation);
    EmailSenders emailSenders = EmailSenderPluginLoader.load();
    authenticationDependencyContainer.register(EmailSenders.class, emailSenders);
    WebAuthnExecutors webAuthnExecutors =
        WebAuthnExecutorPluginLoader.load(authenticationDependencyContainer);
    authenticationDependencyContainer.register(WebAuthnExecutors.class, webAuthnExecutors);
    AuthenticationDeviceNotifiers authenticationDeviceNotifiers =
        AuthenticationDeviceNotifiersPluginLoader.load();
    authenticationDependencyContainer.register(
        AuthenticationDeviceNotifiers.class, authenticationDeviceNotifiers);

    FidoUafExecutors fidoUafExecutors =
        FidoUafExecutorPluginLoader.load(authenticationDependencyContainer);
    authenticationDependencyContainer.register(FidoUafExecutors.class, fidoUafExecutors);
    FidoUafAdditionalRequestResolvers fidoUafAdditionalRequestResolvers =
        FidoUafAdditionalRequestResolverPluginLoader.load();
    authenticationDependencyContainer.register(
        FidoUafAdditionalRequestResolvers.class, fidoUafAdditionalRequestResolvers);

    SmsAuthenticationExecutors smsAuthenticationExecutors =
        SmsAuthenticationExecutorPluginLoader.load(authenticationDependencyContainer);
    authenticationDependencyContainer.register(
        SmsAuthenticationExecutors.class, smsAuthenticationExecutors);

    AuthenticationInteractors authenticationInteractors =
        AuthenticationInteractorPluginLoader.load(authenticationDependencyContainer);

    UserLifecycleEventExecutorsMap userLifecycleEventExecutorsMap =
        UserLifecycleEventExecutorPluginLoader.load(
            applicationComponentContainer, authenticationDependencyContainer);

    TenantDialectProvider tenantDialectProvider = new TenantDialectProvider(tenantQueryRepository);

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
            tenantDialectProvider);

    OAuthFlowEventPublisher oAuthFLowEventPublisher =
        new OAuthFlowEventPublisher(securityEventPublisher);
    CibaFlowEventPublisher cibaFlowEventPublisher =
        new CibaFlowEventPublisher(securityEventPublisher);
    TokenEventPublisher tokenEventPublisher = new TokenEventPublisher(securityEventPublisher);

    OidcSsoExecutors oidcSsoExecutors = OidcSsoExecutorPluginLoader.load();
    FederationDependencyContainer federationDependencyContainer =
        FederationDependencyContainerPluginLoader.load();
    federationDependencyContainer.register(OidcSsoExecutors.class, oidcSsoExecutors);
    FederationInteractors federationInteractors =
        FederationInteractorPluginLoader.load(federationDependencyContainer);

    SchemaReader.initialValidate();

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
                oAuthFLowEventPublisher,
                userLifecycleEventPublisher),
            OAuthFlowApi.class,
            tenantDialectProvider);

    this.tokenApi =
        TenantAwareEntryServiceProxy.createProxy(
            new TokenEntryService(
                new TokenProtocols(protocolContainer.resolveAll(TokenProtocol.class)),
                userQueryRepository,
                tenantQueryRepository,
                tokenEventPublisher),
            TokenApi.class,
            tenantDialectProvider);

    this.oidcMetaDataApi =
        TenantAwareEntryServiceProxy.createProxy(
            new OidcMetaDataEntryService(
                tenantQueryRepository,
                new DiscoveryProtocols(protocolContainer.resolveAll(DiscoveryProtocol.class))),
            OidcMetaDataApi.class,
            tenantDialectProvider);

    this.userinfoApi =
        TenantAwareEntryServiceProxy.createProxy(
            new UserinfoEntryService(
                new UserinfoProtocols(protocolContainer.resolveAll(UserinfoProtocol.class)),
                userQueryRepository,
                tenantQueryRepository,
                tokenEventPublisher),
            UserinfoApi.class,
            tenantDialectProvider);

    this.cibaFlowApi =
        TenantAwareEntryServiceProxy.createProxy(
            new CibaFlowEntryService(
                new CibaProtocols(protocolContainer.resolveAll(CibaProtocol.class)),
                authenticationInteractors,
                userQueryRepository,
                tenantQueryRepository,
                authenticationTransactionCommandRepository,
                authenticationTransactionQueryRepository,
                cibaFlowEventPublisher,
                userLifecycleEventPublisher,
                passwordVerificationDelegation),
            CibaFlowApi.class,
            tenantDialectProvider);

    this.authenticationMetaDataApi =
        TenantAwareEntryServiceProxy.createProxy(
            new AuthenticationMetaDataEntryService(
                authenticationDependencyContainer.resolve(
                    AuthenticationConfigurationQueryRepository.class),
                fidoUafExecutors,
                tenantQueryRepository),
            AuthenticationMetaDataApi.class,
            tenantDialectProvider);

    this.authenticationApi =
        TenantAwareEntryServiceProxy.createProxy(
            new AuthenticationEntryService(
                tenantQueryRepository,
                authenticationTransactionCommandRepository,
                authenticationTransactionQueryRepository),
            AuthenticationApi.class,
            tenantDialectProvider);

    this.identityVerificationApi =
        TenantAwareEntryServiceProxy.createProxy(
            new IdentityVerificationEntryService(
                identityVerificationConfigurationQueryRepository,
                identityVerificationApplicationCommandRepository,
                identityVerificationApplicationQueryRepository,
                identityVerificationResultCommandRepository,
                tenantQueryRepository,
                userQueryRepository,
                userCommandRepository,
                tokenEventPublisher),
            IdentityVerificationApi.class,
            tenantDialectProvider);

    this.securityEventApi =
        TenantAwareEntryServiceProxy.createProxy(
            new SecurityEventEntryService(
                securityEventHooks,
                securityEventCommandRepository,
                securityEventHookResultCommandRepository,
                hookQueryRepository,
                tenantQueryRepository),
            SecurityEventApi.class,
            tenantDialectProvider);

    this.tenantMetaDataApi =
        TenantAwareEntryServiceProxy.createProxy(
            new TenantMetaDataEntryService(tenantQueryRepository),
            TenantMetaDataApi.class,
            tenantDialectProvider);

    this.tenantInvitationMetaDataApi =
        TenantAwareEntryServiceProxy.createProxy(
            new TenantInvitationMetaDataEntryService(
                tenantInvitationQueryRepository, tenantQueryRepository),
            TenantInvitationMetaDataApi.class,
            tenantDialectProvider);

    this.userOperationApi =
        TenantAwareEntryServiceProxy.createProxy(
            new UserOperationEntryService(
                userQueryRepository,
                userCommandRepository,
                tenantQueryRepository,
                authenticationTransactionCommandRepository,
                authenticationTransactionQueryRepository,
                authenticationInteractors,
                tokenEventPublisher,
                userLifecycleEventPublisher),
            UserOperationApi.class,
            tenantDialectProvider);

    this.userLifecycleEventApi =
        TenantAwareEntryServiceProxy.createProxy(
            new UserLifecycleEventEntryService(
                userLifecycleEventExecutorsMap, userLifecycleEventResultCommandRepository),
            UserLifecycleEventApi.class,
            tenantDialectProvider);

    this.onboardingApi =
        TenantAwareEntryServiceProxy.createProxy(
            new OnboardingEntryService(
                tenantCommandRepository,
                tenantQueryRepository,
                organizationRepository,
                userQueryRepository,
                userCommandRepository,
                authorizationServerConfigurationCommandRepository,
                clientConfigurationCommandRepository,
                clientConfigurationQueryRepository),
            OnboardingApi.class,
            tenantDialectProvider);

    this.tenantInitializationApi =
        TenantAwareEntryServiceProxy.createProxy(
            new TenantInitializationEntryService(
                tenantCommandRepository,
                tenantQueryRepository,
                organizationRepository,
                userQueryRepository,
                userCommandRepository,
                authorizationServerConfigurationCommandRepository,
                clientConfigurationCommandRepository,
                clientConfigurationQueryRepository,
                passwordEncodeDelegation),
            TenantInitializationApi.class,
            tenantDialectProvider);

    this.tenantManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new TenantManagementEntryService(
                tenantCommandRepository,
                tenantQueryRepository,
                organizationRepository,
                authorizationServerConfigurationCommandRepository),
            TenantManagementApi.class,
            tenantDialectProvider);

    this.tenantInvitationManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new TenantInvitationManagementEntryService(
                tenantInvitationCommandRepository,
                tenantInvitationQueryRepository,
                tenantQueryRepository,
                emailSenders,
                adminDashboardUrl),
            TenantInvitationManagementApi.class,
            tenantDialectProvider);

    this.authorizationServerManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new AuthorizationServerManagementEntryService(
                tenantQueryRepository,
                authorizationServerConfigurationQueryRepository,
                authorizationServerConfigurationCommandRepository),
            AuthorizationServerManagementApi.class,
            tenantDialectProvider);

    this.clientManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new ClientManagementEntryService(
                tenantQueryRepository,
                clientConfigurationCommandRepository,
                clientConfigurationQueryRepository),
            ClientManagementApi.class,
            tenantDialectProvider);

    this.userManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new UserManagementEntryService(
                tenantQueryRepository,
                userQueryRepository,
                userCommandRepository,
                passwordEncodeDelegation,
                userLifecycleEventPublisher),
            UserManagementApi.class,
            tenantDialectProvider);

    this.authenticationConfigurationManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new AuthenticationConfigurationManagementEntryService(
                authenticationConfigurationCommandRepository,
                authenticationConfigurationQueryRepository,
                tenantQueryRepository),
            AuthenticationConfigurationManagementApi.class,
            tenantDialectProvider);

    this.federationConfigurationManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new FederationConfigurationManagementEntryService(
                federationConfigurationQueryRepository,
                federationConfigurationCommandRepository,
                tenantQueryRepository),
            FederationConfigurationManagementApi.class,
            tenantDialectProvider);

    this.securityEventHookConfigurationManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new SecurityEventHookConfigurationManagementEntryService(
                securityEventHookConfigurationCommandRepository,
                securityEventHookConfigurationQueryRepository,
                tenantQueryRepository),
            SecurityEventHookConfigurationManagementApi.class,
            tenantDialectProvider);

    this.identityVerificationConfigManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new IdentityVerificationConfigManagementEntryService(
                identityVerificationConfigurationCommandRepository,
                identityVerificationConfigurationQueryRepository,
                tenantQueryRepository),
            IdentityVerificationConfigManagementApi.class,
            tenantDialectProvider);

    this.userAuthenticationApi =
        TenantAwareEntryServiceProxy.createProxy(
            new UserAuthenticationEntryService(
                new TokenProtocols(protocolContainer.resolveAll(TokenProtocol.class)),
                tenantQueryRepository,
                userQueryRepository),
            UserAuthenticationApi.class,
            tenantDialectProvider);
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

  public AuthenticationApi authenticationApi() {
    return authenticationApi;
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

  public UserLifecycleEventApi userLifecycleEventApi() {
    return userLifecycleEventApi;
  }

  public OnboardingApi onboardingApi() {
    return onboardingApi;
  }

  public TenantInitializationApi tenantInitializationApi() {
    return tenantInitializationApi;
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

  public IdentityVerificationConfigManagementApi identityVerificationConfigManagementApi() {
    return identityVerificationConfigManagementApi;
  }

  public UserAuthenticationApi operatorAuthenticationApi() {
    return userAuthenticationApi;
  }

  public IdpServerStarterApi idpServerStarterApi() {
    return idpServerStarterApi;
  }

  public FederationConfigurationManagementApi federationConfigManagementApi() {
    return federationConfigurationManagementApi;
  }

  public SecurityEventHookConfigurationManagementApi securityEventHookConfigurationManagementApi() {
    return securityEventHookConfigurationManagementApi;
  }
}
