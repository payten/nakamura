Sakai Nakamura :: Tika Bundle
=============================

This bundle extracts the externally built Apache Tika artifacts to
inject modifications to tika-mimetypes.xml. To construct the resulting
artifact, we expand org.apache.tika:tika-bundle to add our own
tika-mimetypes.xml and tika-config.xml. We use the manifest from the
tika artifact with some additions.


MANIFEST.MF
-----------
META-INF/MANIFEST.MF is a copy of the like named file from the
org.apache.tika:tika-core artifact (or tika-bundle) with the
modifications below. serviceComponents.xml is added manually because we
copy the manifest from tika-bundle. There are annotations on TikaService
that are used to generate serviceComponents.xml but it is copied and
stored statically in the source tree.

1. Add an entry for serviceComponents.xml
  Service-Component: OSGI-INF/serviceComponents.xml

2. Add to the Export-Package header:
  org.sakaiproject.nakamura.api.tika

3. Add to the Import-Package header:
  org.apache.sling.commons.osgi

4. Replace the following headers accordingly:
  Bundle-Name: Sakai Nakamura :: Tika Bundle
  Bundle-Vendor: Sakai Project
  Bundle-Version: 0.9.1.1_SNAPSHOT
  Bundle-SymbolicName: org.sakaiproject.nakamura.tika

5. Add to the Bundle-Description header:
  A bundle that wraps Tika with changes to tika-mimetypes.xml and adds
  an OSGi tika service.


tika-mimetypes.xml
------------------
tika-mimetypes.xml is copied from the tika-core artifact (inlined in
the tika-bundle artifact) with the following modifications made:

1. Remove comment matching from XML processing:
  <mime-type type="application/xml">
    <root-XML localName="p"/>
    <root-XML localName="P"/>
+    <root-XML localName="div"/>
+    <root-XML localName="DIV"/>
    <root-XML localName="script"/>
    <root-XML localName="SCRIPT"/>
    ...
    <magic priority="50">
      <match value="&lt;?xml" type="string" offset="0"/>
      <match value="&lt;?XML" type="string" offset="0"/>
-      <match value="&lt;!--" type="string" offset="0"/>
+      <!-- <match value="&lt;!- -" type="string" offset="0"/> -->

2. Add comment and link matching to html processing:
  <mime-type type="text/html">
    ...
    <magic priority="40">
+      <match value="&lt;!--" type="string" offset="0:5"/>
      <match value="&lt;!DOCTYPE HTML" type="string" offset="0:64"/>
      <match value="&lt;!doctype html" type="string" offset="0:64"/>
      <match value="&lt;HEAD" type="string" offset="0:64"/>
      <match value="&lt;head" type="string" offset="0:64"/>
      <match value="&lt;TITLE" type="string" offset="0:64"/>
      <match value="&lt;title" type="string" offset="0:64"/>
+      <match value="&lt;link" type="string" offset="0:64"/>
      ...
      <match value="&lt;h1" type="string" offset="0"/>
      <match value="&lt;H1" type="string" offset="0"/>
+      <match value="&lt;div" type="string" offset="0"/>
+      <match value="&lt;DIV" type="string" offset="0"/>
+      <match value="&lt;p" type="string" offset="0"/>
+      <match value="&lt;P" type="string" offset="0"/>
      <match value="&lt;!doctype HTML" type="string" offset="0"/>
      <match value="&lt;!DOCTYPE html" type="string" offset="0"/>


tika-config.xml
---------------
Since we modify tika-mimetypes.xml and put it back in the same location
as expected by Tika, there is no need to introduce tika-config.xml at
this time. If we want to add our own parsers or limit the parsers that
can be used by Tika, then we should introduce tika-config.xml to take
care of this.