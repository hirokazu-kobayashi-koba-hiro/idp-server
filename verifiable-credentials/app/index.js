import { parse } from "did-resolver";
import express from "express";
import bodyParser from "body-parser";

import { EthrDID } from "ethr-did";
import { createVerifiableCredentialJwt, verifyCredential } from "did-jwt-vc";
import axios from "axios";
import { issueBlockCert } from "./cert.js";
const app = express();
const port = 8000;
const issuer = new EthrDID({
  identifier: process.env.ADDRESS,
  privateKey: process.env.PRIVATE_KEY,
});

app.use(bodyParser.json());

app.listen(port, () => {
  console.log(`Example app listening on port ${port}`);
});

app.get("/health", (req, res) => {
  res.send("OK");
});

app.post("/v1/verifiable-credentials/block-cert", async (request, response) => {
  console.log(request.body);
  if (!request.body.credential) {
    response.status(400);
    response
      .send(
        '{"error": "invalid_request", "error_description": "credential is required"}',
      )
      .status(400);
    return;
  }
  const { payload, error } =
    (await issueBlockCert({
      address: process.env.ADDRESS,
      privateKey: process.env.PRIVATE_KEY,
      verificationMethod:
        process.env.VERIFICATION_METHOD,
      chain: process.env.CHAIN,
      credential: request.body.credential,
    })) || {};
  if (payload && !error) {
    console.log(payload);
    response.send(`{ "vc": ${JSON.stringify(payload)}}`);
    return;
  }
  response.status(400);
  response.send(`{"error": "invalid_request", "error_description": "${error}"}`);
});

app.post("/v1/verifiable-credentials/block-cert/verify", async (request, response) => {
  console.log(request.body);
  if (!request.body.vc_block_cert) {
    response.status(400);
    response
      .send(
        '{"error": "invalid_request", "error_description": "vc_block_cert is required"}',
      )
      .status(400);
    return;
  }
  const { payload, error } =
  (await verifyBlockCert(request.body.vc_block_cert)) || {};
  if (payload && !error) {
    console.log(payload);
    response.send(`{ "verify_result": ${JSON.stringify(payload)}}`);
    return;
  }
  response.status(400);
  response.send(`{"error": "invalid_request", "error_description": "${error}"}`);
});

import { Certificate } from '@blockcerts/cert-verifier-js/dist/verifier-node/index.js';
import { Decoder } from '@vaultie/lds-merkle-proof-2019';

export const verifyBlockCert = async (certificateDefinition) => {
  try {
    const certificate = new Certificate(certificateDefinition);
    await certificate.init();
    const verificationResult = await certificate.verify(({code, label, status, errorMessage}) => {
      console.log("Code:", code, label, " - Status:", status);
      if (errorMessage) {
        console.log(`The step ${code} fails with the error: ${errorMessage}`);
      }
    });

    if (verificationResult.status === "failure") {
      console.log(`The certificate is not valid. Error: ${verificationResult.errorMessage}`);
    }
    return {
      payload: verificationResult,
    };
  } catch (e) {
    console.error(e)
    return {
      error: e
    }
  }
};
export const decodeWithBase58 = (value) => {
  const decoder = new Decoder(value)
  const decodedValue = decoder.decode();
  console.log(decodedValue);
  return decodedValue;
}

app.post("/v1/verifiable-credentials/did-jwt", async (request, response) => {
  console.log(request.body);
  if (!request.body.credential) {
    response.status(400);
    response
      .send(
        '{"error": "invalid_request", "error_description": "vc is required"}',
      )
      .status(400);
    return;
  }
  const { payload, error } = await issueVcJwt({
    credential: request.body.credential,
    headers: request.body.headers,
  });
  if (payload && !error) {
    response.send(`{ "vc": "${payload}"}`);
    return;
  }
  response.status(400);
  response.send('{"error": "invalid_request"}');
});

app.post(
  "/v1/verifiable-credentials/did-jwt/verify",
  async (request, response) => {
    console.log(request.body);
    if (!request.body.vc_jwt) {
      response.status(400);
      response
        .send(
          '{"error": "invalid_request", "error_description": "vc_jwt is required"}',
        )
        .status(400);
      return;
    }
    const { payload, error } = await verifyVcJwt({
      vcJwt: request.body.vc_jwt,
    });
    if (payload && !error) {
      console.log(payload);
      response.send(`{ "verified_vc": ${JSON.stringify(payload)}}`);
      return;
    }
    response.status(400);
    response.send('{"error": "invalid_request"}');
  },
);

const issueVcJwt = async ({ credential, headers }) => {
  try {
    const vcJwt = await createVerifiableCredentialJwt(credential, issuer, {
      header: headers,
    });
    console.log(vcJwt);
    return {
      payload: vcJwt,
    };
  } catch (e) {
    console.error(e);
    return {
      error: e,
    };
  }
};

const verifyVcJwt = async ({ vcJwt }) => {
  try {
    const verifiedVC = await verifyCredential(vcJwt, new UniversalResolver());
    console.log(verifiedVC);
    return {
      payload: verifiedVC,
    };
  } catch (e) {
    console.error(e);
    return {
      error: e,
    };
  }
};

class UniversalResolver {
  async resolve(didUrl, options = {}) {
    const parsed = parse(didUrl);
    console.log(parsed);
    if (parsed === null) {
      return {
        didResolutionMetadata: { error: "invalidDid" },
      };
    }
    const { did } = parsed;
    const encodedUrl = encodeURI(
      `https://dev.uniresolver.io/1.0/identifiers/${did}`,
    );
    const didResponse = await get({ url: encodedUrl, headers: {} });
    console.log(didResponse);
    return didResponse.data;
  }
}

const get = async ({ url, headers }) => {
  try {
    return await axios.get(url, {
      maxRedirects: 0,
      headers,
    });
  } catch (e) {
    return e.response ? e.response : e;
  }
};
