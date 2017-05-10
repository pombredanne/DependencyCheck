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
package org.owasp.dependencycheck.data.nvdcve;

import java.sql.SQLException;
import org.owasp.dependencycheck.BaseDBTestCase;
import org.owasp.dependencycheck.dependency.Vulnerability;
import org.owasp.dependencycheck.dependency.VulnerableSoftware;
import org.owasp.dependencycheck.utils.DependencyVersion;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 *
 * @author Jeremy Long
 */
public class CveDBIT extends BaseDBTestCase {

    /**
     * Pretty useless tests of open, commit, and close methods, of class CveDB.
     */
    @Test
    public void testOpen() {
        CveDB instance = null;
        try {
            instance = CveDB.getInstance();
            instance.commit();
        } catch (DatabaseException | SQLException ex) {
            fail(ex.getMessage());
        } finally {
            int start = instance.getUsageCount();
            instance.close();
            int end = instance.getUsageCount();
            assertTrue( end < start);
        }
    }

    /**
     * Test of getCPEs method, of class CveDB.
     */
    @Test
    public void testGetCPEs() throws Exception {
        CveDB instance = CveDB.getInstance();
        String vendor = "apache";
        String product = "struts";
        Set<VulnerableSoftware> result = instance.getCPEs(vendor, product);
        assertTrue(result.size() > 5);
        instance.close();
    }

    /**
     * Test of getVulnerability method, of class CveDB.
     */
    @Test
    public void testgetVulnerability() throws Exception {
        CveDB instance = CveDB.getInstance();
        Vulnerability result = instance.getVulnerability("CVE-2014-0094");
        assertEquals("The ParametersInterceptor in Apache Struts before 2.3.16.1 allows remote attackers to \"manipulate\" the ClassLoader via the class parameter, which is passed to the getClass method.", result.getDescription());
        instance.close();
    }

    /**
     * Test of getVulnerabilities method, of class CveDB.
     */
    @Test
    public void testGetVulnerabilities() throws Exception {
        String cpeStr = "cpe:/a:apache:struts:2.1.2";
        CveDB instance = CveDB.getInstance();
        List<Vulnerability> results;

        results = instance.getVulnerabilities(cpeStr);
        assertTrue(results.size() > 5);
        cpeStr = "cpe:/a:jruby:jruby:1.6.3";
        results = instance.getVulnerabilities(cpeStr);
        assertTrue(results.size() > 1);

        boolean found = false;
        String expected = "CVE-2011-4838";
        for (Vulnerability v : results) {
            if (expected.equals(v.getName())) {
                found = true;
                break;
            }
        }
        assertTrue("Expected " + expected + ", but was not identified", found);

        found = false;
        expected = "CVE-2012-5370";
        for (Vulnerability v : results) {
            if (expected.equals(v.getName())) {
                found = true;
                break;
            }
        }
        assertTrue("Expected " + expected + ", but was not identified", found);
        instance.close();
    }

    /**
     * Test of getMatchingSoftware method, of class CveDB.
     */
    @Test
    public void testGetMatchingSoftware() throws Exception {
        CveDB instance = CveDB.getInstance();
        Map<String, Boolean> versions = new HashMap<>();
        DependencyVersion identifiedVersion = new DependencyVersion("1.0.1o");
        versions.put("cpe:/a:openssl:openssl:1.0.1e", Boolean.FALSE);
        Entry<String, Boolean> results = instance.getMatchingSoftware(versions, "openssl", "openssl", identifiedVersion);
        assertNull(results);
        versions.put("cpe:/a:openssl:openssl:1.0.1p", Boolean.FALSE);
        results = instance.getMatchingSoftware(versions, "openssl", "openssl", identifiedVersion);
        assertNull(results);

        versions.put("cpe:/a:openssl:openssl:1.0.1q", Boolean.TRUE);
        results = instance.getMatchingSoftware(versions, "openssl", "openssl", identifiedVersion);
        assertNotNull(results);
        assertEquals("cpe:/a:openssl:openssl:1.0.1q", results.getKey());

        versions.clear();

        versions.put("cpe:/a:springsource:spring_framework:3.2.5", Boolean.FALSE);
        versions.put("cpe:/a:springsource:spring_framework:3.2.6", Boolean.FALSE);
        versions.put("cpe:/a:springsource:spring_framework:3.2.7", Boolean.TRUE);

        versions.put("cpe:/a:springsource:spring_framework:4.0.1", Boolean.TRUE);
        versions.put("cpe:/a:springsource:spring_framework:4.0.0:m1", Boolean.FALSE);
        versions.put("cpe:/a:springsource:spring_framework:4.0.0:m2", Boolean.FALSE);
        versions.put("cpe:/a:springsource:spring_framework:4.0.0:rc1", Boolean.FALSE);

        identifiedVersion = new DependencyVersion("3.2.2");
        results = instance.getMatchingSoftware(versions, "springsource", "spring_framework", identifiedVersion);
        assertEquals("cpe:/a:springsource:spring_framework:3.2.7", results.getKey());
        assertTrue(results.getValue());
        identifiedVersion = new DependencyVersion("3.2.12");
        results = instance.getMatchingSoftware(versions, "springsource", "spring_framework", identifiedVersion);
        assertNull(results);

        identifiedVersion = new DependencyVersion("4.0.0");
        results = instance.getMatchingSoftware(versions, "springsource", "spring_framework", identifiedVersion);
        assertEquals("cpe:/a:springsource:spring_framework:4.0.1", results.getKey());
        assertTrue(results.getValue());
        identifiedVersion = new DependencyVersion("4.1.0");
        results = instance.getMatchingSoftware(versions, "springsource", "spring_framework", identifiedVersion);
        assertNull(results);

        versions.clear();

        versions.put("cpe:/a:jruby:jruby:-", Boolean.FALSE);
        identifiedVersion = new DependencyVersion("1.6.3");
        results = instance.getMatchingSoftware(versions, "springsource", "spring_framework", identifiedVersion);
        assertNotNull(results);
        instance.close();
    }
}
