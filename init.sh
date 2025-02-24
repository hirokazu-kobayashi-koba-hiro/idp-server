#!/bin/zsh

echo "create api key and secret"

echo "generate api key"
uuidgen | tr 'A-Z' 'a-z'

echo "generate api secret"
echo $(uuidgen | tr 'A-Z' 'a-z') | base64
