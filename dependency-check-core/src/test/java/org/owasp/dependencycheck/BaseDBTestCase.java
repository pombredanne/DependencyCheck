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
package org.owasp.dependencycheck;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.Before;
import org.owasp.dependencycheck.utils.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstract database test case that is used to ensure the H2 DB exists prior
 * to performing tests that utilize the data contained within.
 *
 * @author Jeremy Long
 */
public abstract class BaseDBTestCase extends BaseTest {

    protected final static int BUFFER_SIZE = 2048;

    private final static Logger LOGGER = LoggerFactory.getLogger(BaseDBTestCase.class);

    @Before
    public void setUp() throws Exception {
        ensureDBExists();
    }

    public static void ensureDBExists() throws Exception {

        File f = new File("./target/data/dc.h2.db");
        if (f.exists() && f.isFile() && f.length() < 71680) {
            f.delete();
        }

        File dataPath = Settings.getDataDirectory();
        String fileName = Settings.getString(Settings.KEYS.DB_FILE_NAME);
        LOGGER.trace("DB file name {}", fileName);
        File dataFile = new File(dataPath, fileName);
        LOGGER.trace("Ensuring {} exists", dataFile.toString());
        if (!dataPath.exists() || !dataFile.exists()) {
            LOGGER.trace("Extracting database to {}", dataPath.toString());
            dataPath.mkdirs();
            FileInputStream fis = null;
            ZipInputStream zin = null;
            try {
                File path = new File(BaseDBTestCase.class.getClassLoader().getResource("data.zip").toURI().getPath());
                fis = new FileInputStream(path);
                zin = new ZipInputStream(new BufferedInputStream(fis));
                ZipEntry entry;
                while ((entry = zin.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        final File d = new File(dataPath, entry.getName());
                        d.mkdir();
                        continue;
                    }
                    FileOutputStream fos = null;
                    BufferedOutputStream dest = null;
                    try {
                        File o = new File(dataPath, entry.getName());
                        o.createNewFile();
                        fos = new FileOutputStream(o, false);
                        dest = new BufferedOutputStream(fos, BUFFER_SIZE);
                        byte data[] = new byte[BUFFER_SIZE];
                        int count;
                        while ((count = zin.read(data, 0, BUFFER_SIZE)) != -1) {
                            dest.write(data, 0, count);
                        }
                    } catch (Throwable ex) {
                        LOGGER.error("", ex);
                    } finally {
                        try {
                            if (dest != null) {
                                dest.flush();
                                dest.close();
                            }
                        } catch (Throwable ex) {
                            LOGGER.trace("", ex);
                        }
                        try {
                            if (fos != null) {
                                fos.close();
                            }
                        } catch (Throwable ex) {
                            LOGGER.trace("", ex);
                        }
                    }
                }
            } finally {
                try {
                    if (zin != null) {
                        zin.close();
                    }
                } catch (Throwable ex) {
                    LOGGER.trace("", ex);
                }
                try {
                    if (fis != null) {
                        fis.close();
                    }
                } catch (Throwable ex) {
                    LOGGER.trace("", ex);
                }
            }
        }
    }
}
