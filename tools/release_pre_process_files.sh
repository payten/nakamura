#/bin/sh
set -o nounset
set -o errexit
cversion=$1


function simple_replace {
    sed "s/$cversion-SNAPSHOT/$cversion/" $1 > $1.new
    restore $1
}

function ux_tag_replace {
    sed "s/\<ux\>$cversion-SNAPSHOT\<\/ux\>/\<ux\>$cversion\<\/ux\>/" $1 > $1.new
    restore $1
}

function tag_replace {
    sed "s/\>$cversion-SNAPSHOT\</\>$cversion\</" $1 > $1.new
    restore $1
}

function restore {
    mv $1.new $1
    git add $1
}

echo "Moving config files from $cversion-SNAPSHOT to $cversion"
ux_tag_replace app/pom.xml
simple_replace tools/version
simple_replace tools/version.bat
simple_replace webstart/src/main/jnlp/template.vm
tag_replace app/src/main/bundles/list.xml

otherpoms=`find . -path "./contrib/*pom.xml" -o -path "./samples/*pom.xml" -o -path "./sandbox/*pom.xml" -o -path "./webstart/*pom.xml" -o -path "./modelling/*pom.xml"`
for file in $otherpoms
  do
    tag_replace $file
  done


git commit -m "switching from SNAPSHOT to full version in config files"

