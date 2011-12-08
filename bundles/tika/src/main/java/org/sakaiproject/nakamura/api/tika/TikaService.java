/**
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.api.tika;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;

/**
 * OSGi service to wrap {@link Tika} and load a config file found local this bundle. This
 * service can be updated to match whatever underlying version of Tika that is being used.
 * 
 * The traditional way of getting a reference to Tika is still applicable which is how
 * this service gets its internal reference.
 * 
 * The annotations on this class are used only to generate the serviceComponents.xml file
 * but the maven bundle plugin is not used since we copy the manifest from the tika-bundle
 * artifact, so don't change these annotations and expect the changes to magically appear.
 */
public interface TikaService {
  String detect(byte[] prefix);

  String detect(byte[] prefix, String name);

  String detect(InputStream stream, String name) throws IOException;

  String detect(InputStream stream, Metadata metadata) throws IOException;

  String detect(InputStream stream) throws IOException;

  String detect(File file) throws IOException;

  String detect(URL url) throws IOException;

  String detect(String name);

  Reader parse(InputStream stream, Metadata metadata) throws IOException;

  Reader parse(InputStream stream) throws IOException;

  Reader parse(File file) throws IOException;

  Reader parse(URL url) throws IOException;

  String parseToString(InputStream stream, Metadata metadata) throws IOException,
      TikaException;

  String parseToString(InputStream stream) throws IOException, TikaException;

  String parseToString(File file) throws IOException, TikaException;

  String parseToString(URL url) throws IOException, TikaException;

  int getMaxStringLength();
}