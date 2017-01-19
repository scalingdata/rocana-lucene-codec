rocana-lucene-codec
===================
This is Rocana's custom Lucene codec. The initial motivation behind it is to speed up Searcher opens. Opening a Rocana Search searcher causes Lucene to do a full file checksum, but we don't need that checksum since HDFS itself checksums blocks. Therefore we don't want to pay the performance penalty.

If you have access to Rocana's JIRA, see [ROCANA-8229](https://scalingdata.atlassian.net/browse/ROCANA-8229).