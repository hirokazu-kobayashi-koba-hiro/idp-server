package org.idp.server.core.ciba.request;

import java.util.Set;
import org.idp.server.core.basic.jose.JoseContext;
import org.idp.server.core.basic.jose.JsonWebTokenClaims;
import org.idp.server.core.ciba.CibaProfile;
import org.idp.server.core.ciba.CibaRequestObjectParameters;
import org.idp.server.core.ciba.CibaRequestParameters;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.type.ciba.*;
import org.idp.server.core.type.oauth.*;
import org.idp.server.core.type.oidc.*;

/**
 * RequestObjectPatternFactory
 *
 * <p>6.3.3. Request Parameter Assembly and Validation
 *
 * <p>The Authorization Server MUST assemble the set of Authorization Request parameters to be used
 * from the Request Object value and the OAuth 2.0 Authorization Request parameters (minus the
 * request or request_uri parameters). If the same parameter exists both in the Request Object and
 * the OAuth Authorization Request parameters, the parameter in the Request Object is used. Using
 * the assembled set of Authorization Request parameters, the Authorization Server then validates
 * the request the normal manner for the flow being used, as specified in Sections 3.1.2.2, 3.2.2.2,
 * or 3.3.2.2.
 */
public class RequestObjectPatternFactory implements BackchannelAuthenticationRequestFactory {

  @Override
  public BackchannelAuthenticationRequest create(
      CibaProfile profile,
      ClientSecretBasic clientSecretBasic,
      CibaRequestParameters parameters,
      JoseContext joseContext,
      Set<String> filteredScopes,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    JsonWebTokenClaims jsonWebTokenClaims = joseContext.claims();
    CibaRequestObjectParameters requestObjectParameters =
        new CibaRequestObjectParameters(jsonWebTokenClaims.payload());
    Scopes scopes = new Scopes(filteredScopes);
    ClientId clientId = getClientId(clientSecretBasic, parameters, requestObjectParameters);
    IdTokenHint idTokenHint =
        requestObjectParameters.hasIdTokenHint()
            ? requestObjectParameters.idTokenHint()
            : parameters.idTokenHint();
    LoginHint loginHint =
        requestObjectParameters.hasLoginHint()
            ? requestObjectParameters.loginHint()
            : parameters.loginHint();
    AcrValues acrValues =
        requestObjectParameters.hasAcrValues()
            ? requestObjectParameters.acrValues()
            : parameters.acrValues();
    LoginHintToken loginHintToken =
        requestObjectParameters.hasLoginHintToken()
            ? requestObjectParameters.loginHintToken()
            : parameters.loginHintToken();
    UserCode userCode =
        requestObjectParameters.hasUserCode()
            ? requestObjectParameters.userCode()
            : parameters.userCode();
    BindingMessage bindingMessage =
        requestObjectParameters.hasBindingMessage()
            ? requestObjectParameters.bindingMessage()
            : parameters.bindingMessage();
    ClientNotificationToken clientNotificationToken =
        requestObjectParameters.hasClientNotificationToken()
            ? requestObjectParameters.clientNotificationToken()
            : parameters.clientNotificationToken();
    RequestedExpiry requestedExpiry =
        requestObjectParameters.hasRequestedExpiry()
            ? requestObjectParameters.requestedExpiry()
            : parameters.requestedExpiry();

    RequestObject requestObject = new RequestObject();

    BackchannelAuthenticationRequestBuilder builder = new BackchannelAuthenticationRequestBuilder();
    builder.add(createIdentifier());
    builder.add(serverConfiguration.tenantIdentifier());
    builder.add(profile);
    builder.add(clientConfiguration.backchannelTokenDeliveryMode());
    builder.add(scopes);
    builder.add(clientId);
    builder.add(idTokenHint);
    builder.add(loginHint);
    builder.add(acrValues);
    builder.add(requestObject);
    builder.add(loginHintToken);
    builder.add(userCode);
    builder.add(bindingMessage);
    builder.add(clientNotificationToken);
    builder.add(requestedExpiry);
    return builder.build();
  }

  private static ClientId getClientId(
      ClientSecretBasic clientSecretBasic,
      CibaRequestParameters parameters,
      CibaRequestObjectParameters requestObjectParameters) {
    ClientId clientId =
        requestObjectParameters.hasIdTokenHint()
            ? requestObjectParameters.clientId()
            : parameters.clientId();
    if (clientId.exists()) {
      return clientId;
    }
    return clientSecretBasic.clientId();
  }
}
