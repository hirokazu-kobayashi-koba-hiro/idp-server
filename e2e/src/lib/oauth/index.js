import { createJwt, createJwtWithPrivateKey, generateJti } from "../jose";
import { base64UrlEncode, isObject, toEpocTime } from "../util";
import { digestS256 } from "../hash";
import Base64 from "crypto-js/enc-base64url";
const { createHash } = require("crypto");

export const createClientAssertion = ({ client, issuer }) => {
  const payload = {
    iss: client.clientId,
    sub: client.clientId,
    aud: issuer,
    jti: generateJti(),
    exp: toEpocTime({ adjusted: 3600 }),
    iat: toEpocTime({ adjusted: 0 })
  };
  if (client.clientSecretKey) {
    return createJwtWithPrivateKey({
      payload,
      privateKey: client.clientSecretKey
    });
  }
  return createJwt({
    payload,
    secret: client.clientSecret
  });
};

export const createInvalidClientAssertionWithPrivateKey = ({ client, issuer, invalidPrivateKey }) => {
  const payload = {
    iss: client.clientId,
    sub: client.clientId,
    aud: issuer,
    jti: generateJti(),
    exp: toEpocTime({ adjusted: 3600 }),
    iat: toEpocTime({ adjusted: 0 })
  };
  return createJwtWithPrivateKey({
    payload,
    privateKey: invalidPrivateKey
  });
};

export const calculateCodeChallengeWithS256 = (codeVerifier) => {
  const s256Hash = digestS256(codeVerifier);
  const codeChallenge = Base64.stringify(s256Hash);
  console.log(codeChallenge);
  return codeChallenge;
};

export const generateCodeVerifier =(length = 43) => {

  const charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
  const randomValues = new Uint8Array(length);
  crypto.getRandomValues(randomValues);

  return Array.from(randomValues)
    .map(b => charset[b % charset.length])
    .join("");
};

export const calculateIdTokenClaimHashWithS256 = (input) => {
  const digest = createHash("sha256").update(input).digest();
  return base64UrlEncode(digest.slice(0, digest.length / 2));
};


export const IdTokenDefinition = {
  header: {
    type: "object",
    required: true,
    schema: {
      alg: {
        type: "string",
        values: ["ES257", "RS256"]
      },
      kid: {
        type: "string",
        required: false
      },
      typ: {
        type: "string",
        required: false
      }
    }
  },
  payload: {
    type: "object",
    required: true,
    schema: {
      sub: {
        type: "string",
        required: true
      },
      iss: {
        type: "string",
        required: true
      },
      aud: {
        type: "string",
        required: true
      },
      exp: {
        type: "integer",
        required: true
      }
    }
  }
};
