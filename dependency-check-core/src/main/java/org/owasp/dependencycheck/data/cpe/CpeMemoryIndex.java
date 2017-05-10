/*
 * This file is part of dependency-check-core.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) 2013 Jeremy Long. All Rights Reserved.
 */
package org.owasp.dependencycheck.data.cpe;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.RAMDirectory;
import org.owasp.dependencycheck.data.lucene.LuceneUtils;
import org.owasp.dependencycheck.data.lucene.SearchFieldAnalyzer;
import org.owasp.dependencycheck.data.nvdcve.CveDB;
import org.owasp.dependencycheck.data.nvdcve.DatabaseException;
import org.owasp.dependencycheck.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An in memory lucene index that contains the vendor/product combinations from
 * the CPE (application) identifiers within the NVD CVE data.
 *
 * @author Jeremy Long
 */
public final class CpeMemoryIndex {

    /**
     * The logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(CpeMemoryIndex.class);
    /**
     * singleton instance.
     */
    private static final CpeMemoryIndex INSTANCE = new CpeMemoryIndex();
    /**
     * The in memory Lucene index.
     */
    private RAMDirectory index;
    /**
     * The Lucene IndexReader.
     */
    private IndexReader indexReader;
    /**
     * The Lucene IndexSearcher.
     */
    private IndexSearcher indexSearcher;
    /**
     * The Lucene Analyzer used for Searching.
     */
    private Analyzer searchingAnalyzer;
    /**
     * The Lucene QueryParser used for Searching.
     */
    private QueryParser queryParser;
    /**
     * The search field analyzer for the product field.
     */
    private SearchFieldAnalyzer productFieldAnalyzer;
    /**
     * The search field analyzer for the vendor field.
     */
    private SearchFieldAnalyzer vendorFieldAnalyzer;
    /**
     * A flag indicating whether or not the index is open.
     */
    private boolean openState = false;

    /**
     * private constructor for singleton.
     */
    private CpeMemoryIndex() {
    }

    /**
     * Gets the singleton instance of the CpeMemoryIndex.
     *
     * @return the instance of the CpeMemoryIndex
     */
    public static CpeMemoryIndex getInstance() {
        return INSTANCE;
    }

    /**
     * Creates and loads data into an in memory index.
     *
     * @param cve the data source to retrieve the cpe data
     * @throws IndexException thrown if there is an error creating the index
     */
    public void open(CveDB cve) throws IndexException {
        synchronized (INSTANCE) {
            if (!openState) {
                index = new RAMDirectory();
                buildIndex(cve);
                try {
                    indexReader = DirectoryReader.open(index);
                } catch (IOException ex) {
                    throw new IndexException(ex);
                }
                indexSearcher = new IndexSearcher(indexReader);
                searchingAnalyzer = createSearchingAnalyzer();
                queryParser = new QueryParser(LuceneUtils.CURRENT_VERSION, Fields.DOCUMENT_KEY, searchingAnalyzer);
                openState = true;
            }
        }
    }

    /**
     * returns whether or not the index is open.
     *
     * @return whether or not the index is open
     */
    public boolean isOpen() {
        return openState;
    }

    /**
     * Creates an Analyzer for searching the CPE Index.
     *
     * @return the CPE Analyzer.
     */
    private Analyzer createSearchingAnalyzer() {
        final Map<String, Analyzer> fieldAnalyzers = new HashMap<>();
        fieldAnalyzers.put(Fields.DOCUMENT_KEY, new KeywordAnalyzer());
        productFieldAnalyzer = new SearchFieldAnalyzer(LuceneUtils.CURRENT_VERSION);
        vendorFieldAnalyzer = new SearchFieldAnalyzer(LuceneUtils.CURRENT_VERSION);
        fieldAnalyzers.put(Fields.PRODUCT, productFieldAnalyzer);
        fieldAnalyzers.put(Fields.VENDOR, vendorFieldAnalyzer);

        return new PerFieldAnalyzerWrapper(new KeywordAnalyzer(), fieldAnalyzers);
    }

