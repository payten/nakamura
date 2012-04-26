#!/bin/bash
TESTS=`find . -name 'target' -prune -o -name testall.sh -print`
for i in $TESTS
do
	pushd `dirname $i`
	./testall.sh
	popd
done

