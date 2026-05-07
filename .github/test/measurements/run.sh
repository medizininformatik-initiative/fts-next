#!/usr/bin/env sh

for c in 16 64 128; do
    MAX_SEND_CONCURRENCY=$c \
        OUT_DIR=.github/test/measurements/conc-$c \
        make -C .github/test measure-e2e
done
