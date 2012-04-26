/*
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
package org.sakaiproject.nakamura.resource.lite.servlet.post.operations;

import com.google.common.collect.ImmutableMap;

import junit.framework.Assert;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.servlets.post.Modification;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 *
 */
@RunWith(value = MockitoJUnitRunner.class)
public class SakaiDocPagePublishOperationTest {

  @Mock
  protected SlingHttpServletRequest request;
  
  @Mock
  protected HtmlResponse response;
  
  @Test(expected=IllegalArgumentException.class)
  public void testPublishWrongPath() throws Exception {
    String from = namespace("testPublishWrongPath/wrong");
    String to = namespace("testPublishWrongPath/wrongAgain");
    Repository repo = createRepository();
    SakaiDocPagePublishOperation op = createOperation();
    mockRequest(request, to);
    op.doRun(request, response, repo.loginAdministrative().getContentManager(), new LinkedList<Modification>(), from);
  }
  
  @Test(expected=IllegalArgumentException.class)
  public void testDraftDoesNotExist() throws Exception {
    String from = namespace("testDraftDoesNotExist/nonexistent/tmp_id12345");
    String to = namespace("testDraftDoesNotExist/nonexistent/id12345");
    Repository repo = createRepository();
    SakaiDocPagePublishOperation op = createOperation();
    mockRequest(request, to);
    op.doRun(request, response, repo.loginAdministrative().getContentManager(), new LinkedList<Modification>(), from);
  }
  
  @Test
  public void testPublishNoClobber() throws Exception {
    String from = namespace("testPublishNoClobber/tmp_id12345");
    String to = namespace("testPublishNoClobber/id12345");
    
    Repository repo = createRepository();
    ContentManager cm = repo.loginAdministrative().getContentManager();
    createMessageInPage(cm, from, "DraftPage", "DraftWidget", "message", "DraftContent");
    createMessageInPage(cm, to, "LivePage", "LiveWidget", "message", "LiveContent");
    
    SakaiDocPagePublishOperation op = createOperation();
    mockRequest(request, to);
    op.doRun(request, response, cm, new LinkedList<Modification>(), from);
    
    //verify the draft was deleted
    Assert.assertFalse(cm.exists(from));
    
    Content page = cm.get(to);
    Content widget = cm.get(to+"/widget");
    Content message = cm.get(to+"/widget/message");
    
    //verify everything exists
    Assert.assertNotNull(page);
    Assert.assertNotNull(widget);
    Assert.assertNotNull(message);
    
    Assert.assertEquals("DraftPage", page.getProperty("page"));
    Assert.assertEquals("DraftWidget", widget.getProperty("widget"));
    
    // though the draft message has different content, it should not clobber since it was not
    // updated
    Assert.assertEquals("LiveContent", message.getProperty("message"));
    
  }
  
  @Test
  public void testPublishWithClobber() throws Exception {
    String from = namespace("testPublishWithClobber/tmp_id12345");
    String to = namespace("testPublishWithClobber/id12345");
    
    Repository repo = createRepository();
    ContentManager cm = repo.loginAdministrative().getContentManager();
    createMessageInPage(cm, from, "DraftPage", "DraftWidget", "message", "DraftContent");
    createMessageInPage(cm, to, "LivePage", "LiveWidget", "message", "LiveContent");
    
    // touch the draft message property
    Thread.sleep(5);
    Content draftMessage = cm.get(from+"/widget/message");
    Map<String, Object> props = new HashMap<String, Object>(draftMessage.getProperties());
    props.put("message", "DraftContent1");
    cm.update(new Content(draftMessage.getPath(), props));
    
    SakaiDocPagePublishOperation op = createOperation();
    mockRequest(request, to);
    op.doRun(request, response, cm, new LinkedList<Modification>(), from);
    
    //verify the draft was deleted
    Assert.assertFalse(cm.exists(from));
    
    Content page = cm.get(to);
    Content widget = cm.get(to+"/widget");
    Content message = cm.get(to+"/widget/message");
    
    //verify everything exists
    Assert.assertNotNull(page);
    Assert.assertNotNull(widget);
    Assert.assertNotNull(message);
    
    Assert.assertEquals("DraftPage", page.getProperty("page"));
    Assert.assertEquals("DraftWidget", widget.getProperty("widget"));
    
    // though the draft message has different content, it should not clobber since it was not
    // updated
    Assert.assertEquals("DraftContent1", message.getProperty("message"));
    
  }
  
  /**
   * Verify that when there is a message that exists in the published content, but not in the
   * draft content, that the published message is not deleted.
   * 
   * @throws Exception
   */
  @Test
  public void testPublishKeepNewMessages() throws Exception {
    String from = namespace("testPublishKeepNewMessages/tmp_id12345");
    String to = namespace("testPublishKeepNewMessages/id12345");
    
    Repository repo = createRepository();
    ContentManager cm = repo.loginAdministrative().getContentManager();
    createMessageInPage(cm, from, "DraftPage", "DraftWidget", "message", "DraftContent");
    createMessageInPage(cm, to, "LivePage", "LiveWidget", "message", "LiveContent");
    
    // delete the draft message
    cm.delete(from+"/widget/message");
    
    SakaiDocPagePublishOperation op = createOperation();
    mockRequest(request, to);
    op.doRun(request, response, cm, new LinkedList<Modification>(), from);
    
    //verify the draft was deleted
    Assert.assertFalse(cm.exists(from));
    
    Content page = cm.get(to);
    Content widget = cm.get(to+"/widget");
    Content message = cm.get(to+"/widget/message");
    
    //verify everything exists
    Assert.assertNotNull(page);
    Assert.assertNotNull(widget);
    Assert.assertNotNull(message);
    
    Assert.assertEquals("DraftPage", page.getProperty("page"));
    Assert.assertEquals("DraftWidget", widget.getProperty("widget"));
    
    // though the draft message has different content, it should not clobber since it was not
    // updated
    Assert.assertEquals("LiveContent", message.getProperty("message"));
    
  }
  
  protected void createMessageInPage(ContentManager cm, String path, String pageValue,
      String widgetValue, String messagePropName, String messagePropValue) throws AccessDeniedException,
      StorageClientException {
    cm.update(new Content(path, ImmutableMap.<String, Object>of("page", pageValue)));
    cm.update(new Content(path+"/widget", ImmutableMap.<String, Object>of("widget", widgetValue)));
    cm.update(new Content(path+"/widget/message", ImmutableMap.<String, Object>of("sling:resourceType", "sakai/message", messagePropName, messagePropValue)));
  }
  
  protected void mockRequest(SlingHttpServletRequest request, String to) {
    Mockito.when(request.getParameter(":replace")).thenReturn("true");
    Mockito.when(request.getParameter(":dest")).thenReturn(to);
    Mockito.when(request.getParameter(":keepDestHistory")).thenReturn("true");
  }
  
  protected SakaiDocPagePublishOperation createOperation() {
    return new SakaiDocPagePublishOperation();
  }
  
  protected Repository createRepository() throws ClientPoolException, StorageClientException,
      AccessDeniedException, ClassNotFoundException, IOException {
    return (new BaseMemoryRepository()).getRepository();
  }
  
  protected String namespace(String path) {
    return String.format("org/sakaiproject/nakamura/resource/lite/servlet/post/operations/%s", path);
  }
}
