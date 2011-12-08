Sakai Nakamura :: JackRabbit Server Bundle
==========================================

This bundle expands the Sling JCR JackRabbit Server bundle to inject
our implementation of AccessControlProviderFactoryImpl and
SlingServerRepository and to use Sparse as storage rather than JCR.


repository.xml
--------------
This file was copied from the Sling JCR JackRabbit Server bundle with
modifications to add in the SparseUserManager and SparseLoginModule.
There are other changes that are best noted by checking the revision
history.


serviceComponents.xml
---------------------
This file is copied from the Sling JCR JackRabbit Server bundle with the
following modifications:

1. Comment out:
  1.1 TestContentLoader
  1.2 JNDI registration
  1.3 RMI registration

2. Update SlingServerRepository by:
  2.1 Change the implementation class of SlingServerRepository to:
    org.apache.sling.jcr.jackrabbit.server.impl.security.dynamic.SakaiSlingServerRepository
  2.2 Add pool.* properties
  2.3 Add a reference to the Sparse repository:
    <reference name="repository" interface="org.sakaiproject.nakamura.api.lite.Repository" cardinality="1..1" policy="static" bind="bindRepository" unbind="unbindRepository"/>


MANIFEST.MF
-----------
This file is copied from the Sling JCR JackRabbit Server bundle with the
following modifications:

1. Add org.sakaiproject.nakamura.api.lite.jackrabbit to Export-Package
2. Add org.sakaiproject.nakamura.utils to Import-Package
