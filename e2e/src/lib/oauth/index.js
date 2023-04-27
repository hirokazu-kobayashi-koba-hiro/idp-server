import { createJwt } from "../jose";
import { v4 as uuidv4 } from "uuid";
import { toEpocTime } from "../util";

export const createClientAssertion = ({ client, issuer }) => {
  //iss
  // REQUIRED. Issuer. This MUST contain the client_id of the OAuth Client.
  // sub
  // REQUIRED. Subject. This MUST contain the client_id of the OAuth Client.
  // aud
  // REQUIRED. Audience. The aud (audience) Claim. Value that identifies the Authorization Server as an intended audience. The Authorization Server MUST verify that it is an intended audience for the token. The Audience SHOULD be the URL of the Authorization Server's Token Endpoint.
  // jti
  // REQUIRED. JWT ID. A unique identifier for the token, which can be used to prevent reuse of the token. These tokens MUST only be used once, unless conditions for reuse were negotiated between the parties; any such negotiation is beyond the scope of this specification.
  // exp
  // REQUIRED. Expiration time on or after which the ID Token MUST NOT be accepted for processing.
  // iat
  // OPTIONAL. Time at which the JWT was issued.
  const payload = {
    iss: client.clientId,
    sub: client.clientId,
    aud: issuer,
    jti: uuidv4(),
    exp: toEpocTime({ plus: 3600 }),
    iat: toEpocTime({ plus: 0 }),
  };

  return createJwt({
    payload,
    secret: client.clientSecret,
  });
};
