package org.idp.server.oauth.token;

import java.util.Map;
import org.idp.server.basic.base64.Base64Codeable;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ConfigurationInvalidException;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.clientcredentials.ClientCredentials;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.verifiablepresentation.response.VerifiablePresentationBuilder;
import org.idp.server.type.verifiablecredential.VerifiableCredentialContext;
import org.idp.server.type.verifiablecredential.VerifiableCredentialType;
import org.idp.server.type.verifiablepresentation.VpToken;

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
