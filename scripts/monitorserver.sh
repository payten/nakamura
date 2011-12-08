#!/bin/bash

#
# Run the preview_processor.rb script every INTERVAL seconds.
#

# In seconds
INTERVAL=15

### No need to edit below this line. ###
PROG=`basename $0`

function usage(){
   cat << EOF

   usage: $PROG [-h|--help] server adminpassword term-extraction address [interval]
   
   example: $PROG http://localhost:8080/ admin http://localhost:8085/ 20

EOF
   
}

# server and password are required
if [[ -z $1 || -z $2 || -z $3 ]]; then
    usage
    exit 1
fi

if [[ $1 == "-h" || $1 == "--help " ]]; then
    usage
    exit 0
fi

# interval is optional
if [[ ! -z $4 ]]; then
    INTERVAL=$4
fi

SCRIPTS_DIR=`dirname $0`
pushd $SCRIPTS_DIR > /dev/null
while [[ 1 -eq 1 ]]
do
   ./preview_processor.rb -s $1 -p $2 -t $3 -i $INTERVAL
done
# This will probably never be reached but its a good idea to pop what you push.
popd > /dev/null
