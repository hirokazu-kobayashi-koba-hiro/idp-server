package org.idp.server.core.clientauthenticator;

import static org.idp.server.basic.type.oauth.ClientAuthenticationType.*;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.oauth.clientcredentials.ClientCredentials;
import org.idp.server.basic.type.oauth.ClientAuthenticationType;

public class ClientAuthenticatorHandler {

  static Map<ClientAuthenticationType, ClientAuthenticator> map = new HashMap<>();

  static {
    map.put(client_secret_basic, new ClientSecretBasicAuthenticator());
    map.put(client_secret_post, new ClientSecretPostAuthenticator());
    map.put(client_secret_jwt, new ClientSecretJwtAuthenticator());
    map.put(private_key_jwt, new PrivateKeyJwtAuthenticator());
    map.put(tls_client_auth, new TlsClientAuthAuthenticator());
    map.put(self_signed_tls_client_auth, new SelfSignedTlsClientAuthAuthenticator());
    map.put(none, context -> new ClientCredentials());
  }

  public ClientCredentials authenticate(BackchannelRequestContext context) {
    ClientAuthenticator clientAuthenticator = map.get(context.clientAuthenticationType());
    ClientAuthenticationVerifier verifier =
        new ClientAuthenticationVerifier(
            context.clientAuthenticationType(), clientAuthenticator, context.serverConfiguration());
    verifier.verify();
    return clientAuthenticator.authenticate(context);
  }
}
