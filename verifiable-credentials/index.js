const express = require('express')
const bodyParser = require('body-parser')
const app = express()
const port = 3000
const { EthrDID } = require('ethr-did')
const { createVerifiableCredentialJwt } = require('did-jwt-vc')

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

app.post('/v1/verifiable-credentials/did-jwt', async (request, response) => {
  console.log(request.body)
  if (!request.body.vc) {
    response.status(400)
    response.send('{"error": "invalid_request", "error_description": "vc_payload is required"}').status(400)
    return
  }
  const { payload, error } = await issueVcWithDid({
    vcPayload: request.body.vc,
  })
  if (payload && !error) {
    response.send(`{ "vc": "${payload}"}`)
    return
  }
  response.status(400)
  response.send('{"error": "invalid_request"}')
})


const issueVcWithDid = async ({ vcPayload }) => {
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
