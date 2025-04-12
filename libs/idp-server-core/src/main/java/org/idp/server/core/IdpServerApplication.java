package org.idp.server.core;

import org.idp.server.core.admin.*;
import org.idp.server.core.authentication.*;
import org.idp.server.core.authentication.webauthn.WebAuthnExecutorLoader;
import org.idp.server.core.authentication.webauthn.WebAuthnExecutors;
import org.idp.server.core.basic.crypto.AesCipher;
import org.idp.server.core.basic.crypto.HmacHasher;
import org.idp.server.core.basic.datasource.DataSourceContainer;
import org.idp.server.core.basic.datasource.DataSourceContainerLoader;
import org.idp.server.core.basic.datasource.DataSourceDependencyContainer;
import org.idp.server.core.basic.protcol.ProtocolContainer;
import org.idp.server.core.basic.protcol.ProtocolContainerLoader;
import org.idp.server.core.basic.sql.DatabaseConfig;
import org.idp.server.core.basic.sql.TransactionInterceptor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.ciba.CibaFlowApi;
import org.idp.server.core.ciba.CibaProtocol;
import org.idp.server.core.configuration.ClientConfigurationRepository;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.configuration.handler.ClientConfigurationHandler;
import org.idp.server.core.discovery.*;
import org.idp.server.core.federation.FederationDependencyContainer;
import org.idp.server.core.federation.FederationDependencyContainerLoader;
import org.idp.server.core.federation.FederationInteractorLoader;
import org.idp.server.core.federation.FederationInteractors;
import org.idp.server.core.federation.oidc.OidcSsoExecutorLoader;
import org.idp.server.core.federation.oidc.OidcSsoExecutors;
import org.idp.server.core.notification.EmailSenderLoader;
import org.idp.server.core.notification.EmailSenders;
import org.idp.server.core.oauth.OAuthFlowApi;
import org.idp.server.core.oauth.OAuthProtocol;
import org.idp.server.core.oauth.OAuthSessionDelegate;
import org.idp.server.core.oauth.identity.PasswordEncodeDelegation;
import org.idp.server.core.oauth.identity.PasswordVerificationDelegation;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.oauth.identity.permission.PermissionCommandRepository;
import org.idp.server.core.oauth.identity.role.RoleCommandRepository;
import org.idp.server.core.organization.OrganizationRepository;
import org.idp.server.core.security.SecurityEventApi;
import org.idp.server.core.security.SecurityEventHooks;
import org.idp.server.core.security.SecurityEventPublisher;
import org.idp.server.core.security.event.OAuthFlowEventPublisher;
import org.idp.server.core.security.event.SecurityEventRepository;
import org.idp.server.core.security.hook.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.core.security.hook.SecurityEventHooksLoader;
import org.idp.server.core.tenant.TenantRepository;
import org.idp.server.core.token.*;
import org.idp.server.core.userinfo.UserinfoApi;
import org.idp.server.core.userinfo.UserinfoProtocol;

/** IdpServerApplication */
public class IdpServerApplication {

  IdpServerStarterApi idpServerStarterApi;
  OAuthFlowApi oAuthFlowApi;
  TokenApi tokenApi;
  OidcMetaDataApi oidcMetaDataApi;
  UserinfoApi userinfoApi;
  CibaFlowApi cibaFlowApi;
  SecurityEventApi securityEventApi;
  OnboardingApi onboardingApi;
  ServerManagementApi serverManagementApi;
  ClientManagementApi clientManagementApi;
  UserManagementApi userManagementApi;
  OperatorAuthenticationApi operatorAuthenticationApi;

