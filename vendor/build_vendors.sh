#!/bin/sh
set -eux -o pipefail

pushd electric && ./src-build/build.sh && popd
pushd hyperfiddle-contrib && ./src-build/build.sh && popd
pushd hyperfiddle && ./src-build/build.sh && popd
