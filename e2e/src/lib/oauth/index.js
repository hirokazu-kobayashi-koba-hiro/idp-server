import { createJwt, createJwtWithPrivateKey, generateJti } from "../jose";
import { toEpocTime } from "../util";
import { digestS256 } from "../hash";
import Base64 from "crypto-js/enc-base64url";

export const createClientAssertion = ({ client, issuer }) => {
  const payload = {
    iss: client.clientId,
    sub: client.clientId,
    aud: issuer,
    jti: generateJti(),
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

export const calculateCodeChallengeWithS256 = (codeVerifier) => {
  const s256Hash = digestS256(codeVerifier);
  console.log(s256Hash);
  const codeChallenge = Base64.stringify(s256Hash);
  console.log(codeChallenge);
  return codeChallenge;
};