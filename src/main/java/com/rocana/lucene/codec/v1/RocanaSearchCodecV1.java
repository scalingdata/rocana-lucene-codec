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

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.lucene54.Lucene54Codec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom codec that speeds up opening Searchers.
 *
 * We don't need Lucene's entire-file checksum since HDFS already
 * uses checksums for HDFS blocks. Eliminating Lucene's checksum for
 * the entire file improves Searcher open performance.
 *
 * If you have access to Rocana's JIRA, see ROCANA-8229.
 */
public class RocanaSearchCodecV1 extends FilterCodec {

  public static final String NAME = RocanaSearchCodecV1.class.getSimpleName();
  private static final Logger logger = LoggerFactory.getLogger(RocanaSearchCodecV1.class);

  public RocanaSearchCodecV1() {
    super(NAME, new Lucene54Codec());
    logger.debug("Instantiated custom codec: {} which wraps codec: {}", getClass(), getWrappedCodec().getClass());
  }

  /**
   * Get the codec we wrap, meaning, the one
   * we pass to the super class for delegating
   * method calls too.
   *
   * Visible only for testing.
   */
  Codec getWrappedCodec() {
    return delegate;
  }

}
