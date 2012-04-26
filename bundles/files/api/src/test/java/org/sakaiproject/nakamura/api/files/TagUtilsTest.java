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
package org.sakaiproject.nakamura.api.files;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;
import static org.apache.sling.jcr.resource.JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY;
import static org.mockito.Mockito.when;
import static org.sakaiproject.nakamura.api.files.FilesConstants.RT_SAKAI_TAG;
import static org.sakaiproject.nakamura.api.files.FilesConstants.SAKAI_TAG_NAME;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.sling.commons.osgi.PropertiesUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class TagUtilsTest {
  @Mock
  private ContentManager cm;
  
  private Content c;
  
  private Content tagsNode;
  private Content aTagNode;
  private Content dummyTagNode;
  private Content otherTagNode;
  private Content lastTagNode;

  @Before
  public void setUp() throws Exception {
    c = new Content("/some/content", null);

    tagsNode = new Content("/tags", null);
    aTagNode = createTagNode("/tags/atag", "A Tag", null);
    dummyTagNode = createTagNode("/tags/atag/dummy", "Dummy Tag", null);
    otherTagNode = createTagNode("/tags/atag/othertag", "Other Tag", null);
    lastTagNode = createTagNode("/tags/atag/othertag/lasttag", "Last Tag", null);

    when(cm.get(tagsNode.getPath())).thenReturn(tagsNode);
    when(cm.get(aTagNode.getPath())).thenReturn(aTagNode);
    when(cm.get(otherTagNode.getPath())).thenReturn(otherTagNode);
    when(cm.get(lastTagNode.getPath())).thenReturn(lastTagNode);

    when(cm.listChildren(tagsNode.getPath())).thenAnswer(new Answer<Iterator<Content>>() {
      @Override
      public Iterator<Content> answer(InvocationOnMock invocation) throws Throwable {
        return Lists.newArrayList(aTagNode).iterator();
      }
    });
    when(cm.listChildren(aTagNode.getPath())).thenAnswer(new Answer<Iterator<Content>>() {
      @Override
      public Iterator<Content> answer(InvocationOnMock invocation) throws Throwable {
        return Lists.newArrayList(dummyTagNode, otherTagNode).iterator();
      }
    });
    when(cm.listChildren(otherTagNode.getPath())).thenAnswer(new Answer<Iterator<Content>>() {
      @Override
      public Iterator<Content> answer(InvocationOnMock invocation) throws Throwable {
        return Lists.newArrayList(lastTagNode).iterator();
      }
    });
    when(cm.listChildren(dummyTagNode.getPath())).thenAnswer(new Answer<Iterator<Content>>() {
      @Override
      public Iterator<Content> answer(InvocationOnMock invocation) throws Throwable {
        return Iterators.<Content> emptyIterator();
      }
    });
    when(cm.listChildren(lastTagNode.getPath())).thenAnswer(new Answer<Iterator<Content>>() {
      @Override
      public Iterator<Content> answer(InvocationOnMock invocation) throws Throwable {
        return Iterators.<Content> emptyIterator();
      }
    });
  }

  @Test
  public void testIsTag() {
    assertFalse(TagUtils.isTag(null));
    assertTrue(TagUtils.isTag(aTagNode));
    assertFalse(TagUtils.isTag(c));
  }

  @Test
  public void testAddTags() throws Exception {
    try {
      TagUtils.addTags(null, null, null);
      fail("Null content manager or content should throw an exception.");
    } catch (Exception e) {
      // expected
    }
    List<Content> tagNodes = Lists.newArrayList(aTagNode);
    List<Content> tags = TagUtils.addTags(cm, c, tagNodes);
    assertEquals(1, tags.size());
  }

  @Test
  public void testAddNullTags() throws Exception {
    List<Content> tags = TagUtils.addTags(cm, c, null);
    assertEquals(0, tags.size());
  }

  @Test
  public void testDeleteTag() throws Exception {
    try {
      TagUtils.deleteTag(null, null, null);
      fail("Null content manager or content should throw an exception.");
    } catch (Exception e) {
      // expected
    }

    assertFalse(TagUtils.deleteTag(cm, c, "random"));

    List<Content> tagNodes = Lists.newArrayList(aTagNode);
    TagUtils.addTags(cm, c, tagNodes);
    assertTrue(TagUtils.deleteTag(cm, c, "A Tag"));
  }

  @Test
  public void testDeleteBlankTag() throws Exception {
    assertFalse(TagUtils.deleteTag(cm, c, null));
    assertFalse(TagUtils.deleteTag(cm, c, ""));
    assertFalse(TagUtils.deleteTag(cm, c, "  "));
  }

  @Test
  public void testShallowAncestorTags() throws Exception {
    try {
      TagUtils.ancestorTags(null, null);
      fail("Null content manager or content should throw an exception");
    } catch (Exception e) {
      // expected
    }
    
    // test top level tag
    Collection<String> ancestors = TagUtils.ancestorTags(aTagNode, cm);
    assertEquals(0, ancestors.size());
  }

  @Test
  public void testNestedAncestorTags() throws Exception {
    // test tag with parents
    Collection<String> ancestors = TagUtils.ancestorTags(lastTagNode, cm);
    assertEquals(2, ancestors.size());
    
    Iterator<String> ancs = ancestors.iterator();
    assertEquals("Other Tag", ancs.next());
    assertEquals("A Tag", ancs.next());
  }

  @Test
  public void testIsChildOfRoot() {
    try {
      TagUtils.isChildOfRoot(null);
      fail("Null node should throw exception.");
    } catch (Exception e) {
      // expected
    }
    assertFalse(TagUtils.isChildOfRoot(c));
    assertTrue(TagUtils.isChildOfRoot(new Content("", null)));
    assertTrue(TagUtils.isChildOfRoot(new Content("/", null)));
  }

  @Test
  public void testAlreadyTaggedBelowThisLevel() throws Exception {
    try {
      TagUtils.alreadyTaggedBelowThisLevel(null, null, null);
      fail("Null content manager or content should throw an exception");
    } catch (Exception e) {
      // expected
    }
    assertFalse(TagUtils.alreadyTaggedBelowThisLevel(c, null, cm));

    assertFalse(TagUtils.alreadyTaggedBelowThisLevel(otherTagNode,
        new String[] { "Bad Tag" }, cm));
    assertTrue(TagUtils.alreadyTaggedBelowThisLevel(otherTagNode,
        new String[] { "Last Tag" }, cm));
    assertTrue(TagUtils.alreadyTaggedBelowThisLevel(aTagNode,
        new String[] { "Last Tag" }, cm));
  }

  @Test
  public void testAlreadyTaggedAtOrAboveThisLevel() throws Exception {
    assertFalse(TagUtils.alreadyTaggedAtOrAboveThisLevel(null, null));

    assertFalse(TagUtils.alreadyTaggedAtOrAboveThisLevel(new String[] { "Last Tag" },
        Lists.newArrayList("This Tag", "That Tag")));
    assertTrue(TagUtils.alreadyTaggedAtOrAboveThisLevel(new String[] { "Last Tag" },
        Lists.newArrayList("This Tag", "Last Tag")));
  }

  @Test
  public void testIncrementTagCounts() throws Exception {
    try {
      TagUtils.bumpTagCounts(null, null, false, false, null);
      fail("Null content manager or content should throw an exception");
    } catch (Exception e) {
      // expected
    }

    TagUtils.bumpTagCounts(lastTagNode, new String[] { "Last Tag", "Other Tag" }, true,
        false, cm);
    TagUtils.bumpTagCounts(lastTagNode, new String[] { "Last Tag", "Other Tag" }, true,
        false, cm);
    TagUtils.bumpTagCounts(otherTagNode, new String[] { "Last Tag", "Other Tag" }, true,
        false, cm);
    assertEquals(2, PropertiesUtil.toInteger(
        lastTagNode.getProperty(FilesConstants.SAKAI_TAG_COUNT), 0));
    assertEquals("Expect 0 since 'Last Tag' is already tagged (short circuit before 'Other Tag'.",
        0, PropertiesUtil.toInteger(otherTagNode.getProperty(FilesConstants.SAKAI_TAG_COUNT), 0));

    TagUtils.bumpTagCounts(otherTagNode, new String[] { "Other Tag" }, true,
        false, cm);
    assertEquals(1, PropertiesUtil.toInteger(
        otherTagNode.getProperty(FilesConstants.SAKAI_TAG_COUNT), 0));
  }

  @Test
  public void testDecrementTagCounts() throws Exception {
    TagUtils.bumpTagCounts(lastTagNode, new String[] { "Last Tag", "Other Tag" }, true,
        false, cm);
    TagUtils.bumpTagCounts(lastTagNode, new String[] { "Last Tag", "Other Tag" }, false,
        false, cm);
    assertEquals(0, PropertiesUtil.toInteger(
        lastTagNode.getProperty(FilesConstants.SAKAI_TAG_COUNT), 0));
    
  }

  private Content createTagNode(String path, String name, Map<String, Object> props) {
    Map<String, Object> properties = Maps.newHashMap();
    if (props != null) {
      properties.putAll(props);
    }
    properties.put(SLING_RESOURCE_TYPE_PROPERTY, RT_SAKAI_TAG);
    properties.put(SAKAI_TAG_NAME, name);
    Content c = new Content(path, properties);
    return c;
  }
}
