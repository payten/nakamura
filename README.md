This is the master branch of Nakamura maintained by the Sakai Foundation. 

This is the Sling-based Sakai OAE kernel that uses OSGi.


To run:

    mvn clean install
    . tools/version
    java -Dfile.encoding=UTF8 -jar app/target/org.sakaiproject.nakamura.app-${NAKAMURA_VERSION}.jar

This will start Felix configured with Sling and the Sakai nakamura bundles.

You will find the core bundles under bundles/, non-core bundles under
contrib/ and some libraries under libraries/.

