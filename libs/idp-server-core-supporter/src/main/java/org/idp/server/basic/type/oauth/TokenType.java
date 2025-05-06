package org.idp.server.basic.type.oauth;

/**
 * 7.1. Access Token Types
 *
 * <p>
 * The access token type provides the client with the information required to successfully utilize
 * the access token to make a protected resource request (along with type-specific attributes). The
 * client MUST NOT use an access token if it does not understand the token type.
 *
 * <p>
 * For example, the "bearer" token type defined in [RFC6750] is utilized by simply including the
 * access token string in the request:
 *
 * <p>
 * GET /resource/1 HTTP/1.1 Host: example.com Authorization: Bearer mF_9.B5f-4.1JqM
 *
 * <p>
 * while the "mac" token type defined in [OAuth-HTTP-MAC] is utilized by issuing a Message
 * Authentication Code (MAC) key together with the access token that is used to sign certain
 * components of the HTTP requests:
 *
 * <p>
 * GET /resource/1 HTTP/1.1 Host: example.com Authorization: MAC id="h480djs93hd8",
 * nonce="274312:dj83hs9s", mac="kDZvddkndxvhGRXZhvuDjEWhGeE="
 *
 * <p>
 * The above examples are provided for illustration purposes only. Developers are advised to consult
 * the [RFC6750] and [OAuth-HTTP-MAC] specifications before use.
 *
 * <p>
 * Each access token type definition specifies the additional attributes (if any) sent to the client
 * together with the "access_token" response parameter. It also defines the HTTP authentication
 * method used to include the access token when making a protected resource request.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-7.1">7.1. Access Token Types</a>
 */
public enum TokenType {
  Bearer, DPoP, undefined;

  public boolean isDefined() {
    return this != undefined;
  }
}
