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
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test for {@link RocanaLucene50PostingsFormat}.
 *
 * This test class contains additional Rocana codec tests and is not a fork of
 * any existing Lucene tests. In other words, these tests are original.
 */
public class TestRocanaLucene50PostingsFormat {

  /**
   * We don't want to use Lucene's own short name in the Lucene index files.
   * Instead we'll set a Rocana-specific short name.
   *
   * If we didn't do this Lucene would put its postings format name ("Lucene50")
   * in the Lucene index files, then, when reading those index files, Lucene
   * may go searching for the Lucene50 postings format. Instead we want Lucene
   * using our postings format, and the best way to do that is to use our own
   * short name and register our postings format in the SPI file:
   * META-INF/services/org.apache.lucene.codecs.PostingsFormat
   */
  @Test
  public void shortNameIsNotLucene50() {
    Assert.assertNotEquals(
      "Rocana's postings format short name cannot be 'Lucene50' as Lucene would load its own postings format when it sees 'Lucene50' in the index files",
      "Lucene50",
      RocanaLucene50PostingsFormat.SHORT_NAME
    );
  }

  /**
   * The constructor should set the name to Rocana's postings format name.
   */
  @Test
  public void constructorShouldSetRocanaSpecificShortName() {
    RocanaLucene50PostingsFormat postingsFormat = new RocanaLucene50PostingsFormat();

    Assert.assertEquals(
      "Expected Rocana's postings format short name",
      RocanaLucene50PostingsFormat.SHORT_NAME,
      postingsFormat.getName()
    );
  }

  /**
   * This test verifies we created the SPI lookup file and registered our postings
   * format in it.
   *
   * If we didn't do this Lucene would put its postings format name ("Lucene50")
   * in the Lucene index files, then, when reading those index files, Lucene
   * may go searching for the Lucene50 postings format. Instead we want Lucene
   * using our postings format, and the best way to do that is to use our own
   * short name and register our postings format in the SPI file:
   * META-INF/services/org.apache.lucene.codecs.PostingsFormat
   */
  @Test
  public void classShouldBeRegisteredForSPILookups() {
    PostingsFormat postingsFormat = PostingsFormat.forName(RocanaLucene50PostingsFormat.SHORT_NAME);

    Assert.assertEquals(
      "Expected a non-null instance of our postings format",
      RocanaLucene50PostingsFormat.class,
      postingsFormat.getClass()
    );
  }

}
