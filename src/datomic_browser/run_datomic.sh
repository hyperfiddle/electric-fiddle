#!/bin/sh
set -eux -o pipefail

./state/datomic-pro/bin/transactor config/samples/dev-transactor-template.properties >>state/datomic.log 2>&1 &
