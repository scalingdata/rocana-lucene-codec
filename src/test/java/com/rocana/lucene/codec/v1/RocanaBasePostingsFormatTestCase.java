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
import org.apache.lucene.codecs.FieldsConsumer;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.asserting.AssertingCodec;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.BasePostingsFormatTestCase;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.index.TermsEnum.SeekStatus;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.LineFileDocs;
import org.apache.lucene.util.RamUsageTester;
import org.apache.lucene.util.TestUtil;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public abstract class RocanaBasePostingsFormatTestCase extends BasePostingsFormatTestCase {

  private static class TermFreqs {
    long totalTermFreq;
    int docFreq;
  };
  
  public void testInvertedWrite() throws Exception {
    Directory dir = newDirectory();
    MockAnalyzer analyzer = new MockAnalyzer(random());
    analyzer.setMaxTokenLength(TestUtil.nextInt(random(), 1, IndexWriter.MAX_TERM_LENGTH));
    IndexWriterConfig iwc = newIndexWriterConfig(analyzer);

    // Must be concurrent because thread(s) can be merging
    // while up to one thread flushes, and each of those
    // threads iterates over the map while the flushing
    // thread might be adding to it:
    final Map<String,TermFreqs> termFreqs = new ConcurrentHashMap<>();

    final AtomicLong sumDocFreq = new AtomicLong();
    final AtomicLong sumTotalTermFreq = new AtomicLong();

    // TODO: would be better to use / delegate to the current
    // Codec returned by getCodec()

    iwc.setCodec(new AssertingCodec() {
        @Override
        public PostingsFormat getPostingsFormatForField(String field) {

          PostingsFormat p = getCodec().postingsFormat();
          if (p instanceof PerFieldPostingsFormat) {
            p = ((PerFieldPostingsFormat) p).getPostingsFormatForField(field);
          }
          final PostingsFormat defaultPostingsFormat = p;

          final Thread mainThread = Thread.currentThread();

          if (field.equals("body")) {

            // A PF that counts up some stats and then in
            // the end we verify the stats match what the
            // final IndexReader says, just to exercise the
            // new freedom of iterating the postings more
            // than once at flush/merge:

            return new PostingsFormat(defaultPostingsFormat.getName()) {

              @Override
              public FieldsConsumer fieldsConsumer(final SegmentWriteState state) throws IOException {

                final FieldsConsumer fieldsConsumer = defaultPostingsFormat.fieldsConsumer(state);

                return new FieldsConsumer() {
                  @Override
                  public void write(Fields fields) throws IOException {
                    fieldsConsumer.write(fields);

                    boolean isMerge = state.context.context == IOContext.Context.MERGE;

                    // We only use one thread for flushing
                    // in this test:
                    assert isMerge || Thread.currentThread() == mainThread;

                    // We iterate the provided TermsEnum
                    // twice, so we excercise this new freedom
                    // with the inverted API; if
                    // addOnSecondPass is true, we add up
                    // term stats on the 2nd iteration:
                    boolean addOnSecondPass = random().nextBoolean();

                    //System.out.println("write isMerge=" + isMerge + " 2ndPass=" + addOnSecondPass);

                    // Gather our own stats:
                    Terms terms = fields.terms("body");
                    assert terms != null;

                    TermsEnum termsEnum = terms.iterator();
                    PostingsEnum docs = null;
                    while(termsEnum.next() != null) {
                      BytesRef term = termsEnum.term();
                      // TODO: also sometimes ask for payloads/offsets?
                      boolean noPositions = random().nextBoolean();
                      if (noPositions) {
                        docs = termsEnum.postings(docs, PostingsEnum.FREQS);
                      } else {
                        docs = termsEnum.postings(null, PostingsEnum.POSITIONS);
                      }
                      int docFreq = 0;
                      long totalTermFreq = 0;
                      while (docs.nextDoc() != PostingsEnum.NO_MORE_DOCS) {
                        docFreq++;
                        totalTermFreq += docs.freq();
                        int limit = TestUtil.nextInt(random(), 1, docs.freq());
                        if (!noPositions) {
                          for (int i = 0; i < limit; i++) {
                            docs.nextPosition();
                          }
                        }
                      }

                      String termString = term.utf8ToString();

                      // During merge we should only see terms
                      // we had already seen during a
                      // previous flush:
                      assertTrue(isMerge==false || termFreqs.containsKey(termString));

                      if (isMerge == false) {
                        if (addOnSecondPass == false) {
                          TermFreqs tf = termFreqs.get(termString);
                          if (tf == null) {
                            tf = new TermFreqs();
                            termFreqs.put(termString, tf);
                          }
                          tf.docFreq += docFreq;
                          tf.totalTermFreq += totalTermFreq;
                          sumDocFreq.addAndGet(docFreq);
                          sumTotalTermFreq.addAndGet(totalTermFreq);
                        } else if (termFreqs.containsKey(termString) == false) {
                          // Add placeholder (2nd pass will
                          // set its counts):
                          termFreqs.put(termString, new TermFreqs());
                        }
                      }
                    }

                    // Also test seeking the TermsEnum:
                    for(String term : termFreqs.keySet()) {
                      if (termsEnum.seekExact(new BytesRef(term))) {
                        // TODO: also sometimes ask for payloads/offsets?
                        boolean noPositions = random().nextBoolean();
                        if (noPositions) {
                          docs = termsEnum.postings(docs, PostingsEnum.FREQS);
                        } else {
                          docs = termsEnum.postings(null, PostingsEnum.POSITIONS);
                        }

                        int docFreq = 0;
                        long totalTermFreq = 0;
                        while (docs.nextDoc() != PostingsEnum.NO_MORE_DOCS) {
                          docFreq++;
                          totalTermFreq += docs.freq();
                          int limit = TestUtil.nextInt(random(), 1, docs.freq());
                          if (!noPositions) {
                            for (int i = 0; i < limit; i++) {
                              docs.nextPosition();
                            }
                          }
                        }

                        if (isMerge == false && addOnSecondPass) {
                          TermFreqs tf = termFreqs.get(term);
                          assert tf != null;
                          tf.docFreq += docFreq;
                          tf.totalTermFreq += totalTermFreq;
                          sumDocFreq.addAndGet(docFreq);
                          sumTotalTermFreq.addAndGet(totalTermFreq);
                        }

                        //System.out.println("  term=" + term + " docFreq=" + docFreq + " ttDF=" + termToDocFreq.get(term));
                        assertTrue(docFreq <= termFreqs.get(term).docFreq);
                        assertTrue(totalTermFreq <= termFreqs.get(term).totalTermFreq);
                      }
                    }

                    // Also test seekCeil
                    for(int iter=0;iter<10;iter++) {
                      BytesRef term = new BytesRef(TestUtil.randomRealisticUnicodeString(random()));
                      SeekStatus status = termsEnum.seekCeil(term);
                      if (status == SeekStatus.NOT_FOUND) {
                        assertTrue(term.compareTo(termsEnum.term()) < 0);
                      }
                    }
                  }

                  @Override
                  public void close() throws IOException {
                    fieldsConsumer.close();
                  }
                };
              }

              @Override
              public FieldsProducer fieldsProducer(SegmentReadState state) throws IOException {
                return defaultPostingsFormat.fieldsProducer(state);
              }
            };
          } else {
            return defaultPostingsFormat;
          }
        }
      });

    RandomIndexWriter w = new RandomIndexWriter(random(), dir, iwc);

    LineFileDocs docs = new LineFileDocs(random());
    int bytesToIndex = atLeast(100) * 1024;
    int bytesIndexed = 0;
    while (bytesIndexed < bytesToIndex) {
      Document doc = docs.nextDoc();
      w.addDocument(doc);
      bytesIndexed += RamUsageTester.sizeOf(doc);
    }

    IndexReader r = w.getReader();
    w.close();

    Terms terms = MultiFields.getTerms(r, "body");
    assertEquals(sumDocFreq.get(), terms.getSumDocFreq());
    assertEquals(sumTotalTermFreq.get(), terms.getSumTotalTermFreq());

    TermsEnum termsEnum = terms.iterator();
    long termCount = 0;
    boolean supportsOrds = true;
    while(termsEnum.next() != null) {
      BytesRef term = termsEnum.term();
      assertEquals(termFreqs.get(term.utf8ToString()).docFreq, termsEnum.docFreq());
      assertEquals(termFreqs.get(term.utf8ToString()).totalTermFreq, termsEnum.totalTermFreq());
      if (supportsOrds) {
        long ord;
        try {
          ord = termsEnum.ord();
        } catch (UnsupportedOperationException uoe) {
          supportsOrds = false;
          ord = -1;
        }
        if (ord != -1) {
          assertEquals(termCount, ord);
        }
      }
      termCount++;
    }
    assertEquals(termFreqs.size(), termCount);

    r.close();
    dir.close();
  }

}
