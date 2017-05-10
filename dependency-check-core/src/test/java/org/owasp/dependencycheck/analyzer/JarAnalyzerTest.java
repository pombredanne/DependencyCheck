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
 * Copyright (c) 2012 Jeremy Long. All Rights Reserved.
 */
package org.owasp.dependencycheck.analyzer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import org.junit.Test;
import org.owasp.dependencycheck.BaseTest;
import org.owasp.dependencycheck.dependency.Dependency;
import org.owasp.dependencycheck.dependency.Evidence;
import org.owasp.dependencycheck.utils.Settings;

/**
 * @author Jeremy Long
 */
public class JarAnalyzerTest extends BaseTest {

    /**
     * Test of inspect method, of class JarAnalyzer.
     *
     * @throws Exception is thrown when an exception occurs.
     */
    @Test
    public void testAnalyze() throws Exception {
        //File file = new File(this.getClass().getClassLoader().getResource("struts2-core-2.1.2.jar").getPath());
        File file = BaseTest.getResourceAsFile(this, "struts2-core-2.1.2.jar");
        Dependency result = new Dependency(file);
        JarAnalyzer instance = new JarAnalyzer();
        instance.initializeFileTypeAnalyzer();
        instance.analyze(result, null);
        assertTrue(result.getVendorEvidence().toString().toLowerCase().contains("apache"));
        assertTrue(result.getVendorEvidence().getWeighting().contains("apache"));

        file = BaseTest.getResourceAsFile(this, "dwr.jar");
        result = new Dependency(file);
        instance.analyze(result, null);
        boolean found = false;
        for (Evidence e : result.getVendorEvidence()) {
            if (e.getName().equals("url")) {
                assertEquals("Project url was not as expected in dwr.jar", e.getValue(), "http://getahead.ltd.uk/dwr");
                found = true;
                break;
            }
        }
        assertTrue("Project url was not found in dwr.jar", found);

        //file = new File(this.getClass().getClassLoader().getResource("org.mortbay.jetty.jar").getPath());
        file = BaseTest.getResourceAsFile(this, "org.mortbay.jetty.jar");
        result = new Dependency(file);
        instance.analyze(result, null);
        found = false;
        for (Evidence e : result.getProductEvidence()) {
            if (e.getName().equalsIgnoreCase("package-title")
                    && e.getValue().equalsIgnoreCase("org.mortbay.http")) {
                found = true;
                break;
            }
        }
        assertTrue("package-title of org.mortbay.http not found in org.mortbay.jetty.jar", found);

        found = false;
        for (Evidence e : result.getVendorEvidence()) {
            if (e.getName().equalsIgnoreCase("implementation-url")
                    && e.getValue().equalsIgnoreCase("http://jetty.mortbay.org")) {
                found = true;
                break;
            }
        }
        assertTrue("implementation-url of http://jetty.mortbay.org not found in org.mortbay.jetty.jar", found);

        found = false;
        for (Evidence e : result.getVersionEvidence()) {
            if (e.getName().equalsIgnoreCase("Implementation-Version")
                    && e.getValue().equalsIgnoreCase("4.2.27")) {
                found = true;
                break;
            }
        }
        assertTrue("implementation-version of 4.2.27 not found in org.mortbay.jetty.jar", found);

        //file = new File(this.getClass().getClassLoader().getResource("org.mortbay.jmx.jar").getPath());
        file = BaseTest.getResourceAsFile(this, "org.mortbay.jmx.jar");
        result = new Dependency(file);
        instance.analyze(result, null);
        assertEquals("org.mortbar.jmx.jar has version evidence?", result.getVersionEvidence().size(), 0);
    }

    /**
     * Test of getSupportedExtensions method, of class JarAnalyzer.
     */
    @Test
    public void testAcceptSupportedExtensions() throws Exception {
        JarAnalyzer instance = new JarAnalyzer();
        instance.initialize();
        instance.setEnabled(true);
        String[] files = {"test.jar", "test.war"};
        for (String name : files) {
            assertTrue(name, instance.accept(new File(name)));
        }
    }

    /**
     * Test of getName method, of class JarAnalyzer.
     */
    @Test
    public void testGetName() {
        JarAnalyzer instance = new JarAnalyzer();
        String expResult = "Jar Analyzer";
        String result = instance.getName();
        assertEquals(expResult, result);
    }

    @Test
    public void testParseManifest() throws Exception {
        File file = BaseTest.getResourceAsFile(this, "xalan-2.7.0.jar");
        Dependency result = new Dependency(file);
        JarAnalyzer instance = new JarAnalyzer();
        List<JarAnalyzer.ClassNameInformation> cni = new ArrayList<>();
        instance.parseManifest(result, cni);

        assertTrue(result.getVersionEvidence().getEvidence("manifest: org/apache/xalan/").size() > 0);
    }

    /**
     * Test of getAnalysisPhase method, of class JarAnalyzer.
     */
    @Test
    public void testGetAnalysisPhase() {
        JarAnalyzer instance = new JarAnalyzer();
        AnalysisPhase expResult = AnalysisPhase.INFORMATION_COLLECTION;
        AnalysisPhase result = instance.getAnalysisPhase();
        assertEquals(expResult, result);
    }

    /**
     * Test of getAnalyzerEnabledSettingKey method, of class JarAnalyzer.
     */
    @Test
    public void testGetAnalyzerEnabledSettingKey() {
        JarAnalyzer instance = new JarAnalyzer();
        String expResult = Settings.KEYS.ANALYZER_JAR_ENABLED;
        String result = instance.getAnalyzerEnabledSettingKey();
        assertEquals(expResult, result);
    }

    @Test
    public void testClassInformation() {
        JarAnalyzer.ClassNameInformation instance = new JarAnalyzer.ClassNameInformation("org/owasp/dependencycheck/analyzer/JarAnalyzer");
        assertEquals("org/owasp/dependencycheck/analyzer/JarAnalyzer", instance.getName());
        List<String> expected = Arrays.asList("owasp", "dependencycheck", "analyzer", "jaranalyzer");
        List<String> results = instance.getPackageStructure();
        assertEquals(expected, results);
    }
}
