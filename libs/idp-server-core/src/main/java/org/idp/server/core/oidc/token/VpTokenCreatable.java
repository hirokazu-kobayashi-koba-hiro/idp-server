package org.idp.server.core.oidc.token;

import java.util.Map;
import org.idp.server.basic.base64.Base64Codeable;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.type.verifiablecredential.VerifiableCredentialContext;
import org.idp.server.basic.type.verifiablecredential.VerifiableCredentialType;
import org.idp.server.basic.type.verifiablepresentation.VpToken;
import org.idp.server.core.identity.User;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.configuration.ClientConfiguration;
import org.idp.server.core.oidc.configuration.ConfigurationInvalidException;
import org.idp.server.core.oidc.configuration.ServerConfiguration;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.vp.response.VerifiablePresentationBuilder;

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
