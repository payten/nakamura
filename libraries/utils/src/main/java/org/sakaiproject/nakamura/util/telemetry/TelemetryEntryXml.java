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
 *
 *
 * portions of this file copyright (c) 2010, OmniTI Computer Consulting, Inc.
 * see https://github.com/omniti-labs/reconnoiter/blob/master/src/java/com/omniti/jezebel/ResmonResult.java
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name OmniTI Computer Consulting, Inc. nor the names
 *       of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written
 *       permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.sakaiproject.nakamura.util.telemetry;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.transform.sax.TransformerHandler;
import java.util.Map;

public class TelemetryEntryXml {

  public static void write(TransformerHandler hd, TelemetryEntry entry) throws SAXException {
    AttributesImpl atts = new AttributesImpl();
    atts.addAttribute("","","module","CDATA", entry.getModule());
    atts.addAttribute("","","service","CDATA", entry.getService());
    hd.startElement("","","ResmonResult",atts);
    atts.clear();
    hd.startElement("","","last_update",atts);
    String epochString = "" + entry.getLastUpdate();
    char epochChars[] = epochString.toCharArray();
    hd.characters(epochChars, 0, epochChars.length);
    hd.endElement("","","last_update");
    for (Map.Entry<String,TelemetryData> e : entry.getMetrics().entrySet()) {
      TelemetryData d = e.getValue();
      atts.clear();
      atts.addAttribute("","","name","CDATA",e.getKey());
      atts.addAttribute("","","type","CDATA",d.type);
      hd.startElement("","","metric",atts);
      char valueChars[] = d.value.toCharArray();
      hd.characters(valueChars, 0, valueChars.length);
      hd.endElement("","","metric");
    }
    atts.clear();
    hd.startElement("", "", "state", atts);
    hd.characters(entry.getStatus().toString().toCharArray(), 0, entry.getStatus().toString().length());
    hd.endElement("", "", "state");
    hd.endElement("","","ResmonResult");
  }
}
