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


import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Locale;

import org.apache.lucene.codecs.PostingsReaderBase;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;

/**
 * Fork of Lucene's {@link org.apache.lucene.codecs.blocktree.Stats}
 * from Lucene's git repository, tag: releases/lucene-solr/5.5.0
 *
 * Why we forked:
 *   - To use the other forked classes, like {@link RocanaSegmentTermsEnumFrame}.
 *
 * What changed in the fork?
 *   - Use the other forked classes.
 *   - Removed trailing whitespace.
 *   - Changed these javadocs.
 *
 * This is one of the forked classes where no logic changed, but to get
 * the fork to compile we had to fork this class too. That happened with
 * several classes because they had a hard reference to another class we
 * forked. Ideally, our forked classes would extend the original Lucene
 * class and override just the methods we need to change.
 *
 * Unfortunately in this particular case, the methods we need to customize
 * reference a private field and, as this class might be on a critical
 * performance path, we don't want to pay the performance price of using
 * reflection to access that private field.
 *
 * To see a full diff of changes in our fork: compare this version to the very first
 * commit in git history. That first commit is the exact file from Lucene with no
 * modifications.
 *
 * @see RocanaSearchCodecV1
 *
 * Original Lucene documentation:
 * BlockTree statistics for a single field
 * returned by {@link FieldReader#getStats()}.
 * @lucene.internal
 */
public class RocanaStats {
  /** Byte size of the index. */
  public long indexNumBytes;

  /** Total number of terms in the field. */
  public long totalTermCount;

  /** Total number of bytes (sum of term lengths) across all terms in the field. */
  public long totalTermBytes;

  // TODO: add total auto-prefix term count

  /** The number of normal (non-floor) blocks in the terms file. */
  public int nonFloorBlockCount;

  /** The number of floor blocks (meta-blocks larger than the
   *  allowed {@code maxItemsPerBlock}) in the terms file. */
  public int floorBlockCount;

  /** The number of sub-blocks within the floor blocks. */
  public int floorSubBlockCount;

  /** The number of "internal" blocks (that have both
   *  terms and sub-blocks). */
  public int mixedBlockCount;

  /** The number of "leaf" blocks (blocks that have only
   *  terms). */
  public int termsOnlyBlockCount;

  /** The number of "internal" blocks that do not contain
   *  terms (have only sub-blocks). */
  public int subBlocksOnlyBlockCount;

  /** Total number of blocks. */
  public int totalBlockCount;

  /** Number of blocks at each prefix depth. */
  public int[] blockCountByPrefixLen = new int[10];
  private int startBlockCount;
  private int endBlockCount;

  /** Total number of bytes used to store term suffixes. */
  public long totalBlockSuffixBytes;

  /** Total number of bytes used to store term stats (not
   *  including what the {@link PostingsReaderBase}
   *  stores. */
  public long totalBlockStatsBytes;

  /** Total bytes stored by the {@link PostingsReaderBase},
   *  plus the other few vInts stored in the frame. */
  public long totalBlockOtherBytes;

  /** Segment name. */
  public final String segment;

  /** Field name. */
  public final String field;

  RocanaStats(String segment, String field) {
    this.segment = segment;
    this.field = field;
  }

  void startBlock(RocanaSegmentTermsEnumFrame frame, boolean isFloor) {
    totalBlockCount++;
    if (isFloor) {
      if (frame.fp == frame.fpOrig) {
        floorBlockCount++;
      }
      floorSubBlockCount++;
    } else {
      nonFloorBlockCount++;
    }

    if (blockCountByPrefixLen.length <= frame.prefix) {
      blockCountByPrefixLen = ArrayUtil.grow(blockCountByPrefixLen, 1+frame.prefix);
    }
    blockCountByPrefixLen[frame.prefix]++;
    startBlockCount++;
    totalBlockSuffixBytes += frame.suffixesReader.length();
    totalBlockStatsBytes += frame.statsReader.length();
  }

