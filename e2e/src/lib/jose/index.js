// eslint-disable-next-line no-undef
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
  const jwk = jwks.keys.filter(jwk => jwk.kid === header.kid)[0];
  const publicKey = jwkToPem(jwk);
  const verifyResult = jwt.verify(idToken, publicKey);
  return {
    header,
    payload: verifyResult,
  };
};
