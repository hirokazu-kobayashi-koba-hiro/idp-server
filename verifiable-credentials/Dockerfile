FROM node:20

COPY ./package.json .
COPY ./package-lock.json .
RUN npm install
COPY libs/Encoder.js ./node_modules/@vaultie/lds-merkle-proof-2019/src/Encoder.js
COPY ./app ./app

EXPOSE 8000

CMD [ "node", "./app/index.js" ]
