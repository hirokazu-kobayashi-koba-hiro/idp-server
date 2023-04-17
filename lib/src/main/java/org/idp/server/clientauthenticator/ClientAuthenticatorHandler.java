package org.idp.server.clientauthenticator;

import static org.idp.server.type.oauth.ClientAuthenticationType.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.type.oauth.ClientAuthenticationType;

public class ClientAuthenticatorHandler {

  static Map<ClientAuthenticationType, ClientAuthenticator> map = new HashMap<>();

  static {
    map.put(client_secret_basic, new ClientSecretBasicAuthenticator());
    map.put(client_secret_post, new ClientSecretPostAuthenticator());
    map.put(client_secret_jwt, new ClientSecretJwtAuthenticator());
    map.put(private_key_jwt, new PrivateKeyJwtAuthenticator());
    map.put(tls_client_auth, new TlsClientAuthAuthenticator());
    map.put(self_signed_tls_client_auth, new SelfSignedTlsClientAuthAuthenticator());
  }

  public void authenticate(BackchannelRequestContext context) {
    ClientAuthenticator clientAuthenticator = map.get(context.clientAuthenticationType());
    if (Objects.isNull(clientAuthenticator)) {
      throw new RuntimeException("not supported client authentication type");
    }
    clientAuthenticator.authenticate(context);
  }
}
