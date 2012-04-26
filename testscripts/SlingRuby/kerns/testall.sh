#!/bin/sh
#
# Run all Ruby integration tests in the same directory as this script and whose
# name ends with '-test.rb'. This script outputs just a summary of the tests
# results. To see the full test output, run testall.rb.
#
TESTS=`ls kern-*.rb`
for i in $TESTS
do
echo $i `./$i | grep failure`
done
