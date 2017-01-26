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
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.lucene54.Lucene54Codec;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.lucene.index.SegmentReadState;
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
 *
 * The idea behind this:
 * 
 * Ultimately we're trying to comment out one line of code in
 * {@link RocanaBlockTreeTermsReader}'s constructor.
 * 
 * We don't want to customize Lucene's on-disk format. We just want
 * to stop Lucene from checksumming an entire file when there's no
 * benefit. The Lucene checksum is redundant since we store indexes
 * on HDFS, which also checksums. The Lucene checksum happens at an
 * inopportune time as we're trying to open a Rocana Search Searcher,
 * which may happen during a rebalance, and we'd rather eliminate
 * that penalty altogether or at least delay it until the rebalance
 * finishes.
 * 
 * To accomplish that we register this class as our Lucene codec,
 * which happens in rocana-search, when it calls:
 * {@link org.apache.lucene.index.IndexWriterConfig#setCodec(Codec)}.
 *
 * We also gave this codec a unique 'short name', which gets written
 * to Lucene's segment info files. When reading those files Lucene
 * looks up our codec using Java's Service Provider Interface (SPI).
 * See {@link #NAME}.
 *
 * This custom codec wraps Lucene's codec: {@link Lucene54Codec}. It
 * does that specifically to return our custom postings format:
 * {@link RocanaLucene50PostingsFormat}, which is a fork of
 * Lucene's postings format. We don't want to change Lucene's
 * postings format, just prevent the checksum, so
 * {@link RocanaLucene50PostingsFormat#fieldsProducer(SegmentReadState)}
 * returns another forked class: {@link RocanaBlockTreeTermsReader},
 * which has the commented out line of code so we don't checksum
 * the entire file.
 *
 * Or more succinctly:
 *   - this custom codec returns:
 *   - our forked postings format, which returns:
 *   - our forked block tree terms reader, which comments out the call to:
 *   - {@link CodecUtil#checksumEntireFile(org.apache.lucene.store.IndexInput)}
 *     which speeds up Searcher opens.
 *
 * The classes of interest, in execution order:
 *  - {@link RocanaSearchCodecV1}
 *  - {@link RocanaLucene50PostingsFormat}
 *  - {@link RocanaBlockTreeTermsReader}
 *
 * Other forked classes were only forked to make the code compile.
 */
public class RocanaSearchCodecV1 extends FilterCodec {

  public static final String NAME = RocanaSearchCodecV1.class.getSimpleName();
  private static final Logger logger = LoggerFactory.getLogger(RocanaSearchCodecV1.class);

  private final PerFieldPostingsFormat postingsFormat;

  public RocanaSearchCodecV1() {
    super(NAME, new Lucene54Codec());
    logger.debug("Instantiated custom codec: {} which wraps codec: {}", getClass(), getWrappedCodec().getClass());

    final RocanaLucene50PostingsFormat forkedPostingsFormat = new RocanaLucene50PostingsFormat();

    postingsFormat = new PerFieldPostingsFormat() {
      @Override
      public PostingsFormat getPostingsFormatForField(String field) {
        return forkedPostingsFormat;
      }
    };
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

  /**
   * Ultimately we want Lucene to use our forked
   * {@link RocanaBlockTreeTermsReader}, which comments out
   * the code that checksums the entire file. To do that we
   * have to return a custom postings format here, which
   * acts exactly like Lucene's postings format except it
   * returns {@link RocanaBlockTreeTermsReader}. 
   */
  @Override
  public PostingsFormat postingsFormat() {
    return postingsFormat;
  }

}
