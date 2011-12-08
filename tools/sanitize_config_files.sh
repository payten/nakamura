#!/bin/sh
if [ $# -lt 1 ] ; then
  echo "Usage: $0 <path-to-sling-directory>"
  echo "Path can either be absolute or relative to the current directory."
  exit 0
fi

SLING_DIR=$1
for file in `find $SLING_DIR/config -name "*.config"`
  do
    sed '/^service\.bundleLocation/d' $file > $file.new
    mv $file.new $file
  done