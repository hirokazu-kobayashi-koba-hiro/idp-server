package org.idp.server.core.adapters.httpclient.credential;

import org.idp.server.core.basic.http.HttpClientFactory;
import org.idp.server.core.basic.json.JsonConvertable;
import org.idp.server.core.basic.vc.Credential;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.verifiablecredential.VerifiableCredential;
import org.idp.server.core.verifiablecredential.VerifiableCredentialCreator;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

public class VerifiableCredentialJwtClient implements VerifiableCredentialCreator {

  HttpClient httpClient;

  public VerifiableCredentialJwtClient() {
    this.httpClient = HttpClientFactory.defaultClient();
  }

  // FIXME setting value
  public VerifiableCredential create(
      Credential credential,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    try {
      HashMap<String, Object> requestBodyMap = new HashMap<>();
      requestBodyMap.put("credential", credential.values());
      String requestBody = JsonConvertable.write(requestBodyMap);
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(new URI("http://localhost:8000/v1/verifiable-credentials/did-jwt"))
              .POST((HttpRequest.BodyPublishers.ofString(requestBody)))
              .header("Content-Type", "application/json")
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      String responseBody = response.body();
      Map map = JsonConvertable.read(responseBody, Map.class);
      return new VerifiableCredential(map.get("vc"));
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
