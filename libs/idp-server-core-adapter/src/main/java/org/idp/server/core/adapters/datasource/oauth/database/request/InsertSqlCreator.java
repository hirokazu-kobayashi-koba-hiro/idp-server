package org.idp.server.core.adapters.datasource.oauth.database.request;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.oauth.rar.AuthorizationDetails;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.vp.request.PresentationDefinition;
import org.idp.server.core.type.oauth.CustomParams;

public class InsertSqlCreator {

  static List<Object> createInsert(AuthorizationRequest authorizationRequest) {
    List<Object> params = new ArrayList<>();
    params.add(authorizationRequest.identifier().value());
    params.add(authorizationRequest.tenantIdentifier().value());
    params.add(authorizationRequest.profile().name());
    params.add(authorizationRequest.scope().toStringValues());
    params.add(authorizationRequest.responseType().name());
    params.add(authorizationRequest.clientId().value());

    if (authorizationRequest.hasRedirectUri()) {
      params.add(authorizationRequest.redirectUri().value());
    } else {
      params.add("");
    }

    if (authorizationRequest.hasState()) {
      params.add(authorizationRequest.state().value());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasResponseMode()) {
      params.add(authorizationRequest.responseMode().name());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasNonce()) {
      params.add(authorizationRequest.nonce().value());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasDisplay()) {
      params.add(authorizationRequest.display().name());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasPrompts()) {
      params.add(authorizationRequest.prompts().toStringValues());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasMaxAge()) {
      params.add(authorizationRequest.maxAge().value());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasUilocales()) {
      params.add(authorizationRequest.uiLocales().toStringValues());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasIdTokenHint()) {
      params.add(authorizationRequest.idTokenHint().value());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasLoginHint()) {
      params.add(authorizationRequest.loginHint().value());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasAcrValues()) {
      params.add(authorizationRequest.acrValues().toStringValues());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasClaims()) {
      params.add(authorizationRequest.claims().value());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasRequest()) {
      params.add(authorizationRequest.request().value());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasRequestUri()) {
      params.add(authorizationRequest.requestUri().value());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasCodeChallenge()) {
      params.add(authorizationRequest.codeChallenge().value());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasCodeChallengeMethod()) {
      params.add(authorizationRequest.codeChallengeMethod().name());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasAuthorizationDetails()) {
      params.add(convertJsonAuthorizationDetails(authorizationRequest.authorizationDetails()));
    } else {
      params.add("[]");
    }

    if (authorizationRequest.hasPresentationDefinition()) {
      params.add(convertJsonPresentationDefinition(authorizationRequest.presentationDefinition()));
    } else {
      params.add("{}");
    }

    if (authorizationRequest.hasPresentationDefinitionUri()) {
      params.add(authorizationRequest.presentationDefinitionUri().value());
    } else {
      params.add(null);
    }

    if (authorizationRequest.hasCustomParams()) {
      params.add(convertJson(authorizationRequest.customParams()));
    } else {
      params.add("{}");
    }

    return params;
  }

  private static String convertJsonAuthorizationDetails(AuthorizationDetails authorizationDetails) {
    JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
    return jsonConverter.write(authorizationDetails.toMapValues());
  }

  private static String convertJsonPresentationDefinition(
      PresentationDefinition presentationDefinition) {
    JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
    return jsonConverter.write(presentationDefinition);
  }

  private static String convertJson(CustomParams customParams) {
    JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
    return jsonConverter.write(customParams.values());
  }
}
