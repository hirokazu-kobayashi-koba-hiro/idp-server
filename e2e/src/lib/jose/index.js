// eslint-disable-next-line no-undef
import { re } from "@babel/core/lib/vendor/import-meta-resolve";

const jwt = require("jsonwebtoken");
import jwkToPem from "jwk-to-pem";
import jwt_decode from "jwt-decode";

export const createJwt = ({ payload, secret, options }) => {
  return jwt.sign(payload, secret, options);
};

export const createJwtWithPrivateKey = ({
  payload,
  privateKey,
  additionalOptions,
}) => {
  const secret = jwkToPem(privateKey, { private: true });
  const options = {
    ...additionalOptions,
    keyid: privateKey.kid,
    algorithm: privateKey.alg,
  };
  return createJwt({
    payload,
    secret,
    options,
  });
};

export const verifyAndDecodeIdToken = ({idToken, jwks}) => {
  const header = jwt_decode(idToken, { header: true });
  const payload = jwt_decode(idToken);
  const jwk = jwks.keys.filter(jwk => jwk.kid === header.kid)[0];
  const publicKey = jwkToPem(jwk);
  const verifyResult = verifySignature({idToken, publicKey});
  return {
    header,
    payload,
    verifyResult,
  };
};

export const verifySignature = ({idToken, publicKey}) => {
  try {
    jwt.verify(idToken, publicKey);
    return true;
  } catch (e) {
    console.log(e);
    return false;
  }
};