    /**
     * Closes the CPE Index.
     */
    public void close() {
        if (searchingAnalyzer != null) {
            searchingAnalyzer.close();
            searchingAnalyzer = null;
        }
        if (indexReader != null) {
            try {
                indexReader.close();
            } catch (IOException ex) {
                LOGGER.trace("", ex);
            }
            indexReader = null;
        }
        queryParser = null;
        indexSearcher = null;
        if (index != null) {
            index.close();
            index = null;
        }
        openState = false;
    }

    /**
     * Builds the CPE Lucene Index based off of the data within the CveDB.
     *
     * @param cve the data base containing the CPE data
     * @throws IndexException thrown if there is an issue creating the index
     */
    private void buildIndex(CveDB cve) throws IndexException {
        try (Analyzer analyzer = createSearchingAnalyzer();
                IndexWriter indexWriter = new IndexWriter(index, new IndexWriterConfig(LuceneUtils.CURRENT_VERSION, analyzer))) {
            // Tip: reuse the Document and Fields for performance...
            // See "Re-use Document and Field instances" from
            // http://wiki.apache.org/lucene-java/ImproveIndexingSpeed
            final Document doc = new Document();
            final Field v = new TextField(Fields.VENDOR, Fields.VENDOR, Field.Store.YES);
            final Field p = new TextField(Fields.PRODUCT, Fields.PRODUCT, Field.Store.YES);
            doc.add(v);
            doc.add(p);

            final Set<Pair<String, String>> data = cve.getVendorProductList();
            for (Pair<String, String> pair : data) {
                if (pair.getLeft() != null && pair.getRight() != null) {
                    v.setStringValue(pair.getLeft());
                    p.setStringValue(pair.getRight());
                    indexWriter.addDocument(doc);
                    resetFieldAnalyzer();
                }
            }
            indexWriter.commit();
            indexWriter.close(true);
        } catch (DatabaseException ex) {
            LOGGER.debug("", ex);
            throw new IndexException("Error reading CPE data", ex);
        } catch (CorruptIndexException ex) {
            throw new IndexException("Unable to close an in-memory index", ex);
        } catch (IOException ex) {
            throw new IndexException("Unable to close an in-memory index", ex);
        }
    }

    /**
     * Resets the product and vendor field analyzers.
     */
    private void resetFieldAnalyzer() {
        if (productFieldAnalyzer != null) {
            productFieldAnalyzer.clear();
        }
        if (vendorFieldAnalyzer != null) {
            vendorFieldAnalyzer.clear();
        }
    }

    /**
     * Searches the index using the given search string.
     *
     * @param searchString the query text
     * @param maxQueryResults the maximum number of documents to return
     * @return the TopDocs found by the search
     * @throws ParseException thrown when the searchString is invalid
     * @throws IOException is thrown if there is an issue with the underlying
     * Index
     */
    public synchronized TopDocs search(String searchString, int maxQueryResults) throws ParseException, IOException {
        if (searchString == null || searchString.trim().isEmpty()) {
            throw new ParseException("Query is null or empty");
        }
        LOGGER.debug(searchString);
        resetFieldAnalyzer();
        final Query query = queryParser.parse(searchString);
        return search(query, maxQueryResults);
    }

    /**
     * Searches the index using the given query.
     *
     * @param query the query used to search the index
     * @param maxQueryResults the max number of results to return
     * @return the TopDocs found be the query
     * @throws CorruptIndexException thrown if the Index is corrupt
     * @throws IOException thrown if there is an IOException
     */
    public synchronized TopDocs search(Query query, int maxQueryResults) throws CorruptIndexException, IOException {
        resetFieldAnalyzer();
        return indexSearcher.search(query, maxQueryResults);
    }

    /**
     * Retrieves a document from the Index.
     *
     * @param documentId the id of the document to retrieve
     * @return the Document
     * @throws IOException thrown if there is an IOException
     */
    public Document getDocument(int documentId) throws IOException {
        return indexSearcher.doc(documentId);
    }

    /**
     * Returns the number of CPE entries stored in the index.
     *
     * @return the number of CPE entries stored in the index
     */
    public int numDocs() {
        if (indexReader == null) {
            return -1;
        }
        return indexReader.numDocs();
    }
}
