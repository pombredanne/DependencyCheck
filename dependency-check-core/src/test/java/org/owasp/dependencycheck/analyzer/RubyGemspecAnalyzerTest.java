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
 * Copyright (c) 2015 Institute for Defense Analyses. All Rights Reserved.
 */
package org.owasp.dependencycheck.analyzer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.owasp.dependencycheck.BaseTest;
import org.owasp.dependencycheck.analyzer.exception.AnalysisException;
import org.owasp.dependencycheck.dependency.Dependency;

import java.io.File;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * Unit tests for {@link RubyGemspecAnalyzer}.
 *
 * @author Dale Visser
 */
public class RubyGemspecAnalyzerTest extends BaseTest {

    /**
     * The analyzer to test.
     */
    private RubyGemspecAnalyzer analyzer;

    /**
     * Correctly setup the analyzer for testing.
     *
     * @throws Exception thrown if there is a problem
     */
    @Before
    public void setUp() throws Exception {
        analyzer = new RubyGemspecAnalyzer();
        analyzer.setFilesMatched(true);
        analyzer.initialize();
    }

    /**
     * Cleanup the analyzer's temp files, etc.
     *
     * @throws Exception thrown if there is a problem
     */
    @After
    public void tearDown() throws Exception {
        analyzer.close();
        analyzer = null;
    }

    /**
     * Test Ruby Gemspec name.
     */
    @Test
    public void testGetName() {
        assertThat(analyzer.getName(), is("Ruby Gemspec Analyzer"));
    }

    /**
     * Test Ruby Gemspec file support.
     */
    @Test
    public void testSupportsFiles() {
        assertThat(analyzer.accept(new File("test.gemspec")), is(true));
//        assertThat(analyzer.accept(new File("Rakefile")), is(true));
    }

    /**
     * Test Ruby Gemspec analysis.
     *
     * @throws AnalysisException is thrown when an exception occurs.
     */
    @Test
    public void testAnalyzePackageJson() throws AnalysisException {
        final Dependency result = new Dependency(BaseTest.getResourceAsFile(this,
                "ruby/vulnerable/gems/specifications/rest-client-1.7.2.gemspec"));
        analyzer.analyze(result, null);
        final String vendorString = result.getVendorEvidence().toString();
        assertThat(vendorString, containsString("REST Client Team"));
        assertThat(vendorString, containsString("rest-client_project"));
        assertThat(vendorString, containsString("rest.client@librelist.com"));
        assertThat(vendorString, containsString("https://github.com/rest-client/rest-client"));
        assertThat(result.getProductEvidence().toString(), containsString("rest-client"));
        assertThat(result.getVersionEvidence().toString(), containsString("1.7.2"));
    }
    
    /**
     * Test Rakefile analysis.
     *
     * @throws AnalysisException is thrown when an exception occurs.
     */
    //@Test  TODO: place holder to test Rakefile support
    public void testAnalyzeRakefile() throws AnalysisException {
        final Dependency result = new Dependency(BaseTest.getResourceAsFile(this,
                "ruby/vulnerable/gems/rails-4.1.15/vendor/bundle/ruby/2.2.0/gems/pg-0.18.4/Rakefile"));
        analyzer.analyze(result, null);
        assertTrue(result.getEvidence().size()>0);
    }
}
