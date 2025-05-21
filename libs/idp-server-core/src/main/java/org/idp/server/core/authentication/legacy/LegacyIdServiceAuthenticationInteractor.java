package org.idp.server.core.authentication.legacy;

import java.util.*;
import org.idp.server.basic.http.*;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.authentication.*;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class LegacyIdServiceAuthenticationInteractor implements AuthenticationInteractor {

  AuthenticationConfigurationQueryRepository configurationRepository;
  HttpRequestExecutor httpRequestExecutor;
  JsonConverter jsonConverter;

  public LegacyIdServiceAuthenticationInteractor(
      AuthenticationConfigurationQueryRepository configurationRepository) {
    this.configurationRepository = configurationRepository;
    this.httpRequestExecutor = new HttpRequestExecutor(HttpClientFactory.defaultClient());
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthorizationIdentifier authorizationIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      AuthenticationTransaction transaction,
      UserQueryRepository userQueryRepository) {

    LegacyIdServiceAuthenticationConfiguration configuration =
        configurationRepository.get(
            tenant, "legacy-id-service", LegacyIdServiceAuthenticationConfiguration.class);

    LegacyIdServiceAuthenticationDetailConfiguration authenticationConfig =
        configuration.authenticationDetailConfig();

    HttpRequestResult authenticationResult =
        httpRequestExecutor.execute(
            authenticationConfig.httpRequestUrl(),
            authenticationConfig.httpMethod(),
            authenticationConfig.httpRequestHeaders(),
            new HttpRequestBaseParams(request.toMap()),
            authenticationConfig.httpRequestDynamicBodyKeys(),
            authenticationConfig.httpRequestStaticBody());

    if (authenticationResult.isClientError()) {

      return new AuthenticationInteractionRequestResult(
          AuthenticationInteractionStatus.CLIENT_ERROR,
          type,
          authenticationResult.toMap(),
          DefaultSecurityEventType.legacy_authentication_failure);
    }

    Authentication authentication =
        new Authentication()
            .setTime(SystemDateTime.now())
            .addMethods(new ArrayList<>(List.of("pwd")))
            .addAcrValues(List.of("urn:mace:incommon:iap:silver"));

    LegacyIdServiceAuthenticationDetailConfiguration userinfoConfig =
        configuration.userinfoDetailConfig();

    HttpRequestResult userinfoResult =
        httpRequestExecutor.execute(
            userinfoConfig.httpRequestUrl(),
            userinfoConfig.httpMethod(),
            userinfoConfig.httpRequestHeaders(),
            new HttpRequestBaseParams(request.toMap()),
            userinfoConfig.httpRequestDynamicBodyKeys(),
            userinfoConfig.httpRequestStaticBody());

    UserInfoMapper userInfoMapper =
        new UserInfoMapper(
            configuration.providerName(),
            userinfoResult.body(),
            userinfoConfig.userinfoMappingRules());
    User user = userInfoMapper.toUser();

    User exsitingUser =
        userQueryRepository.findByProvider(
            tenant, configuration.providerName(), user.providerUserId());
    if (exsitingUser.exists()) {
      user.setSub(exsitingUser.sub());
    } else {
      user.setSub(UUID.randomUUID().toString());
    }

    Map<String, Object> result = new HashMap<>();
    result.put("user", user.toMap());
    result.put("authentication", authentication.toMap());

    return new AuthenticationInteractionRequestResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        user,
        authentication,
        result,
        DefaultSecurityEventType.legacy_authentication_success);
  }
}