  void endBlock(RocanaSegmentTermsEnumFrame frame) {
    final int termCount = frame.isLeafBlock ? frame.entCount : frame.state.termBlockOrd;
    final int subBlockCount = frame.entCount - termCount;
    totalTermCount += termCount;
    if (termCount != 0 && subBlockCount != 0) {
      mixedBlockCount++;
    } else if (termCount != 0) {
      termsOnlyBlockCount++;
    } else if (subBlockCount != 0) {
      subBlocksOnlyBlockCount++;
    } else {
      throw new IllegalStateException();
    }
    endBlockCount++;
    final long otherBytes = frame.fpEnd - frame.fp - frame.suffixesReader.length() - frame.statsReader.length();
    assert otherBytes > 0 : "otherBytes=" + otherBytes + " frame.fp=" + frame.fp + " frame.fpEnd=" + frame.fpEnd;
    totalBlockOtherBytes += otherBytes;
  }

  void term(BytesRef term) {
    totalTermBytes += term.length;
  }

  void finish() {
    assert startBlockCount == endBlockCount: "startBlockCount=" + startBlockCount + " endBlockCount=" + endBlockCount;
    assert totalBlockCount == floorSubBlockCount + nonFloorBlockCount: "floorSubBlockCount=" + floorSubBlockCount + " nonFloorBlockCount=" + nonFloorBlockCount + " totalBlockCount=" + totalBlockCount;
    assert totalBlockCount == mixedBlockCount + termsOnlyBlockCount + subBlocksOnlyBlockCount: "totalBlockCount=" + totalBlockCount + " mixedBlockCount=" + mixedBlockCount + " subBlocksOnlyBlockCount=" + subBlocksOnlyBlockCount + " termsOnlyBlockCount=" + termsOnlyBlockCount;
  }

  @Override
  public String toString() {
    final ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
    PrintStream out;
    try {
      out = new PrintStream(bos, false, IOUtils.UTF_8);
    } catch (UnsupportedEncodingException bogus) {
      throw new RuntimeException(bogus);
    }

    out.println("  index FST:");
    out.println("    " + indexNumBytes + " bytes");
    out.println("  terms:");
    out.println("    " + totalTermCount + " terms");
    out.println("    " + totalTermBytes + " bytes" + (totalTermCount != 0 ? " (" + String.format(Locale.ROOT, "%.1f", ((double) totalTermBytes)/totalTermCount) + " bytes/term)" : ""));
    out.println("  blocks:");
    out.println("    " + totalBlockCount + " blocks");
    out.println("    " + termsOnlyBlockCount + " terms-only blocks");
    out.println("    " + subBlocksOnlyBlockCount + " sub-block-only blocks");
    out.println("    " + mixedBlockCount + " mixed blocks");
    out.println("    " + floorBlockCount + " floor blocks");
    out.println("    " + (totalBlockCount-floorSubBlockCount) + " non-floor blocks");
    out.println("    " + floorSubBlockCount + " floor sub-blocks");
    out.println("    " + totalBlockSuffixBytes + " term suffix bytes" + (totalBlockCount != 0 ? " (" + String.format(Locale.ROOT, "%.1f", ((double) totalBlockSuffixBytes)/totalBlockCount) + " suffix-bytes/block)" : ""));
    out.println("    " + totalBlockStatsBytes + " term stats bytes" + (totalBlockCount != 0 ? " (" + String.format(Locale.ROOT, "%.1f", ((double) totalBlockStatsBytes)/totalBlockCount) + " stats-bytes/block)" : ""));
    out.println("    " + totalBlockOtherBytes + " other bytes" + (totalBlockCount != 0 ? " (" + String.format(Locale.ROOT, "%.1f", ((double) totalBlockOtherBytes)/totalBlockCount) + " other-bytes/block)" : ""));
    if (totalBlockCount != 0) {
      out.println("    by prefix length:");
      int total = 0;
      for(int prefix=0;prefix<blockCountByPrefixLen.length;prefix++) {
        final int blockCount = blockCountByPrefixLen[prefix];
        total += blockCount;
        if (blockCount != 0) {
          out.println("      " + String.format(Locale.ROOT, "%2d", prefix) + ": " + blockCount);
        }
      }
      assert totalBlockCount == total;
    }

    try {
      return bos.toString(IOUtils.UTF_8);
    } catch (UnsupportedEncodingException bogus) {
      throw new RuntimeException(bogus);
    }
  }
}
