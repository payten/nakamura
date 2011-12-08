#!/bin/bash

PROG=`basename $0`

function usage(){
  cat << EOF
  usage: $PROG [-h|--help] port max-term-number
  example: $PROG 8085 5
EOF
}

if [[ $1 == "-h" || $1 == "--help" ]]; then
    usage
    exit 0
fi

PORT=8085
LENGTH=5

if [[ ! -z $1 ]]; then
    PORT=$1
fi

if [[ ! -z $2 ]]; then
  LENGTH=$2
fi

python code.py $PORT $LENGTH
