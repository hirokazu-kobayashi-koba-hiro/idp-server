package org.idp.server;

import org.idp.server.basic.crypto.AesCipher;
import org.idp.server.basic.crypto.HmacHasher;
import org.idp.server.basic.datasource.*;
import org.idp.server.basic.datasource.cache.CacheStore;
import org.idp.server.basic.dependency.ApplicationComponentContainer;
import org.idp.server.basic.dependency.ApplicationComponentContainerLoader;
import org.idp.server.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.basic.dependency.protocol.ProtocolContainer;
import org.idp.server.basic.dependency.protocol.ProtocolContainerLoader;
import org.idp.server.control_plane.admin.starter.IdpServerStarterApi;
import org.idp.server.control_plane.admin.tenant.TenantInitializationApi;
import org.idp.server.control_plane.base.definition.DefinitionReader;
import org.idp.server.control_plane.base.schema.SchemaReader;
import org.idp.server.control_plane.management.authentication.AuthenticationConfigurationManagementApi;
import org.idp.server.control_plane.management.federation.FederationConfigurationManagementApi;
import org.idp.server.control_plane.management.identity.user.UserManagementApi;
import org.idp.server.control_plane.management.identity.verification.IdentityVerificationConfigManagementApi;
import org.idp.server.control_plane.management.oidc.authorization.AuthorizationServerManagementApi;
import org.idp.server.control_plane.management.oidc.client.ClientManagementApi;
import org.idp.server.control_plane.management.onboarding.OnboardingApi;
import org.idp.server.control_plane.management.security.hook.SecurityEventHookConfigurationManagementApi;
import org.idp.server.core.authentication.*;
import org.idp.server.core.authentication.device.AuthenticationDeviceApi;
import org.idp.server.core.authentication.device.AuthenticationDeviceNotifiers;
import org.idp.server.core.authentication.device.AuthenticationDeviceNotifiersLoader;
import org.idp.server.core.authentication.factory.AuthenticationDependencyContainer;
import org.idp.server.core.authentication.factory.AuthenticationDependencyContainerLoader;
import org.idp.server.core.authentication.factory.AuthenticationInteractorLoader;
import org.idp.server.core.authentication.fidouaf.FidoUafExecutorLoader;
import org.idp.server.core.authentication.fidouaf.FidoUafExecutors;
import org.idp.server.core.authentication.notification.EmailSenderLoader;
import org.idp.server.core.authentication.notification.EmailSenders;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationCommandRepository;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.authentication.repository.AuthenticationTransactionCommandRepository;
import org.idp.server.core.authentication.repository.AuthenticationTransactionQueryRepository;
import org.idp.server.core.authentication.sms.SmsAuthenticationExecutorLoader;
import org.idp.server.core.authentication.sms.SmsAuthenticationExecutors;
import org.idp.server.core.authentication.webauthn.WebAuthnExecutorLoader;
import org.idp.server.core.authentication.webauthn.WebAuthnExecutors;
import org.idp.server.core.ciba.CibaFlowApi;
import org.idp.server.core.ciba.CibaProtocol;
import org.idp.server.core.ciba.CibaProtocols;
import org.idp.server.core.federation.FederationInteractors;
import org.idp.server.core.federation.factory.FederationDependencyContainer;
import org.idp.server.core.federation.factory.FederationDependencyContainerLoader;
import org.idp.server.core.federation.factory.FederationInteractorLoader;
import org.idp.server.core.federation.repository.FederationConfigurationCommandRepository;
import org.idp.server.core.federation.repository.FederationConfigurationQueryRepository;
import org.idp.server.core.federation.sso.oidc.OidcSsoExecutorLoader;
import org.idp.server.core.federation.sso.oidc.OidcSsoExecutors;
import org.idp.server.core.identity.*;
import org.idp.server.core.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.identity.authentication.PasswordVerificationDelegation;
import org.idp.server.core.identity.authentication.UserPasswordAuthenticator;
import org.idp.server.core.identity.event.*;
import org.idp.server.core.identity.permission.PermissionCommandRepository;
import org.idp.server.core.identity.repository.UserCommandRepository;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.identity.role.RoleCommandRepository;
import org.idp.server.core.identity.verification.IdentityVerificationApi;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationCommandRepository;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationQueryRepository;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfigurationCommandRepository;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfigurationQueryRepository;
import org.idp.server.core.identity.verification.result.IdentityVerificationResultCommandRepository;
import org.idp.server.core.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.core.multi_tenancy.tenant.*;
import org.idp.server.core.oidc.OAuthFlowApi;
import org.idp.server.core.oidc.OAuthProtocol;
import org.idp.server.core.oidc.OAuthProtocols;
import org.idp.server.core.oidc.OAuthSessionDelegate;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationCommandRepository;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationCommandRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.discovery.*;
import org.idp.server.core.oidc.userinfo.UserinfoApi;
import org.idp.server.core.oidc.userinfo.UserinfoProtocol;
import org.idp.server.core.oidc.userinfo.UserinfoProtocols;
import org.idp.server.core.security.SecurityEventApi;
import org.idp.server.core.security.SecurityEventHooks;
import org.idp.server.core.security.SecurityEventPublisher;
import org.idp.server.core.security.event.CibaFlowEventPublisher;
import org.idp.server.core.security.event.OAuthFlowEventPublisher;
import org.idp.server.core.security.event.TokenEventPublisher;
import org.idp.server.core.security.hook.SecurityEventHooksLoader;
import org.idp.server.core.security.repository.SecurityEventCommandRepository;
import org.idp.server.core.security.repository.SecurityEventHookConfigurationCommandRepository;
import org.idp.server.core.security.repository.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.core.security.repository.SecurityEventHookResultCommandRepository;
import org.idp.server.core.token.*;
import org.idp.server.usecases.application.*;
import org.idp.server.usecases.control_plane.*;