  public IdpServerApplication(
      DatabaseConfig databaseConfig,
      String encryptionKey,
      OAuthSessionDelegate oAuthSessionDelegate,
      PasswordEncodeDelegation passwordEncodeDelegation,
      PasswordVerificationDelegation passwordVerificationDelegation,
      SecurityEventPublisher securityEventPublisher) {

    TransactionManager.setConnectionConfig(
        databaseConfig.url(), databaseConfig.username(), databaseConfig.password());

    DataSourceDependencyContainer dependencyContainer = new DataSourceDependencyContainer();
    AesCipher aesCipher = new AesCipher(encryptionKey);
    HmacHasher hmacHasher = new HmacHasher(encryptionKey);
    dependencyContainer.register(AesCipher.class, aesCipher);
    dependencyContainer.register(HmacHasher.class, hmacHasher);
    DataSourceContainer dataSourceContainer = DataSourceContainerLoader.load(dependencyContainer);
    dataSourceContainer.register(OAuthSessionDelegate.class, oAuthSessionDelegate);

    ServerConfigurationRepository serverConfigurationRepository =
        dataSourceContainer.resolve(ServerConfigurationRepository.class);
    ClientConfigurationRepository clientConfigurationRepository =
        dataSourceContainer.resolve(ClientConfigurationRepository.class);
    SecurityEventRepository securityEventRepository =
        dataSourceContainer.resolve(SecurityEventRepository.class);
    UserRepository userRepository = dataSourceContainer.resolve(UserRepository.class);
    OrganizationRepository organizationRepository =
        dataSourceContainer.resolve(OrganizationRepository.class);
    TenantRepository tenantRepository = dataSourceContainer.resolve(TenantRepository.class);
    RoleCommandRepository roleCommandRepository =
        dataSourceContainer.resolve(RoleCommandRepository.class);
    PermissionCommandRepository permissionCommandRepository =
        dataSourceContainer.resolve(PermissionCommandRepository.class);
    SecurityEventHookConfigurationQueryRepository hookQueryRepository =
        dataSourceContainer.resolve(SecurityEventHookConfigurationQueryRepository.class);

    ProtocolContainer protocolContainer = ProtocolContainerLoader.load(dataSourceContainer);

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

    AuthenticationInteractors authenticationInteractors =
        AuthenticationInteractorLoader.load(authenticationDependencyContainer);

    this.idpServerStarterApi =
        TransactionInterceptor.createProxy(
            new IdpServerStarterEntryService(
                organizationRepository,
                tenantRepository,
                userRepository,
                permissionCommandRepository,
                roleCommandRepository,
                serverConfigurationRepository,
                passwordEncodeDelegation),
            IdpServerStarterApi.class);

    OAuthFlowEventPublisher oAuthFLowEventPublisher =
        new OAuthFlowEventPublisher(securityEventPublisher);

    OidcSsoExecutors oidcSsoExecutors = OidcSsoExecutorLoader.load();
    FederationDependencyContainer federationDependencyContainer =
        FederationDependencyContainerLoader.load();
    federationDependencyContainer.register(OidcSsoExecutors.class, oidcSsoExecutors);
    FederationInteractors federationInteractors =
        FederationInteractorLoader.load(federationDependencyContainer);

    this.oAuthFlowApi =
        TransactionInterceptor.createProxy(
            new OAuthFlowEntryService(
                protocolContainer.resolve(OAuthProtocol.class),
                oAuthSessionDelegate,
                authenticationInteractors,
                federationInteractors,
                userRepository,
                tenantRepository,
                oAuthFLowEventPublisher),
            OAuthFlowApi.class);

    this.tokenApi =
        TransactionInterceptor.createProxy(
            new TokenEntryService(
                protocolContainer.resolve(TokenProtocol.class),
                protocolContainer.resolve(TokenIntrospectionProtocol.class),
                protocolContainer.resolve(TokenRevocationProtocol.class),
                userRepository,
                tenantRepository,
                passwordVerificationDelegation),
            TokenApi.class);

    this.oidcMetaDataApi =
        TransactionInterceptor.createProxy(
            new OidcMetaDataEntryService(
                tenantRepository,
                protocolContainer.resolve(DiscoveryProtocol.class),
                protocolContainer.resolve(JwksProtocol.class)),
            OidcMetaDataApi.class);

    this.userinfoApi =
        TransactionInterceptor.createProxy(
            new UserinfoEntryService(
                protocolContainer.resolve(UserinfoProtocol.class),
                userRepository,
                tenantRepository),
            UserinfoApi.class);

    this.cibaFlowApi =
        TransactionInterceptor.createProxy(
            new CibaFlowEntryService(
                protocolContainer.resolve(CibaProtocol.class), userRepository, tenantRepository),
            CibaFlowApi.class);

    this.securityEventApi =
        TransactionInterceptor.createProxy(
            new SecurityEventEntryService(
                tenantRepository, securityEventRepository, securityEventHooks, hookQueryRepository),
            SecurityEventApi.class);

    this.onboardingApi =
        TransactionInterceptor.createProxy(
            new OnboardingEntryService(
                tenantRepository,
                organizationRepository,
                userRepository,
                serverConfigurationRepository),
            OnboardingApi.class);

    this.serverManagementApi =
        TransactionInterceptor.createProxy(
            new ServerManagementEntryService(tenantRepository, serverConfigurationRepository),
            ServerManagementApi.class);

    this.clientManagementApi =
        TransactionInterceptor.createProxy(
            new ClientManagementEntryService(
                tenantRepository, new ClientConfigurationHandler(clientConfigurationRepository)),
            ClientManagementApi.class);

    this.userManagementApi =
        TransactionInterceptor.createProxy(
            new UserManagementEntryService(tenantRepository, userRepository),
            UserManagementApi.class);

    this.operatorAuthenticationApi =
        TransactionInterceptor.createProxy(
            new OperatorAuthenticationEntryService(
                protocolContainer.resolve(TokenIntrospectionProtocol.class),
                tenantRepository,
                userRepository),
            OperatorAuthenticationApi.class);
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

  public SecurityEventApi securityEventApi() {
    return securityEventApi;
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
