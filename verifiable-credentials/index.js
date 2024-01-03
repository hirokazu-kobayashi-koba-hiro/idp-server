const { parse } = require("did-resolver")
const express = require('express')
const bodyParser = require('body-parser')
const app = express()
const port = 3000
const { EthrDID } = require('ethr-did')
const { createVerifiableCredentialJwt, verifyCredential } = require('did-jwt-vc')
const  axios = require("axios")
const { spawn, exec } = require('child_process');
const { issueVcBlockCert } = require("./blockCert");

const issuer = new EthrDID({
  identifier: '0xf1232f840f3ad7d23fcdaa84d6c66dac24efb198',
  privateKey: 'd8b595680851765f38ea5405129244ba3cbad84467d190859f4c8b20c1ff6c75'
})

app.use(bodyParser.json())

app.listen(port, () => {
  console.log(`Example app listening on port ${port}`)
})

app.get("/health", (req, res) => {
  res.send("OK")
})

app.post("/v1/verifiable-credentials/block-cert", async (request, response) => {
  console.log(request.body)
  if (!request.body.vc) {
    response.status(400)
    response.send('{"error": "invalid_request", "error_description": "vc is required"}').status(400)
    return
  }
  const { payload, error } = await issueVcBlockCert({
    vcPayload: request.body.vc,
  })
  if (payload && !error) {
    console.log(payload)
    response.send(`{ "vc": ${JSON.stringify(payload)}}`)
    return
  }
  response.status(400)
  response.send(`{"error": "invalid_request", "error_description": ${error}"}`)
})

app.post('/v1/verifiable-credentials/did-jwt', async (request, response) => {
  console.log(request.body)
  if (!request.body.vc) {
    response.status(400)
    response.send('{"error": "invalid_request", "error_description": "vc is required"}').status(400)
    return
  }
  const { payload, error } = await issueVcJwt({
    vcPayload: request.body.vc,
  })
  if (payload && !error) {
    response.send(`{ "vc": "${payload}"}`)
    return
  }
  response.status(400)
  response.send('{"error": "invalid_request"}')
})

app.post("/v1/verifiable-credentials/did-jwt/verify", async (request, response) => {
  console.log(request.body)
  if (!request.body.vc_jwt) {
    response.status(400)
    response.send('{"error": "invalid_request", "error_description": "vc_jwt is required"}').status(400)
    return
  }
  const { payload, error } = await verifyVcJwt({
    vcJwt: request.body.vc_jwt,
  })
  if (payload && !error) {
    console.log(payload)
    response.send(`{ "verified_vc": ${JSON.stringify(payload)}}`)
    return
  }
  response.status(400)
  response.send('{"error": "invalid_request"}')
})

const issueVcJwt = async ({ vcPayload }) => {
  try {

    const vcJwt = await createVerifiableCredentialJwt(vcPayload, issuer)
    console.log(vcJwt)
    return {
      payload: vcJwt,
    }
  } catch (e) {
    console.error(e)
    return {
      error: e
    }
  }
}

const verifyVcJwt = async ({ vcJwt }) => {
  try {
    const verifiedVC = await verifyCredential(vcJwt, new UniversalResolver())
    console.log(verifiedVC)
    return {
      payload: verifiedVC
    }
  } catch (e) {
   console.error(e)
   return {
     error: e
   }
  }
}

class UniversalResolver {

  async resolve(didUrl, options = {}) {
    const parsed = parse(didUrl)
    console.log(parsed)
    if (parsed === null) {
      return {
        didResolutionMetadata: { error: 'invalidDid' },
      }
    }
    const { did } = parsed
    const encodedUrl = encodeURI(`https://dev.uniresolver.io/1.0/identifiers/${did}`)
    const didResponse = await get({ url: encodedUrl, headers: {} })
    console.log(didResponse)
    return didResponse.data
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
