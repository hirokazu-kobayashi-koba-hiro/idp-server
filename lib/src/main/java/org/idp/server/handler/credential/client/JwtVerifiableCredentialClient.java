package org.idp.server.handler.credential.client;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.json.stream.JsonParser;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.basic.http.HttpClientFactory;
import org.idp.server.basic.jose.JoseInvalidException;
import org.idp.server.basic.jose.JsonWebKeyInvalidException;
import org.idp.server.basic.jose.JsonWebSignature;
import org.idp.server.basic.jose.JsonWebSignatureFactory;
import org.idp.server.basic.json.JsonConvertable;
import org.idp.server.basic.vc.VerifiableCredential;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.type.extension.CreatedAt;
import org.idp.server.type.extension.ExpiredAt;
import org.idp.server.type.oauth.ExpiresIn;
import org.idp.server.verifiablecredential.VerifiableCredentialCreator;
import org.idp.server.verifiablecredential.VerifiableCredentialJwt;

public class JwtVerifiableCredentialClient implements VerifiableCredentialCreator {

  HttpClient httpClient;

  public JwtVerifiableCredentialClient() {
    this.httpClient = HttpClientFactory.defaultClient();
  }

  // FIXME setting value
  public VerifiableCredentialJwt create(
      VerifiableCredential verifiableCredential,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    try {
      HashMap<String, Object> requestBodyMap = new HashMap<>();
      requestBodyMap.put("vc", verifiableCredential.values());
      String requestBody = JsonConvertable.write(requestBodyMap);
      HttpRequest request =
              HttpRequest.newBuilder()
                      .uri(new URI("http://localhost:3000/v1/verifiable-credentials/did-jwt"))
                      .POST((HttpRequest.BodyPublishers.ofString(requestBody)))
                      .header("Content-Type", "application/json")
                      .build();
      HttpResponse<String> response =
              httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      String responseBody = response.body();
      Map map = JsonConvertable.read(responseBody, Map.class);
      return new VerifiableCredentialJwt((String) map.get("vc"));
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
