#!/bin/sh
#while ! curl http://iroha-postgres:5432/ 2>&1 | grep '52'
#do
#done
sleep 30
irohad --genesis_block genesis.block --config config.docker --keypair_name $KEY --overwrite-ledger
