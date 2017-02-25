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
package org.owasp.dependencycheck.analyzer;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;
import org.junit.Test;
import org.owasp.dependencycheck.BaseTest;
import org.owasp.dependencycheck.Engine;
import org.owasp.dependencycheck.BaseDBTestCase;
import org.owasp.dependencycheck.dependency.Dependency;
import org.owasp.dependencycheck.exception.InitializationException;
import org.owasp.dependencycheck.utils.Settings;

/**
 *
 * @author Jeremy Long
 */
public class ArchiveAnalyzerIntegrationTest extends BaseDBTestCase {

    /**
     * Test of getSupportedExtensions method, of class ArchiveAnalyzer.
     */
    @Test
    public void testSupportsExtensions() {
        ArchiveAnalyzer instance = new ArchiveAnalyzer();
        Set<String> expResult = new HashSet<>();
        expResult.add("zip");
        expResult.add("war");
        expResult.add("ear");
        expResult.add("jar");
        expResult.add("sar");
        expResult.add("apk");
        expResult.add("nupkg");
        expResult.add("tar");
        expResult.add("gz");
        expResult.add("tgz");
        expResult.add("bz2");
        expResult.add("tbz2");
        for (String ext : expResult) {
            assertTrue(ext, instance.accept(new File("test." + ext)));
        }
    }

    /**
     * Test of getName method, of class ArchiveAnalyzer.
     */
    @Test
    public void testGetName() {
        ArchiveAnalyzer instance = new ArchiveAnalyzer();
        String expResult = "Archive Analyzer";
        String result = instance.getName();
        assertEquals(expResult, result);
    }

    /**
     * Test of supportsExtension method, of class ArchiveAnalyzer.
     */
    @Test
    public void testSupportsExtension() {
        String extension = "test.7z"; //not supported
        ArchiveAnalyzer instance = new ArchiveAnalyzer();
        assertFalse(extension, instance.accept(new File(extension)));
    }

    /**
     * Test of getAnalysisPhase method, of class ArchiveAnalyzer.
     */
    @Test
    public void testGetAnalysisPhase() {
        ArchiveAnalyzer instance = new ArchiveAnalyzer();
        AnalysisPhase expResult = AnalysisPhase.INITIAL;
        AnalysisPhase result = instance.getAnalysisPhase();
        assertEquals(expResult, result);
    }

    /**
     * Test of initialize and close methods, of class ArchiveAnalyzer.
     */
    @Test
    public void testInitialize() {
        ArchiveAnalyzer instance = new ArchiveAnalyzer();
        try {
            instance.setEnabled(true);
            instance.setFilesMatched(true);
            instance.initialize();
        } catch (InitializationException ex) {
            fail(ex.getMessage());
        } finally {
            try {
                instance.close();
            } catch (Exception ex) {
                fail(ex.getMessage());
            }
        }
    }

    /**
     * Test of analyze method, of class ArchiveAnalyzer.
     *
     * @throws java.lang.Exception when an error occurs
     */
    @Test
    public void testAnalyze() throws Exception {
        ArchiveAnalyzer instance = new ArchiveAnalyzer();
        //trick the analyzer into thinking it is active.
        instance.accept(new File("test.ear"));
        try {
            instance.initialize();
            File file = BaseTest.getResourceAsFile(this, "daytrader-ear-2.1.7.ear");
            Dependency dependency = new Dependency(file);
            Settings.setBoolean(Settings.KEYS.AUTO_UPDATE, false);
            Settings.setBoolean(Settings.KEYS.ANALYZER_NEXUS_ENABLED, false);
            Settings.setBoolean(Settings.KEYS.ANALYZER_CENTRAL_ENABLED, false);
            Engine engine = new Engine();

            int initial_size = engine.getDependencies().size();
            instance.analyze(dependency, engine);
            int ending_size = engine.getDependencies().size();

            engine.cleanup();

            assertTrue(initial_size < ending_size);

        } finally {
            instance.close();
        }
    }

