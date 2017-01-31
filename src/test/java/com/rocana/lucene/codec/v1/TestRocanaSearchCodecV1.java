/*
 * Copyright (c) 2017 Rocana
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.rocana.lucene.codec.v1;

import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.util.Version;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Properties;

/**
 * Unit test for {@link RocanaSearchCodecV1}.
 *
 * This test class contains additional Rocana codec tests and is not a fork of
 * any existing Lucene tests. In other words, these tests are original.
 */
public class TestRocanaSearchCodecV1 {

  /**
   * Verify the postings format is our custom class, as we expect.
   *
   * We need this since our {@link RocanaPerFieldPostingsFormat} has
   * special code that returns our real postings format even if Lucene
   * asks the Service Provider Interface (SPI) to lookup it's own
   * "Lucene50" postings format.
   */
  @Test
  public void postingsFormatReturnsOurForkOfPerFieldPostingsFormat() {
    RocanaSearchCodecV1 codec = new RocanaSearchCodecV1();

    PostingsFormat postingsFormat = codec.postingsFormat();

    Assert.assertTrue(
      "Expected an instance of " + RocanaPerFieldPostingsFormat.class.getSimpleName(),
      postingsFormat instanceof RocanaPerFieldPostingsFormat
    );
  }

  /**
   * Verify the {@link RocanaPerFieldPostingsFormat} we receive is a wrapper
   * around the real postings format.
   *
   * Our forked postings format is one of the keys to our ultimate goal.
   * Our forked postings format returns our {@link RocanaBlockTreeTermsReader}
   * whose code has been altered such that it doesn't checksum the entire file.
   */
  @Test
  public void postingsFormatWrapsOurForkOfLucenesPostingsFormat() {
    RocanaSearchCodecV1 codec = new RocanaSearchCodecV1();
    RocanaPerFieldPostingsFormat perFieldPostingsFormat = (RocanaPerFieldPostingsFormat) codec.postingsFormat();

    PostingsFormat postingsFormat = perFieldPostingsFormat.getPostingsFormatForField("some fake field");

    Assert.assertEquals(
      "Expected an instance of type " + RocanaLucene50PostingsFormat.class.getSimpleName(),
      postingsFormat.getClass(), RocanaLucene50PostingsFormat.class
    );
  }

  /**
   * The method should return the real {@link RocanaLucene50PostingsFormat}, not the
   * wrapper class {@link RocanaSearchCodecV1#postingsFormat()} returns.
   */
  @Test
  public void getActualPostingsFormatShouldReturnSameInstanceAsPostingsFormatForAField() {
    RocanaSearchCodecV1 codec = new RocanaSearchCodecV1();
    RocanaPerFieldPostingsFormat perFieldPostingsFormat = (RocanaPerFieldPostingsFormat) codec.postingsFormat();
    PostingsFormat expected = perFieldPostingsFormat.getPostingsFormatForField("some fake field");

    PostingsFormat actual = codec.getActualPostingsFormat();

    Assert.assertEquals(actual.getClass(), RocanaLucene50PostingsFormat.class);
    Assert.assertSame(expected, actual);
  }

  /**
   * Verify we get the exact same instance Lucene instantiated for SPI
   * (Service Provider Interface) lookups. Lucene only instantiates the
   * postings format once, and there's no reason we shouldn't use that
   * exact same instance.
   */
  @Test
  public void getActualPostingsFormatShouldReturnPostingsFormatFromSPI() {
    RocanaSearchCodecV1 codec = new RocanaSearchCodecV1();
    PostingsFormat expected = PostingsFormat.forName(RocanaLucene50PostingsFormat.SHORT_NAME);

    PostingsFormat actual = codec.getActualPostingsFormat();

    Assert.assertSame("Expected the exact same instance the SPI lookup returned", expected, actual);
  }

  /**
   * This test ensures we don't rename the codec class and forget to
   * update the SPI config file.
   *
   * In META-INF/services/org.apache.lucene.codecs.Codec we store
   * the fully qualified codec name. If we rename the Codec class
   * we have to update the SPI config file too. If we forget, this
   * test reminds us by failing.
   */
  @Test
  public void validateJavaServiceProviderConfigFile() throws Exception {
    File serviceProviderConfigFile = findCodecServiceProviderFile();
    Properties properties = new Properties();

    try (InputStream inputStream = new FileInputStream(serviceProviderConfigFile)) {
      properties.load(inputStream);
    }

    String expectedCodecName = RocanaSearchCodecV1.class.getName();
    Assert.assertEquals("Expected exactly 1 entry, specifying our custom codec", 1, properties.size());
    Assert.assertTrue("Expected the config file to contain the codec name.", properties.containsKey(expectedCodecName));
    Assert.assertEquals(
      "There shouldn't be a value (this isn't a real properties file, it just uses comments like properties files do and we want to ignore them)",
      "",
      properties.get(expectedCodecName)
    );
  }

  /**
   * This test reminds us to check the codec version when upgrading Lucene.
   *
   * Rationale: a failing test is better than a comment in the pom file
   * reminding us to check the codec when upgrading Lucene.
   */
  @Test
  public void luceneCodecVersionShouldMatchActualLuceneVersion() {
    String luceneCodecName = new RocanaSearchCodecV1().getWrappedCodec().getName();
    String luceneVersion = Version.LATEST.toString();

    Assert.assertTrue(
      "Reminder: check the Lucene codec version when upgrading Lucene. "
      + "Example: if upgrading to Lucene 6.0 consider changing to the Lucene60 codec. "
      + "To fix this test: change the Lucene version string (ex: 6.0.0) and change the codec name if upgrading it",
      luceneCodecName.equals("Lucene54") && luceneVersion.equals("5.5.0")
    );
  }

  /**
   * Originally we used this instead:
   *
   *   Resources.getResource("META-INF/services/org.apache.lucene.codecs.Codec");
   *
   * That would work well except for one potential problem: Lucene's
   * own jar puts a file of the same name on the classpath. That means
   * for our test to work our module would have to be earlier on the
   * classpath than Lucene. That might be totally reasonable in Maven,
   * IntelliJ, and Eclipse, but it also seems risky. Instead we'll
   * traverse the file system and find the right Codecs SPI (service
   * provider interface) file.
   */
  private File findCodecServiceProviderFile() throws URISyntaxException, IOException {

    Class<?> classFileToFind = RocanaSearchCodecV1.class;
    File classFile = findClassOnFileSystem(classFileToFind);
    int numParentDirectories = countDirectoriesInPackage(classFileToFind);
    File classpathRoot = traverseParents(classFile, numParentDirectories);

    File serviceProviderFile = new File(classpathRoot, "META-INF/services/org.apache.lucene.codecs.Codec");
    return serviceProviderFile;
  }

  private File findClassOnFileSystem(Class<?> classFileToFind) throws URISyntaxException {
    String classFileName = classFileToFind.getSimpleName() + ".class";
    URL classFileUrl = getClass().getResource(classFileName);

    return new File(classFileUrl.toURI());
  }

  private int countDirectoriesInPackage(Class<?> classFile) {
    String fullPackageName = classFile.getPackage().getName();
    String[] directoryNames = fullPackageName.split("\\.");

    return directoryNames.length;
  }

  private File traverseParents(File file, int numParents) {
    File parent = file.getParentFile();

    while (numParents > 0) {
      parent = parent.getParentFile();
      numParents--;
    }

    return parent;
  }
}
