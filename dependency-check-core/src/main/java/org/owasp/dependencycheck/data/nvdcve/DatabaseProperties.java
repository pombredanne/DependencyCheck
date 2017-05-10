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

import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TreeMap;
import javax.annotation.concurrent.ThreadSafe;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.owasp.dependencycheck.data.update.nvd.NvdCveInfo;
import org.owasp.dependencycheck.data.update.exception.UpdateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a wrapper around a set of properties that are stored in the database.
 * This class is safe to be accessed from multiple threads in parallel.
 *
 * @author Jeremy Long
 */
@ThreadSafe
public class DatabaseProperties {

    /**
     * The Logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseProperties.class);
    /**
     * Modified key word, used as a key to store information about the modified
     * file (i.e. the containing the last 8 days of updates)..
     */
    public static final String MODIFIED = "Modified";
    /**
     * The properties file key for the last checked field - used to store the
     * last check time of the Modified NVD CVE xml file.
     */
    public static final String LAST_CHECKED = "NVD CVE Checked";
    /**
     * The properties file key for the last updated field - used to store the
     * last updated time of the Modified NVD CVE xml file.
     */
    public static final String LAST_UPDATED = "NVD CVE Modified";
    /**
     * Stores the last updated time for each of the NVD CVE files. These
     * timestamps should be updated if we process the modified file within 7
     * days of the last update.
     */
    public static final String LAST_UPDATED_BASE = "NVD CVE ";
    /**
     * The key for the last time the CPE data was updated.
     */
    public static final String LAST_CPE_UPDATE = "LAST_CPE_UPDATE";
    /**
     * The key for the database schema version.
     */
    public static final String VERSION = "version";

    /**
     * A collection of properties about the data.
     */
    private final Properties properties;
    /**
     * A reference to the database.
     */
    private final CveDB cveDB;

    /**
     * Constructs a new data properties object.
     *
     * @param cveDB the database object holding the properties
     */
    DatabaseProperties(CveDB cveDB) {
        this.cveDB = cveDB;
        this.properties = cveDB.getProperties();
    }

    /**
     * Returns whether or not any properties are set.
     *
     * @return whether or not any properties are set
     */
    public boolean isEmpty() {
        return properties == null || properties.isEmpty();
    }

    /**
     * Saves the last updated information to the properties file.
     *
     * @param updatedValue the updated NVD CVE entry
     * @throws UpdateException is thrown if there is an update exception
     */
    public void save(NvdCveInfo updatedValue) throws UpdateException {
        if (updatedValue == null) {
            return;
        }
        save(LAST_UPDATED_BASE + updatedValue.getId(), String.valueOf(updatedValue.getTimestamp()));
    }

    /**
     * Saves the key value pair to the properties store.
     *
     * @param key the property key
     * @param value the property value
     * @throws UpdateException is thrown if there is an update exception
     */
    public void save(String key, String value) throws UpdateException {
        properties.put(key, value);
        cveDB.saveProperty(key, value);
    }

    /**
     * Returns the property value for the given key. If the key is not contained
     * in the underlying properties null is returned.
     *
     * @param key the property key
     * @return the value of the property
     */
    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    /**
     * Returns the property value for the given key. If the key is not contained
     * in the underlying properties the default value is returned.
     *
     * @param key the property key
     * @param defaultValue the default value
     * @return the value of the property
     */
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    /**
     * Returns the collection of Database Properties as a properties collection.
     *
     * @return the collection of Database Properties
     */
    public Properties getProperties() {
        return properties;
    }

    /**
     * Returns a map of the meta data from the database properties. This
     * primarily contains timestamps of when the NVD CVE information was last
     * updated.
     *
     * @return a map of the database meta data
     */
    public Map<String, String> getMetaData() {
        final Map<String, String> map = new TreeMap<>();
        for (Entry<Object, Object> entry : properties.entrySet()) {
            final String key = (String) entry.getKey();
            if (!"version".equals(key)) {
                if (key.startsWith("NVD CVE ")) {
                    try {
                        final long epoch = Long.parseLong((String) entry.getValue());
                        final DateTime date = new DateTime(epoch);
                        final DateTimeFormatter format = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm:ss");
                        final String formatted = format.print(date);
//                        final Date date = new Date(epoch);
//                        final DateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
//                        final String formatted = format.format(date);
                        map.put(key, formatted);
                    } catch (Throwable ex) { //deliberately being broad in this catch clause
                        LOGGER.debug("Unable to parse timestamp from DB", ex);
                        map.put(key, (String) entry.getValue());
                    }
                } else {
                    map.put(key, (String) entry.getValue());
                }
            }
        }
        return map;
    }
}
