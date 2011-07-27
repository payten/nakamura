#!/bin/bash

ADMIN_PASSWORD=admin
SAKAI_URL=http://localhost:8080
PASS=groups

curl -u admin:$ADMIN_PASSWORD -F:name=grouper-admin -Fpwd=$PASS -FpwdConfirm=$PASS $SAKAI_URL/system/userManager/user.create.json

curl -u admin:$ADMIN_PASSWORD -F:member=grouper-admin $SAKAI_URL/system/userManager/group/administrators.update.json 
