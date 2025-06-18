#!/usr/bin/env bash
set -eux -o pipefail

mkdir -p state
pushd state
curl https://datomic-pro-downloads.s3.amazonaws.com/1.0.6735/datomic-pro-1.0.6735.zip -O -C -
if [ ! -d "datomic-pro" ]; then
    unzip datomic-pro-1.0.6735.zip
    mv datomic-pro-1.0.6735 datomic-pro
fi

set +e
wget --no-clobber -O mbrainz.tar https://s3.amazonaws.com/mbrainz/datomic-mbrainz-1968-1973-backup-2017-07-20.tar
set -e
if [ ! -d "mbrainz" ]; then
    tar -xf mbrainz.tar
fi

datomic-pro/bin/transactor config/samples/dev-transactor-template.properties >>datomic.log 2>&1 &
datomic_transactor_pid=$!

set +ex
echo "waiting for Datomic port..."
while ! timeout 1 bash -c "echo > /dev/tcp/localhost/4334 2> /dev/null" 2> /dev/null; do sleep 1; done
set -ex

# https://datomic.narkive.com/OUskfRdr/backup-error
datomic-pro/bin/datomic restore-db file:`pwd`/mbrainz-1968-1973 datomic:dev://localhost:4334/mbrainz-1968-1973
kill $datomic_transactor_pid
