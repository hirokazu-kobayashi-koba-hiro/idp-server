FROM node
#FROM amazonlinux:2023.3.20231218.0

#RUN yum update -y && \
#    curl -fsSL https://rpm.nodesource.com/setup_16.x | bash && \
#    yum install -y nodejs

COPY ./index.js .
COPY ./package.json .
COPY ./package-lock.json .
RUN npm install
EXPOSE 3000
CMD [ "node", "index.js" ]