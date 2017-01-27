/*
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


import org.apache.lucene.codecs.Codec;
import org.apache.lucene.util.LuceneTestCase.SuppressSysoutChecks;

/**
 * Fork of Lucene's {@link org.apache.lucene.codecs.perfield.TestPerFieldPostingsForma}
 * from Lucene's git repository, tag: releases/lucene-solr/5.5.0
 *
 * Why we forked:
 *   - To get an extra test for our fork of Lucene's
 *     {@link org.apache.lucene.codecs.perfield.PerFieldPostingsFormat}
 *
 * What changed in the fork?
 *   - Changed the super class to Rocana's fork: {@link RocanaBasePostingsFormatTestCase}
 *   - The {@link #getCodec()} method always returns our custom codec
 *     instead of a random one (since we only want to test our codec).
 *   - Added @SuppressSysoutChecks since Lucene measures how many bytes
 *     we write to stdout and with our SLF4J logging we write too many.
 *     Apparently we can supporess this error across the whole project
 *     by applying this annotation to just one class in the project.
 *   - Removed trailing whitespace.
 *   - Changed these javadocs.
 *   - Renamed class to have 'Rocana' in the name.
 *   - Moved to a different package.
 *
 * Original Lucene documentation:
 * Basic tests of PerFieldPostingsFormat
 */
@SuppressSysoutChecks(bugUrl = "our codec logs more than Lucene expects over SLF4J")
public class TestRocanaPerFieldPostingsFormat extends RocanaBasePostingsFormatTestCase {

  @Override
  protected Codec getCodec() {
    return new RocanaSearchCodecV1();
  }

  @Override
  public void testMergeStability() throws Exception {
    assumeTrue("The MockRandom PF randomizes content on the fly, so we can't check it", false);
  }

  @Override
  public void testPostingsEnumReuse() throws Exception {
    assumeTrue("The MockRandom PF randomizes content on the fly, so we can't check it", false);
  }
}
