#!/bin/bash
# set ENV:"echo pass" and mount Azure File to /afs
${ENV?exit 1} && cd /afs