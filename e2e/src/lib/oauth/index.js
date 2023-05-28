import { createJwt, createJwtWithPrivateKey, generateJti } from "../jose";
import { base64UrlEncode, toEpocTime } from "../util";
import { digestS256 } from "../hash";
import Base64 from "crypto-js/enc-base64url";
// eslint-disable-next-line no-undef
const { createHash } = require("crypto");

export const createClientAssertion = ({ client, issuer }) => {
  const payload = {
    iss: client.clientId,
    sub: client.clientId,
    aud: issuer,
    jti: generateJti(),
    exp: toEpocTime({ adjusted: 3600 }),
    iat: toEpocTime({ adjusted: 0 }),
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

export const calculateIdTokenClaimHashWithS256 = (input) => {
  const digest = createHash("sha256").update(input).digest();
  return base64UrlEncode(digest.slice(0, digest.length / 2));
};
