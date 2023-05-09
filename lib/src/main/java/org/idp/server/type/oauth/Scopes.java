package org.idp.server.type.oauth;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 3.3. Access Token Scope
 *
 * <p>The authorization and token endpoints allow the client to specify the scope of the access
 * request using the "scope" request parameter. In turn, the authorization server uses the "scope"
 * response parameter to inform the client of the scope of the access token issued.
 *
 * <p>The value of the scope parameter is expressed as a list of space- delimited, case-sensitive
 * strings. The strings are defined by the authorization server. If the value contains multiple
 * space-delimited strings, their order does not matter, and each string adds an additional access
 * range to the requested scope.
 *
 * <p>scope = scope-token *( SP scope-token ) scope-token = 1*( %x21 / %x23-5B / %x5D-7E )
 *
 * <p>The authorization server MAY fully or partially ignore the scope requested by the client,
 * based on the authorization server policy or the resource owner's instructions. If the issued
 * access token scope is different from the one requested by the client, the authorization server
 * MUST include the "scope" response parameter to inform the client of the actual scope granted.
 *
 * <p>If the client omits the scope parameter when requesting authorization, the authorization
 * server MUST either process the request using a pre-defined default value or fail the request
 * indicating an invalid scope. The authorization server SHOULD document its scope requirements and
 * default value (if defined).
 */
public class Scopes {
  Set<String> values;

  public Scopes() {
    this.values = new HashSet<>();
  }

  public Scopes(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      this.values = new HashSet<>();
      return;
    }
    this.values = Arrays.stream(value.split(" ")).collect(Collectors.toSet());
  }

  public Scopes(Set<String> values) {
    this.values = values;
  }

  public boolean exists() {
    return !values.isEmpty();
  }

  public String toStringValues() {
    return String.join(" ", values);
  }

  public boolean contains(String scope) {
    return values.contains(scope);
  }
}
