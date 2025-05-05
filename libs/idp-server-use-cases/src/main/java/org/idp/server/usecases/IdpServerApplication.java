package org.idp.server.usecases;

import org.idp.server.basic.crypto.AesCipher;
import org.idp.server.basic.crypto.HmacHasher;
import org.idp.server.basic.datasource.*;
import org.idp.server.basic.dependency.ApplicationComponentContainer;
import org.idp.server.basic.dependency.ApplicationComponentContainerLoader;
import org.idp.server.basic.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.basic.dependency.protocol.ProtocolContainer;
import org.idp.server.basic.dependency.protocol.ProtocolContainerLoader;
import org.idp.server.core.admin.*;
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
import org.idp.server.core.federation.FederationDependencyContainer;
import org.idp.server.core.federation.FederationDependencyContainerLoader;
import org.idp.server.core.federation.FederationInteractorLoader;
import org.idp.server.core.federation.FederationInteractors;
import org.idp.server.core.federation.oidc.OidcSsoExecutorLoader;
import org.idp.server.core.federation.oidc.OidcSsoExecutors;
import org.idp.server.core.identity.*;
import org.idp.server.core.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.identity.authentication.PasswordVerificationDelegation;
import org.idp.server.core.identity.authentication.UserPasswordAuthenticator;
import org.idp.server.core.identity.permission.PermissionCommandRepository;
import org.idp.server.core.identity.repository.UserCommandRepository;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.identity.role.RoleCommandRepository;
import org.idp.server.core.identity.verification.IdentityVerificationApi;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationCommandRepository;
import org.idp.server.core.identity.verification.application.IdentityVerificationApplicationQueryRepository;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfigurationQueryRepository;
import org.idp.server.core.identity.verification.result.IdentityVerificationResultCommandRepository;
import org.idp.server.core.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.core.multi_tenancy.tenant.AdminTenantContext;
import org.idp.server.core.multi_tenancy.tenant.TenantDialectProvider;
import org.idp.server.core.multi_tenancy.tenant.TenantRepository;
import org.idp.server.core.oidc.OAuthFlowApi;
import org.idp.server.core.oidc.OAuthProtocol;
import org.idp.server.core.oidc.OAuthProtocols;
import org.idp.server.core.oidc.OAuthSessionDelegate;
import org.idp.server.core.oidc.configuration.ClientConfigurationRepository;
import org.idp.server.core.oidc.configuration.ServerConfigurationRepository;
import org.idp.server.core.oidc.configuration.handler.ClientConfigurationHandler;
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
import org.idp.server.core.security.repository.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.core.security.repository.SecurityEventHookResultCommandRepository;
import org.idp.server.core.token.*;

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
  UserApi userApi;
  UserLifecycleEventApi userLifecycleEventApi;
  OnboardingApi onboardingApi;
  ServerManagementApi serverManagementApi;
  ClientManagementApi clientManagementApi;
  UserManagementApi userManagementApi;
  OperatorAuthenticationApi operatorAuthenticationApi;

  public IdpServerApplication(
      String adminTenantId,
      DbConnectionProvider dbConnectionProvider,
      String encryptionKey,
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
    ApplicationComponentContainer applicationComponentContainer =
        ApplicationComponentContainerLoader.load(dependencyContainer);
    applicationComponentContainer.register(OAuthSessionDelegate.class, oAuthSessionDelegate);

    ServerConfigurationRepository serverConfigurationRepository =
        applicationComponentContainer.resolve(ServerConfigurationRepository.class);
    ClientConfigurationRepository clientConfigurationRepository =
        applicationComponentContainer.resolve(ClientConfigurationRepository.class);
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
    TenantRepository tenantRepository =
        applicationComponentContainer.resolve(TenantRepository.class);
    AuthenticationTransactionCommandRepository authenticationTransactionCommandRepository =
        applicationComponentContainer.resolve(AuthenticationTransactionCommandRepository.class);
    AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository =
        applicationComponentContainer.resolve(AuthenticationTransactionQueryRepository.class);
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

    RoleCommandRepository roleCommandRepository =
        applicationComponentContainer.resolve(RoleCommandRepository.class);
    PermissionCommandRepository permissionCommandRepository =
        applicationComponentContainer.resolve(PermissionCommandRepository.class);
    SecurityEventHookConfigurationQueryRepository hookQueryRepository =
        applicationComponentContainer.resolve(SecurityEventHookConfigurationQueryRepository.class);

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

    TenantDialectProvider tenantDialectProvider = new TenantDialectProvider(tenantRepository);

    this.idpServerStarterApi =
        TenantAwareEntryServiceProxy.createProxy(
            new IdpServerStarterEntryService(
                organizationRepository,
                tenantRepository,
                userQueryRepository,
                permissionCommandRepository,
                roleCommandRepository,
                serverConfigurationRepository,
                passwordEncodeDelegation),
            IdpServerStarterApi.class,
            OperationType.WRITE,
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

    this.oAuthFlowApi =
        TenantAwareEntryServiceProxy.createProxy(
            new OAuthFlowEntryService(
                new OAuthProtocols(protocolContainer.resolveAll(OAuthProtocol.class)),
                oAuthSessionDelegate,
                authenticationInteractors,
                federationInteractors,
                userQueryRepository,
                tenantRepository,
                authenticationTransactionCommandRepository,
                authenticationTransactionQueryRepository,
                oAuthFLowEventPublisher),
            OAuthFlowApi.class,
            OperationType.WRITE,
            tenantDialectProvider);

    this.tokenApi =
        TenantAwareEntryServiceProxy.createProxy(
            new TokenEntryService(
                new TokenProtocols(protocolContainer.resolveAll(TokenProtocol.class)),
                userQueryRepository,
                tenantRepository,
                tokenEventPublisher),
            TokenApi.class,
            OperationType.WRITE,
            tenantDialectProvider);

    this.oidcMetaDataApi =
        TenantAwareEntryServiceProxy.createProxy(
            new OidcMetaDataEntryService(
                tenantRepository,
                new DiscoveryProtocols(protocolContainer.resolveAll(DiscoveryProtocol.class))),
            OidcMetaDataApi.class,
            OperationType.WRITE,
            tenantDialectProvider);

    this.userinfoApi =
        TenantAwareEntryServiceProxy.createProxy(
            new UserinfoEntryService(
                new UserinfoProtocols(protocolContainer.resolveAll(UserinfoProtocol.class)),
                userQueryRepository,
                tenantRepository,
                tokenEventPublisher),
            UserinfoApi.class,
            OperationType.WRITE,
            tenantDialectProvider);

    this.cibaFlowApi =
        TenantAwareEntryServiceProxy.createProxy(
            new CibaFlowEntryService(
                new CibaProtocols(protocolContainer.resolveAll(CibaProtocol.class)),
                authenticationInteractors,
                userQueryRepository,
                tenantRepository,
                authenticationTransactionCommandRepository,
                authenticationTransactionQueryRepository,
                cibaFlowEventPublisher),
            CibaFlowApi.class,
            OperationType.WRITE,
            tenantDialectProvider);

    this.authenticationMetaDataApi =
        TenantAwareEntryServiceProxy.createProxy(
            new AuthenticationMetaDataEntryService(
                authenticationDependencyContainer.resolve(
                    AuthenticationConfigurationQueryRepository.class),
                fidoUafExecutors,
                tenantRepository),
            AuthenticationMetaDataApi.class,
            OperationType.WRITE,
            tenantDialectProvider);

    this.authenticationDeviceApi =
        TenantAwareEntryServiceProxy.createProxy(
            new AuthenticationDeviceEntryService(
                tenantRepository, authenticationTransactionQueryRepository),
            AuthenticationDeviceApi.class,
            OperationType.READ,
            tenantDialectProvider);

    this.identityVerificationApi =
        TenantAwareEntryServiceProxy.createProxy(
            new IdentityVerificationEntryService(
                identityVerificationConfigurationQueryRepository,
                identityVerificationApplicationCommandRepository,
                identityVerificationApplicationQueryRepository,
                identityVerificationResultCommandRepository,
                tenantRepository,
                userQueryRepository,
                tokenEventPublisher),
            IdentityVerificationApi.class,
            OperationType.WRITE,
            tenantDialectProvider);

    this.securityEventApi =
        TenantAwareEntryServiceProxy.createProxy(
            new SecurityEventEntryService(
                securityEventHooks,
                securityEventCommandRepository,
                securityEventHookResultCommandRepository,
                hookQueryRepository,
                tenantRepository),
            SecurityEventApi.class,
            OperationType.WRITE,
            tenantDialectProvider);

    this.userApi =
        TenantAwareEntryServiceProxy.createProxy(
            new UserEntryService(
                userCommandRepository,
                tenantRepository,
                tokenEventPublisher,
                userLifecycleEventPublisher),
            UserApi.class,
            OperationType.WRITE,
            tenantDialectProvider);

    this.userLifecycleEventApi =
        TenantAwareEntryServiceProxy.createProxy(
            new UserLifecycleEventEntryService(),
            UserLifecycleEventApi.class,
            OperationType.WRITE,
            tenantDialectProvider);

    this.onboardingApi =
        TenantAwareEntryServiceProxy.createProxy(
            new OnboardingEntryService(
                tenantRepository,
                organizationRepository,
                userQueryRepository,
                serverConfigurationRepository),
            OnboardingApi.class,
            OperationType.WRITE,
            tenantDialectProvider);

    this.serverManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new ServerManagementEntryService(tenantRepository, serverConfigurationRepository),
            ServerManagementApi.class,
            OperationType.WRITE,
            tenantDialectProvider);

    this.clientManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new ClientManagementEntryService(
                tenantRepository, new ClientConfigurationHandler(clientConfigurationRepository)),
            ClientManagementApi.class,
            OperationType.WRITE,
            tenantDialectProvider);

    this.userManagementApi =
        TenantAwareEntryServiceProxy.createProxy(
            new UserManagementEntryService(tenantRepository, userQueryRepository),
            UserManagementApi.class,
            OperationType.WRITE,
            tenantDialectProvider);

    this.operatorAuthenticationApi =
        TenantAwareEntryServiceProxy.createProxy(
            new OperatorAuthenticationEntryService(
                new TokenProtocols(protocolContainer.resolveAll(TokenProtocol.class)),
                tenantRepository,
                userQueryRepository),
            OperatorAuthenticationApi.class,
            OperationType.WRITE,
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

  public UserApi userApi() {
    return userApi;
  }

  public UserLifecycleEventApi userLifecycleEventApi() {
    return userLifecycleEventApi;
  }

  public OnboardingApi onboardingApi() {
    return onboardingApi;
  }

  public ServerManagementApi serverManagementApi() {
    return serverManagementApi;
  }

  public ClientManagementApi clientManagementApi() {
    return clientManagementApi;
  }

  public UserManagementApi userManagementAPi() {
    return userManagementApi;
  }

  public OperatorAuthenticationApi operatorAuthenticationApi() {
    return operatorAuthenticationApi;
  }

  public IdpServerStarterApi idpServerStarterApi() {
    return idpServerStarterApi;
  }
}
