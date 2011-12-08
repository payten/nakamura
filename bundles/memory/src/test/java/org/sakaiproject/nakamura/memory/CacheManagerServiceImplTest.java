package org.sakaiproject.nakamura.memory;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class CacheManagerServiceImplTest {

  @Test
  public void processConfig() throws Exception {
    InputStream testConfig = new ByteArrayInputStream("${hello}, kind ${world}!".getBytes("UTF-8"));
    Map<String, Object> testProps = new HashMap<String, Object>();
    testProps.put("hello", "Howdy");
    testProps.put("world", "Globe");
    InputStream finishedConfig = new CacheManagerServiceImpl().processConfig(testConfig, testProps);
    BufferedReader reader = new BufferedReader(new InputStreamReader(finishedConfig));
    assertEquals(reader.readLine(), "Howdy, kind Globe!");
  }
}
