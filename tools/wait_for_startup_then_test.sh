#!/bin/sh
if [ $# -lt 1 ] ; then
  echo "Usage: $0 <path-to-sling-logfile>"
  echo "Path can either be absolute or relative to the current directory."
  exit 0
fi
echo "Waiting for startup..."
TIMEOUT=150
TIMER=0
while ! [ $TIMER -gt $TIMEOUT ] && ! [ -e $1 ] ;do sleep 2; TIMER=$((TIMER+1)); done
while ! [ $TIMER -gt $TIMEOUT ] && ! grep "org.sakaiproject.nakamura.world BundleEvent STARTED" $1 1> /dev/null ;do sleep 2; TIMER=$((TIMER+1)); done

if [ $TIMER -gt $TIMEOUT ]
then
    echo "Startup did not complete for a long time. Giving up."
    exit 1
fi

echo "Startup complete..."
./tools/runalltests-with-exit-code.sh

exit $?