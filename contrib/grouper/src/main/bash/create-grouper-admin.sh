#!/bin/bash

ADMIN_PASSWORD=admin
SAKAI_URL=http://localhost:8080
USERNAME=grouper-admin
PASS=groups

CREATE_CODE=`curl -q -o /dev/null -w '%{http_code}' -e /system/console/grouper -u admin:$ADMIN_PASSWORD -F:name=grouper-admin -Fpwd=$PASS -FpwdConfirm=$PASS $SAKAI_URL/system/userManager/user.create.json 2>/dev/null`

if [[ $CREATE_CODE == "200" ]]; then
	echo "Created $USERNAME"
else
	echo "$CREATE_CODE Failed to create $USERNAME"
	exit 1
fi

GROUPADD_CODE=`curl -q -o /dev/null -w '%{http_code}' -e /system/console/grouper -u admin:$ADMIN_PASSWORD -F:member=grouper-admin $SAKAI_URL/system/userManager/group/administrators.update.json 2>/dev/null`

if [[ $GROUPADD_CODE == 200 ]]; then
	echo "Added $USERNAME to the administrators group"
else
	echo "Failed to add $USERNAME to the administrators group"
	exit 1
fi
