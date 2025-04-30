package org.idp.server.core.oauth.token;

import java.util.Map;
import org.idp.server.core.basic.base64.Base64Codeable;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ConfigurationInvalidException;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.identity.User;
import org.idp.server.core.oauth.clientcredentials.ClientCredentials;
import org.idp.server.core.oauth.grant.AuthorizationGrant;
import org.idp.server.core.oauth.vp.response.VerifiablePresentationBuilder;
import org.idp.server.core.type.verifiablecredential.VerifiableCredentialContext;
import org.idp.server.core.type.verifiablecredential.VerifiableCredentialType;
import org.idp.server.core.type.verifiablepresentation.VpToken;

public interface VpTokenCreatable extends Base64Codeable {

  default VpToken createVpToken(
      User user,
      AuthorizationGrant authorizationGrant,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials) {
    try {

      VerifiablePresentationBuilder builder =
          new VerifiablePresentationBuilder()
              .addContext(VerifiableCredentialContext.values)
              .addType(VerifiableCredentialType.vp.types())
              .addVerifiableCredential(user.verifiableCredentials());

      Map<String, Object> verifiablePresentation = builder.build();
      JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
      String encodeWithUrlSafe = encodeWithUrlSafe(jsonConverter.write(verifiablePresentation));
      return new VpToken(encodeWithUrlSafe);
    } catch (Exception exception) {
      throw new ConfigurationInvalidException(exception);
    }
  }
}
