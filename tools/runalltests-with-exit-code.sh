#!/bin/bash
STATUS=0
TESTS=`find . -name 'target' -prune -o -name testall-with-exit-code.sh -print`
for i in $TESTS
do
	pushd `dirname $i` 1> /dev/null
	./testall-with-exit-code.sh
	CODE=$?
	if [ "$CODE" -ne "0" ]; then
		STATUS=$CODE
	fi
	popd 1> /dev/null
done

exit $STATUS
