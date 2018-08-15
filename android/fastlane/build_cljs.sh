#! /bin/bash -ex
echo $pwd

pushd ../..
yarn
lein prod-build 2>&1 | tee /dev/tty | grep -e SEVERE -e ERROR | wc -l | { read wc; test $wc -eq 0; }
popd
