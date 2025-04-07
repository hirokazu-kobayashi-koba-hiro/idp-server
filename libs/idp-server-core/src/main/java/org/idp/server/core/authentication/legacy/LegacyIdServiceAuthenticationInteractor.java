package org.idp.server.core.authentication.legacy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.authentication.*;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.basic.http.*;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.tenant.Tenant;

public class LegacyIdServiceAuthenticationInteractor implements AuthenticationInteractor {

  AuthenticationConfigurationQueryRepository configurationRepository;
  HttpRequestExecutor httpRequestExecutor;
  JsonConverter jsonConverter;

  public LegacyIdServiceAuthenticationInteractor(
      AuthenticationConfigurationQueryRepository configurationRepository) {
    this.configurationRepository = configurationRepository;
    this.httpRequestExecutor = new HttpRequestExecutor(HttpClientFactory.defaultClient());
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public AuthenticationInteractionResult interact(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      OAuthSession oAuthSession,
      UserRepository userRepository) {

    LegacyIdServiceAuthenticationConfiguration configuration =
        configurationRepository.get(
            tenant, "legacy-id-service", LegacyIdServiceAuthenticationConfiguration.class);

    LegacyIdServiceAuthenticationDetailConfiguration authenticationConfig =
        configuration.getAuthenticationDetailConfig();

    HttpRequestResult authenticationResult =
        httpRequestExecutor.execute(
            authenticationConfig.httpRequestUrl(),
            authenticationConfig.httpMethod(),
            authenticationConfig.httpRequestHeaders(),
            new HttpRequestBaseParams(request.toMap()),
            authenticationConfig.httpRequestDynamicBodyKeys(),
            authenticationConfig.httpRequestStaticBody());

    if (authenticationResult.isError()) {

      return new AuthenticationInteractionResult(
          AuthenticationInteractionStatus.CLIENT_ERROR,
          type,
          authenticationResult.toMap(),
          DefaultSecurityEventType.email_verification_success);
    }

    Authentication authentication =
        new Authentication()
            .setTime(SystemDateTime.now())
            .addMethods(new ArrayList<>(List.of("pwd")))
            .addAcrValues(List.of("urn:mace:incommon:iap:silver"));

      LegacyIdServiceAuthenticationDetailConfiguration userinfoConfig =
              configuration.getUserinfoDetailConfig();

      HttpRequestResult userinfoResult =
              httpRequestExecutor.execute(
                      authenticationConfig.httpRequestUrl(),
                      authenticationConfig.httpMethod(),
                      authenticationConfig.httpRequestHeaders(),
                      new HttpRequestBaseParams(request.toMap()),
                      authenticationConfig.httpRequestDynamicBodyKeys(),
                      authenticationConfig.httpRequestStaticBody());


    UserInfoMapper userInfoMapper = new UserInfoMapper(userinfoResult.body(), userinfoConfig.userInfoMappingRules());
    User user = userInfoMapper.toUser();

    User exsitingUser = userRepository.findByProvider(tenant.identifierValue(), configuration.providerName(), user.providerUserId());
    if (exsitingUser.exists()) {
      user.setSub(exsitingUser.sub());
    }

    Map<String, Object> result = new HashMap<>();
    result.put("user", user);
    result.put("authentication", authentication);

    return new AuthenticationInteractionResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        user,
        authentication,
        result,
        DefaultSecurityEventType.email_verification_success);
  }
}
