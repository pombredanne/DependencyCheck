/*
 * This file is part of dependency-check-maven.
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
 * Copyright (c) 2014 Jeremy Long. All Rights Reserved.
 */
package org.owasp.dependencycheck.maven;

import java.io.File;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import mockit.Mock;
import mockit.MockUp;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.testing.stubs.ArtifactStub;
import org.apache.maven.project.MavenProject;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Assume;
import org.junit.Test;
import org.owasp.dependencycheck.Engine;
import org.owasp.dependencycheck.data.nvdcve.DatabaseException;
import org.owasp.dependencycheck.utils.InvalidSettingException;
import org.owasp.dependencycheck.utils.Settings;

/**
 *
 * @author Jeremy Long
 */
public class BaseDependencyCheckMojoTest extends BaseTest {

    /**
     * Checks if the test can be run. The test in this class fail, presumable
     * due to jmockit, if the JDK is 1.8+.
     *
     * @return true if the JDK is below 1.8.
     */
    public boolean canRun() {
        String version = System.getProperty("java.version");
        int length = version.indexOf('.', version.indexOf('.') + 1);
        version = version.substring(0, length);

        double v = Double.parseDouble(version);
        return v == 1.7;
    }

    /**
     * Test of scanArtifacts method, of class BaseDependencyCheckMojo.
     */
    @Test
    public void testScanArtifacts() throws DatabaseException, InvalidSettingException {
        if (canRun()) {
            MavenProject project = new MockUp<MavenProject>() {
                @Mock
                public Set<Artifact> getArtifacts() {
                    Set<Artifact> artifacts = new HashSet<>();
                    Artifact a = new ArtifactStub();
                    try {
                        File file = new File(Test.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                        a.setFile(file);
                        artifacts.add(a);
                    } catch (URISyntaxException ex) {
                        Logger.getLogger(BaseDependencyCheckMojoTest.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    //File file = new File(this.getClass().getClassLoader().getResource("daytrader-ear-2.1.7.ear").getPath());

                    return artifacts;
                }

                @Mock
                public String getName() {
                    return "test-project";
                }
            }.getMockInstance();

            boolean autoUpdate = Settings.getBoolean(Settings.KEYS.AUTO_UPDATE);
            Settings.setBoolean(Settings.KEYS.AUTO_UPDATE, false);
            Engine engine = new Engine();
            Settings.setBoolean(Settings.KEYS.AUTO_UPDATE, autoUpdate);

            assertTrue(engine.getDependencies().isEmpty());
            BaseDependencyCheckMojoImpl instance = new BaseDependencyCheckMojoImpl();
            try { //the mock above fails under some JDKs
                instance.scanArtifacts(project, engine);
            } catch (NullPointerException ex) {
                Assume.assumeNoException(ex);
            }
            assertFalse(engine.getDependencies().isEmpty());
            engine.cleanup();
        }
    }

    /**
     * Implementation of ODC Mojo for testing.
     */
    public class BaseDependencyCheckMojoImpl extends BaseDependencyCheckMojo {

        @Override
        public void runCheck() throws MojoExecutionException, MojoFailureException {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public String getName(Locale locale) {
            return "test implementation";
        }

        @Override
        public String getDescription(Locale locale) {
            return "test implementation";
        }

        @Override
        public boolean canGenerateReport() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
    }
}
