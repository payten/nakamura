#!/bin/sh
#
# Run all Ruby integration tests in the same directory as this script and whose
# name ends with '-test.rb'. This script outputs just a summary of the tests
# results. To see the full test output, run testall.rb.
#
STATUS=0
TESTS=`ls *-test.rb`
for i in $TESTS
do
echo $i
./$i > temp-output.txt
CODE=$?
if [ "$CODE" -ne "0" ]; then
  cat < temp-output.txt
  STATUS=$CODE
fi
done
rm temp-output.txt
exit $STATUS