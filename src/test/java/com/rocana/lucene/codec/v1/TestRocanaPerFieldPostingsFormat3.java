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
import org.apache.lucene.codecs.simpletext.SimpleTextPostingsFormat;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test for {@link RocanaPerFieldPostingsFormat}.
 *
 * This test class contains additional Rocana tests and is not a fork of
 * any existing Lucene tests. In other words, these tests are original.
 */
public class TestRocanaPerFieldPostingsFormat3 {

  /**
   * This is a sunny day test, ensuring the method returns the postings format it
   * was asked to return. This does not cover any special cases, like "Lucene50".
   */
  @Test
  public void lookupPostingsFormatShouldReturnTheRocanaPostingsFormat() {
    String shortName = RocanaLucene50PostingsFormat.SHORT_NAME;
    PostingsFormat expected = PostingsFormat.forName(shortName);

    PostingsFormat actual = RocanaPerFieldPostingsFormat.FieldsReader.lookupPostingsFormat(shortName);

    Assert.assertSame("Expected the same instance the SPI lookup returns", expected, actual);
    Assert.assertEquals("Expected an instance of the class we asked for",
      RocanaLucene50PostingsFormat.class, actual.getClass()
    );
  }

  /**
   * This verifies we have logic to return Rocana's custom postings format
   * whenever asked for Lucene's postings format.
   *
   * This scenario comes up as Lucene reads old index files (from data written
   * with the original Lucene54 codec, before Rocana's custom codec). When
   * Lucene reads those index files and comes across "Lucene50" specified as the
   * postings format, the code we're testing asks the SPI (Service Provider Interface)
   * for that "Lucene50" postings format, but we want it to use our custom postings
   * format for the performance benefits. So instead we return Rocana's fork of
   * the postings format. This only works because the on-disk representation of
   * both Lucene's and Rocana's postings formats are identical.
   */
  @Test
  public void lookupPostingsFormatShouldReturnTheRocanaPostingsFormtForLucene50() {
    PostingsFormat rocanaPostingsFormat = PostingsFormat.forName(RocanaLucene50PostingsFormat.SHORT_NAME);

    PostingsFormat actual = RocanaPerFieldPostingsFormat.FieldsReader.lookupPostingsFormat("Lucene50");

    Assert.assertSame("Expected the same instance the SPI lookup returns", rocanaPostingsFormat, actual);
    Assert.assertEquals("Expected an instance of the class we asked for",
      RocanaLucene50PostingsFormat.class, actual.getClass()
    );
  }

  /**
   * This is a sunny day test to ensure the method returns the postings format
   * asked for. The other tests verify how the method behaves when returning
   * {@link RocanaLucene50PostingsFormat}, which means if the method
   * always 100% of the time returned {@link RocanaLucene50PostingsFormat} those
   * tests would pass. This test ensures the method actually does return the
   * real postings format asked for, and therefore proves the method only does
   * something special when asked for "Lucene50".
   */
  @Test
  public void lookupPostingsFormatShouldReturnThePostingsFormatAskedFor() {
    String shortName = "SimpleText";
    PostingsFormat expected = PostingsFormat.forName(shortName);

    PostingsFormat actual = RocanaPerFieldPostingsFormat.FieldsReader.lookupPostingsFormat(shortName);

    Assert.assertSame("Expected the same instance the SPI lookup returns", expected, actual);
    Assert.assertEquals("Expected an instance of the class we asked for",
      SimpleTextPostingsFormat.class, actual.getClass()
    );
  }

}
