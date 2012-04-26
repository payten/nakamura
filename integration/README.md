## Integration Test Module

This module doesn't produce any real artifacts. It's just using maven as a wrapper for some tasks which take care of running the nakamura integration tests for you. There is a profile in the parent pom called "integration", so in order to run these tasks, do this from the top-level of the nakamura source tree:

    mvn -Pintegration

These are the steps it will follow:

1. (off-by-default) delete the existing sling folder at `integration/sakai/sling`
2. Start nakamura using a `java` task from the `antrun` plugin. It uses `integration/sakai/sling` as the sling home folder
3. Wait for startup to complete, then run all the integration tests.
4. Shutdown nakamura, again using the `antrun` plugin.

Each of these steps is controlled by a boolean parameter. If the parameter is true, that step runs. Each parameter has a default value which can be overridden at the command line. Here are the four parameters along with their default values:

1. `-Dsling.clean=false`
2. `-Dsling.start=true`
3. `-Dsling.test=true`
4. `-Dsling.stop=true`

## Other considerations
The testing task is designed to allow all the tests to finish running, but if any single test fails or throws an error, the task returns an error code, and maven is instructed to fail the build in that case.

The script we use to wait for startup is not very smart. It just greps the standard log and waits for the message from the World bundle that it has started. The World bundle just happens to be the last bundle to start in our current configuration. If that ever changes, the script will need to be updated.

The wait script also has a timeout, set at around five minutes. If nakamura hasn't started by then, something is wrong and it probably never will. In this case, the script will exit with an error code and maven will fail the build.

