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


import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.blocktree.FieldReader;
import org.apache.lucene.codecs.blocktree.Stats;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.TestUtil;

/**
 * Fork of Lucene's {@link org.apache.lucene.codecs.lucene50.TestBlockPostingsFormat}.
 * from Lucene's git repository, tag: releases/lucene-solr/5.5.0
 *
 * Why we forked:
 *   - To use existing Lucene tests to test our {@link RocanaLucene50PostingsFormat}
 *
 * What changed in the fork?
 *   - Use {@link RocanaLucene50PostingsFormat} instead of Lucene's original.
 *   - Removed trailing whitespace.
 *   - Changed these javadocs.
 *   - Moved to a different package.
 *
 * To see a full diff of changes in our fork: compare this version to the very first
 * commit in git history. That first commit is the exact file from Lucene with no
 * modifications.
 *
 * @see RocanaSearchCodecV1
 *
 * Original Lucene documentation:
 * Tests BlockPostingsFormat
 */
public class TestBlockPostingsFormat extends RocanaBasePostingsFormatTestCase {
  private final Codec codec = TestUtil.alwaysPostingsFormat(new RocanaLucene50PostingsFormat());

  @Override
  protected Codec getCodec() {
    return codec;
  }

  /** Make sure the final sub-block(s) are not skipped. */
  public void testFinalBlock() throws Exception {
    Directory d = newDirectory();
    IndexWriter w = new IndexWriter(d, new IndexWriterConfig(new MockAnalyzer(random())));
    for(int i=0;i<25;i++) {
      Document doc = new Document();
      doc.add(newStringField("field", Character.toString((char) (97+i)), Field.Store.NO));
      doc.add(newStringField("field", "z" + Character.toString((char) (97+i)), Field.Store.NO));
      w.addDocument(doc);
    }
    w.forceMerge(1);

    DirectoryReader r = DirectoryReader.open(w);
    assertEquals(1, r.leaves().size());
    FieldReader field = (FieldReader) r.leaves().get(0).reader().fields().terms("field");
    // We should see exactly two blocks: one root block (prefix empty string) and one block for z* terms (prefix z):
    Stats stats = field.getStats();
    assertEquals(0, stats.floorBlockCount);
    assertEquals(2, stats.nonFloorBlockCount);
    r.close();
    w.close();
    d.close();
  }

  private void shouldFail(int minItemsInBlock, int maxItemsInBlock) {
    try {
      new RocanaLucene50PostingsFormat(minItemsInBlock, maxItemsInBlock);
      fail("did not hit exception");
    } catch (IllegalArgumentException iae) {
      // expected
    }
  }

  public void testInvalidBlockSizes() throws Exception {
    shouldFail(0, 0);
    shouldFail(10, 8);
    shouldFail(-1, 10);
    shouldFail(10, -1);
    shouldFail(10, 12);
  }
}
