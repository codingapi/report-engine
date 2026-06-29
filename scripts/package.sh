#!/usr/bin/env bash

cd ../report-frontend
pnpm i
pnpm run build
pnpm run build:app-pc


rm -rf ../report-engine-example/src/main/resources/static
mkdir -p ../report-engine-example/src/main/resources/static

cp -r apps/app-pc/dist/* ../report-engine-example/src/main/resources/static/

cd ../
mvn clean package -DskipTests
cp ./report-engine-example/target/*.jar ./scripts/server.jar