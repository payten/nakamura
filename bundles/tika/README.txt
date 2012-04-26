=============================
Sakai Nakamura :: Tika Bundle
=============================

This bundle extracts the externally built Apache Tika artifacts to
inject modifications to tika-mimetypes.xml. To construct the resulting
artifact, we expand org.apache.tika:tika-bundle to add our own
tika-mimetypes.xml and tika-config.xml. We use the manifest from the
tika artifact with some additions.

We disect tika-core rather than tika-bundle because mimetypes.xml exists
only in tika-core. Two OSGi services were introduced in tika-bundle 1.0,
Detector and Parser, but we also a service facade for org.apache.tika.Tika
because this class simplifies the usage of Tika greatly. It's worth the
little bit of extra work when upgrading to have this service available.

Notes About Upgrading This Bundle
=================================

Upgrading this project requires some special steps since we're using the
tika-core artifact as a starting point then adding some extra bits to it.
Some of these files are generated and some are copied from tika-core.

We introduce one service that needs to be wired up in OSGi. Rather than
retooling this bundle to be built using the bundle plugin, we generate
the files we need and add to some existing ones.

Generate Required SCR Files
---------------------------
Run a build of this project. Copy target/scr-plugin-generated/OSGI-INF
to src/main/resources/OSGI-INF. This should include at least
serviceComponents.xml and may also include scr-plugin/scrinfo.xml.

MANIFEST.MF
-----------
META-INF/MANIFEST.MF is a copy of the like named file from the
org.apache.tika:tika-core artifact with the
modifications below. serviceComponents.xml is added manually because we
copy the manifest from tika-core. There are annotations on TikaService
that are used to generate serviceComponents.xml but it is copied and
stored statically in the source tree.

0. Copy META-INF/MANIFEST.MF from tika-core to src/main/resources/META-INF

1. Add an entry for serviceComponents.xml:
  Service-Component: OSGI-INF/serviceComponents.xml

2. Append to the Export-Package header:
  ,org.sakaiproject.nakamura.api.tika

3. Import whatever we need to use (normally constructed by bnd):
  DynamicImport-Package: *

4. Replace the following headers accordingly:
  Bundle-Name: Sakai Nakamura :: Tika Core Bundle
  Bundle-Vendor: The Apache Software Foundation, Sakai Project
  Bundle-Version: 1.0.1.1_SNAPSHOT
  Bundle-SymbolicName: org.sakaiproject.nakamura.tika
  Built-By: <whoever is listed>, <your name>
  Tool: <whatever is listed>, hand

5. Add to the Bundle-Description header:
  A bundle that wraps Tika with changes to tika-mimetypes.xml and adds
  an OSGi service for org.apache.tika.Tika.


tika-mimetypes.xml
------------------
src/main/resources/org/apache/tika/mime/tika-mimetypes.xml is copied from
the tika-core artifact with the following modifications made:

1. Remove comment matching from XML processing:
  <mime-type type="application/xml">
    ...
    <magic priority="50">
      <match value="&lt;?xml" type="string" offset="0"/>
      <match value="&lt;?XML" type="string" offset="0"/>
-      <match value="&lt;!--" type="string" offset="0"/>
+      <!-- <match value="&lt;!- -" type="string" offset="0"/> -->

2. Add comment and link matching to html processing:
  <mime-type type="text/html">
    <root-XML localName="p"/>
    <root-XML localName="P"/>
+    <root-XML localName="div"/>
+    <root-XML localName="DIV"/>
    <root-XML localName="script"/>
    <root-XML localName="SCRIPT"/>
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

As of Tika 1.0, it is also possible to use tika-parsers instead of
tika-bundle and introduce the libraries needed to activate any parsers.