#!/usr/bin/env bash
set -eux -o pipefail

echo "starting Datomic transactor..."
state/datomic-pro/bin/transactor config/samples/dev-transactor-template.properties >>state/datomic.log 2>&1 &
datomic_transactor_pid=$!
echo "Datomic transactor PID: $datomic_transactor_pid"

echo "waiting for Datomic port..."
#timeout 10 sh -c 'until nc -z $0 $1; do sleep 1; done' localhost 4334
while ! timeout 1 bash -c "echo > /dev/tcp/localhost/4334"; do sleep 1; done
echo "Datomic ready on localhost:4334"

echo "starting application..."
java -cp app.jar clojure.main -m prod