    /**
     * Test of analyze method, of class ArchiveAnalyzer, with an executable jar.
     */
    @Test
    public void testAnalyzeExecutableJar() throws Exception {
        ArchiveAnalyzer instance = new ArchiveAnalyzer();
        //trick the analyzer into thinking it is active.
        instance.accept(new File("test.ear"));
        try {
            instance.initialize();
            File file = BaseTest.getResourceAsFile(this, "bootable-0.1.0.jar");
            Dependency dependency = new Dependency(file);
            Settings.setBoolean(Settings.KEYS.AUTO_UPDATE, false);
            Settings.setBoolean(Settings.KEYS.ANALYZER_NEXUS_ENABLED, false);
            Settings.setBoolean(Settings.KEYS.ANALYZER_CENTRAL_ENABLED, false);
            Engine engine = new Engine();

            int initial_size = engine.getDependencies().size();
            instance.analyze(dependency, engine);
            int ending_size = engine.getDependencies().size();

            engine.cleanup();

            assertTrue(initial_size < ending_size);

        } finally {
            instance.close();
        }
    }

    /**
     * Test of analyze method, of class ArchiveAnalyzer.
     */
    @Test
    public void testAnalyzeTar() throws Exception {
        ArchiveAnalyzer instance = new ArchiveAnalyzer();
        //trick the analyzer into thinking it is active so that it will initialize
        instance.accept(new File("test.tar"));
        try {
            instance.initialize();

            //File file = new File(this.getClass().getClassLoader().getResource("file.tar").getPath());
            //File file = new File(this.getClass().getClassLoader().getResource("stagedhttp-modified.tar").getPath());
            File file = BaseTest.getResourceAsFile(this, "stagedhttp-modified.tar");
            Dependency dependency = new Dependency(file);
            Settings.setBoolean(Settings.KEYS.AUTO_UPDATE, false);
            Settings.setBoolean(Settings.KEYS.ANALYZER_NEXUS_ENABLED, false);
            Settings.setBoolean(Settings.KEYS.ANALYZER_CENTRAL_ENABLED, false);
            Engine engine = new Engine();

            int initial_size = engine.getDependencies().size();
            instance.analyze(dependency, engine);
            int ending_size = engine.getDependencies().size();
            engine.cleanup();

            assertTrue(initial_size < ending_size);

        } finally {
            instance.close();
        }
    }

    /**
     * Test of analyze method, of class ArchiveAnalyzer.
     */
    @Test
    public void testAnalyzeTarGz() throws Exception {
        ArchiveAnalyzer instance = new ArchiveAnalyzer();
        instance.accept(new File("zip")); //ensure analyzer is "enabled"
        try {
            instance.initialize();

            //File file = new File(this.getClass().getClassLoader().getResource("file.tar.gz").getPath());
            File file = BaseTest.getResourceAsFile(this, "file.tar.gz");
            //Dependency dependency = new Dependency(file);
            Settings.setBoolean(Settings.KEYS.AUTO_UPDATE, false);
            Settings.setBoolean(Settings.KEYS.ANALYZER_NEXUS_ENABLED, false);
            Settings.setBoolean(Settings.KEYS.ANALYZER_CENTRAL_ENABLED, false);
            Engine engine = new Engine();

            int initial_size = engine.getDependencies().size();
            //instance.analyze(dependency, engine);
            engine.scan(file);
            engine.analyzeDependencies();
            int ending_size = engine.getDependencies().size();
            engine.cleanup();
            assertTrue(initial_size < ending_size);

        } finally {
            instance.close();
        }
    }

