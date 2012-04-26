#/bin/sh
if [ $# -lt 1 ] ; then
  echo "Usage: $0 <version number>"
  echo "Don't include -SNAPSHOT."
  exit 0
fi
set -o nounset
set -o errexit
cversion=$1
nversion=$2


function simple_replace {
    sed "s/$cversion-SNAPSHOT/$nversion/" $1 > $1.new
    restore $1
}

function ux_tag_replace {
    sed "s/\<ux\>$cversion-SNAPSHOT\<\/ux\>/\<ux\>$nversion\<\/ux\>/" $1 > $1.new
    restore $1
}

function artifact_version_replace {
    perl -pi.bak -e "undef $/; s/($1<\/artifactId>\n\s+<version)>$cversion-SNAPSHOT/\$1>$nversion/" $2
    rm $2.bak
    git add $2
}

function tag_replace {
    sed "s/\>$cversion-SNAPSHOT\</\>$nversion\</" $1 > $1.new
    restore $1
}

function restore {
    mv $1.new $1
    git add $1
}

echo "Moving config files from $cversion-SNAPSHOT to $nversion"
ux_tag_replace app/pom.xml
artifact_version_replace org.sakaiproject.nakamura.jetty-config app/pom.xml
simple_replace tools/version
simple_replace tools/version.bat
simple_replace webstart/src/main/jnlp/template.vm
simple_replace app/src/main/bundles/list.xml

otherpoms=`find . -path "./contrib/*pom.xml" -o -path "./samples/*pom.xml" -o -path "./sandbox/*pom.xml" -o -path "./webstart/*pom.xml" -o -path "./modelling/*pom.xml"`
for file in $otherpoms
  do
    tag_replace $file
  done


git commit -m "release_pre_process: Moving config files from $cversion-SNAPSHOT to $nversion"

