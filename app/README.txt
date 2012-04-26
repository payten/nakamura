This is the Apache Sling-based Sakai Nakamura that uses OSGi.
 
To run
   mvn clean install
   . tools/version
   java -Dfile.encoding=UTF8 -jar app/target/org.sakaiproject.nakamura.app-${NAKAMURA_VERSION}.jar
 
This will start Apach Felix configured with Sling and the Sakai Nakamura
bundles.
 
You will find the core bundles under /bundles, non-core bundles under
contrib/ and some libraries under libraries/.