    /**
     * Test of analyze method, of class ArchiveAnalyzer.
     */
    @Test
    public void testAnalyzeTarBz2() throws Exception {
        ArchiveAnalyzer instance = new ArchiveAnalyzer();
        instance.accept(new File("zip")); //ensure analyzer is "enabled"
        try {
            instance.initialize();
            File file = BaseTest.getResourceAsFile(this, "file.tar.bz2");
            Settings.setBoolean(Settings.KEYS.AUTO_UPDATE, false);
            Settings.setBoolean(Settings.KEYS.ANALYZER_NEXUS_ENABLED, false);
            Settings.setBoolean(Settings.KEYS.ANALYZER_CENTRAL_ENABLED, false);
            Engine engine = new Engine();
            int initial_size = engine.getDependencies().size();
            engine.scan(file);
            engine.analyzeDependencies();
            int ending_size = engine.getDependencies().size();
            engine.cleanup();
            assertTrue(initial_size < ending_size);
        } finally {
            instance.close();
        }
    }

    /**
     * Test of analyze method, of class ArchiveAnalyzer.
     */
    @Test
    public void testAnalyzeTgz() throws Exception {
        ArchiveAnalyzer instance = new ArchiveAnalyzer();
        instance.accept(new File("zip")); //ensure analyzer is "enabled"
        try {
            instance.initialize();

            //File file = new File(this.getClass().getClassLoader().getResource("file.tgz").getPath());
            File file = BaseTest.getResourceAsFile(this, "file.tgz");
            Settings.setBoolean(Settings.KEYS.AUTO_UPDATE, false);
            Settings.setBoolean(Settings.KEYS.ANALYZER_NEXUS_ENABLED, false);
            Settings.setBoolean(Settings.KEYS.ANALYZER_CENTRAL_ENABLED, false);
            Engine engine = new Engine();

            int initial_size = engine.getDependencies().size();
            engine.scan(file);
            engine.analyzeDependencies();
            int ending_size = engine.getDependencies().size();
            engine.cleanup();
            assertTrue(initial_size < ending_size);

        } finally {
            instance.close();
        }
    }

    /**
     * Test of analyze method, of class ArchiveAnalyzer.
     */
    @Test
    public void testAnalyzeTbz2() throws Exception {
        ArchiveAnalyzer instance = new ArchiveAnalyzer();
        instance.accept(new File("zip")); //ensure analyzer is "enabled"
        try {
            instance.initialize();
            File file = BaseTest.getResourceAsFile(this, "file.tbz2");
            Settings.setBoolean(Settings.KEYS.AUTO_UPDATE, false);
            Settings.setBoolean(Settings.KEYS.ANALYZER_NEXUS_ENABLED, false);
            Settings.setBoolean(Settings.KEYS.ANALYZER_CENTRAL_ENABLED, false);
            Engine engine = new Engine();
            int initial_size = engine.getDependencies().size();
            engine.scan(file);
            engine.analyzeDependencies();
            int ending_size = engine.getDependencies().size();
            engine.cleanup();
            assertTrue(initial_size < ending_size);
        } finally {
            instance.close();
        }
    }

    /**
     * Test of analyze method, of class ArchiveAnalyzer.
     */
    @Test
    public void testAnalyze_badZip() throws Exception {
        ArchiveAnalyzer instance = new ArchiveAnalyzer();
        try {
            instance.initialize();

            //File file = new File(this.getClass().getClassLoader().getResource("test.zip").getPath());
            File file = BaseTest.getResourceAsFile(this, "test.zip");
            Dependency dependency = new Dependency(file);
            Settings.setBoolean(Settings.KEYS.AUTO_UPDATE, false);
            Settings.setBoolean(Settings.KEYS.ANALYZER_NEXUS_ENABLED, false);
            Settings.setBoolean(Settings.KEYS.ANALYZER_CENTRAL_ENABLED, false);
            Engine engine = new Engine();
            int initial_size = engine.getDependencies().size();
//            boolean failed = false;
//            try {
            instance.analyze(dependency, engine);
//            } catch (java.lang.UnsupportedClassVersionError ex) {
//                failed = true;
//            }
//            assertTrue(failed);
            int ending_size = engine.getDependencies().size();
            engine.cleanup();
            assertEquals(initial_size, ending_size);
        } finally {
            instance.close();
        }
    }
}