/** IdpServerApplication */
public class IdpServerApplication {

  IdpServerStarterApi idpServerStarterApi;
  OAuthFlowApi oAuthFlowApi;
  TokenApi tokenApi;
  OidcMetaDataApi oidcMetaDataApi;
  UserinfoApi userinfoApi;
  CibaFlowApi cibaFlowApi;
  AuthenticationMetaDataApi authenticationMetaDataApi;
  AuthenticationDeviceApi authenticationDeviceApi;
  IdentityVerificationApi identityVerificationApi;
  SecurityEventApi securityEventApi;
  TenantMetaDataApi tenantMetaDataApi;
  UserOperationApi userOperationApi;
  UserLifecycleEventApi userLifecycleEventApi;
  OnboardingApi onboardingApi;
  TenantInitializationApi tenantInitializationApi;
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
        ApplicationComponentContainerLoader.load(dependencyContainer);
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
        ProtocolContainerLoader.load(applicationComponentContainer);

    SecurityEventHooks securityEventHooks = SecurityEventHooksLoader.load();

    // create mfa instance
    AuthenticationDependencyContainer authenticationDependencyContainer =
        AuthenticationDependencyContainerLoader.load();
    authenticationDependencyContainer.register(
        PasswordEncodeDelegation.class, passwordEncodeDelegation);
    authenticationDependencyContainer.register(
        PasswordVerificationDelegation.class, passwordVerificationDelegation);
    EmailSenders emailSenders = EmailSenderLoader.load();
    authenticationDependencyContainer.register(EmailSenders.class, emailSenders);
    WebAuthnExecutors webAuthnExecutors =
        WebAuthnExecutorLoader.load(authenticationDependencyContainer);
    authenticationDependencyContainer.register(WebAuthnExecutors.class, webAuthnExecutors);
    AuthenticationDeviceNotifiers authenticationDeviceNotifiers =
        AuthenticationDeviceNotifiersLoader.load();
    authenticationDependencyContainer.register(
        AuthenticationDeviceNotifiers.class, authenticationDeviceNotifiers);

    FidoUafExecutors fidoUafExecutors =
        FidoUafExecutorLoader.load(authenticationDependencyContainer);
    authenticationDependencyContainer.register(FidoUafExecutors.class, fidoUafExecutors);

    SmsAuthenticationExecutors smsAuthenticationExecutors =
        SmsAuthenticationExecutorLoader.load(authenticationDependencyContainer);
    authenticationDependencyContainer.register(
        SmsAuthenticationExecutors.class, smsAuthenticationExecutors);

    AuthenticationInteractors authenticationInteractors =
        AuthenticationInteractorLoader.load(authenticationDependencyContainer);

    UserLifecycleEventExecutorsMap userLifecycleEventExecutorsMap =
        UserLifecycleEventExecutorLoader.load(
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

    OidcSsoExecutors oidcSsoExecutors = OidcSsoExecutorLoader.load();
    FederationDependencyContainer federationDependencyContainer =
        FederationDependencyContainerLoader.load();
    federationDependencyContainer.register(OidcSsoExecutors.class, oidcSsoExecutors);
    FederationInteractors federationInteractors =
        FederationInteractorLoader.load(federationDependencyContainer);

    SchemaReader.initialValidate();
    DefinitionReader.initialValidate();

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
                userLifecycleEventPublisher),
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

    this.authenticationDeviceApi =
        TenantAwareEntryServiceProxy.createProxy(
            new AuthenticationDeviceEntryService(
                tenantQueryRepository, authenticationTransactionQueryRepository),
            AuthenticationDeviceApi.class,
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

    this.userOperationApi =
        TenantAwareEntryServiceProxy.createProxy(
            new UserOperationEntryService(
                userCommandRepository,
                tenantQueryRepository,
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

  public AuthenticationDeviceApi authenticationDeviceApi() {
    return authenticationDeviceApi;
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
