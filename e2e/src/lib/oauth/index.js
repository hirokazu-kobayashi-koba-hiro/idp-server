import { createJwt, createJwtWithPrivateKey } from "../jose";
import { v4 as uuidv4 } from "uuid";
import { toEpocTime } from "../util";

export const createClientAssertion = ({ client, issuer }) => {
  const payload = {
    iss: client.clientId,
    sub: client.clientId,
    aud: issuer,
    jti: uuidv4(),
    exp: toEpocTime({ plus: 3600 }),
    iat: toEpocTime({ plus: 0 }),
  };
  if (client.clientSecretKey) {
    return createJwtWithPrivateKey({
      payload,
      privateKey: client.clientSecretKey,
    });
  }
  return createJwt({
    payload,
    secret: client.clientSecret,
  });
};
