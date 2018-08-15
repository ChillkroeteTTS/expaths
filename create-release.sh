#! /bin/bash -ex

lein prod-build

# bump version

cd android
./gradlew clean
./gradlew assembleRelease --daemon

