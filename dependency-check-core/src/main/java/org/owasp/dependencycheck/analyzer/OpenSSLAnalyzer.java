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

import org.apache.commons.io.FileUtils;
import org.owasp.dependencycheck.Engine;
import org.owasp.dependencycheck.analyzer.exception.AnalysisException;
import org.owasp.dependencycheck.dependency.Confidence;
import org.owasp.dependencycheck.dependency.Dependency;
import org.owasp.dependencycheck.utils.FileFilterBuilder;
import org.owasp.dependencycheck.utils.Settings;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.owasp.dependencycheck.exception.InitializationException;

/**
 * Used to analyze OpenSSL source code present in the file system.
 *
 * @author Dale Visser
 */
public class OpenSSLAnalyzer extends AbstractFileTypeAnalyzer {

    /**
     * Hexadecimal.
     */
    private static final int HEXADECIMAL = 16;
    /**
     * Filename to analyze. All other .h files get removed from consideration.
     */
    private static final String OPENSSLV_H = "opensslv.h";

    /**
     * Filter that detects files named "__init__.py".
     */
    private static final FileFilter OPENSSLV_FILTER = FileFilterBuilder.newInstance().addFilenames(OPENSSLV_H).build();
    /**
     * Open SSL Version number pattern.
     */
    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "define\\s+OPENSSL_VERSION_NUMBER\\s+0x([0-9a-zA-Z]{8})L", Pattern.DOTALL
            | Pattern.CASE_INSENSITIVE);
    /**
     * The offset of the major version number.
     */
    private static final int MAJOR_OFFSET = 28;
    /**
     * The mask for the minor version number.
     */
    private static final long MINOR_MASK = 0x0ff00000L;
    /**
     * The offset of the minor version number.
     */
    private static final int MINOR_OFFSET = 20;
    /**
     * The max for the fix version.
     */
    private static final long FIX_MASK = 0x000ff000L;
    /**
     * The offset for the fix version.
     */
    private static final int FIX_OFFSET = 12;
    /**
     * The mask for the patch version.
     */
    private static final long PATCH_MASK = 0x00000ff0L;
    /**
     * The offset for the patch version.
     */
    private static final int PATCH_OFFSET = 4;
    /**
     * Number of letters.
     */
    private static final int NUM_LETTERS = 26;
    /**
     * The status mask.
     */
    private static final int STATUS_MASK = 0x0000000f;

    /**
     * Returns the open SSL version as a string.
     *
     * @param openSSLVersionConstant The open SSL version
     * @return the version of openssl
     */
    protected static String getOpenSSLVersion(long openSSLVersionConstant) {
        final long major = openSSLVersionConstant >>> MAJOR_OFFSET;
        final long minor = (openSSLVersionConstant & MINOR_MASK) >>> MINOR_OFFSET;
        final long fix = (openSSLVersionConstant & FIX_MASK) >>> FIX_OFFSET;
        final long patchLevel = (openSSLVersionConstant & PATCH_MASK) >>> PATCH_OFFSET;
        final String patch = 0 == patchLevel || patchLevel > NUM_LETTERS ? "" : String.valueOf((char) (patchLevel + 'a' - 1));
        final int statusCode = (int) (openSSLVersionConstant & STATUS_MASK);
        final String status = 0xf == statusCode ? "" : (0 == statusCode ? "-dev" : "-beta" + statusCode);
        return String.format("%d.%d.%d%s%s", major, minor, fix, patch, status);
    }

    /**
     * Returns the name of the Python Package Analyzer.
     *
     * @return the name of the analyzer
     */
    @Override
    public String getName() {
        return "OpenSSL Source Analyzer";
    }

    /**
     * Tell that we are used for information collection.
     *
     * @return INFORMATION_COLLECTION
     */
    @Override
    public AnalysisPhase getAnalysisPhase() {
        return AnalysisPhase.INFORMATION_COLLECTION;
    }

    /**
     * Returns the set of supported file extensions.
     *
     * @return the set of supported file extensions
     */
    @Override
    protected FileFilter getFileFilter() {
        return OPENSSLV_FILTER;
    }

    /**
     * No-op initializer implementation.
     *
     * @throws InitializationException never thrown
     */
    @Override
    protected void initializeFileTypeAnalyzer() throws InitializationException {
        // Nothing to do here.
    }

    /**
     * Analyzes python packages and adds evidence to the dependency.
     *
     * @param dependency the dependency being analyzed
     * @param engine the engine being used to perform the scan
     * @throws AnalysisException thrown if there is an unrecoverable error
     * analyzing the dependency
     */
    @Override
    protected void analyzeDependency(Dependency dependency, Engine engine)
            throws AnalysisException {
        final File file = dependency.getActualFile();
        final String parentName = file.getParentFile().getName();
        boolean found = false;
        final String contents = getFileContents(file);
        if (!contents.isEmpty()) {
            final Matcher matcher = VERSION_PATTERN.matcher(contents);
            if (matcher.find()) {
                dependency.getVersionEvidence().addEvidence(OPENSSLV_H, "Version Constant",
                        getOpenSSLVersion(Long.parseLong(matcher.group(1), HEXADECIMAL)), Confidence.HIGH);
                found = true;
            }
        }
        if (found) {
            dependency.setDisplayFileName(parentName + File.separatorChar + OPENSSLV_H);
            dependency.getVendorEvidence().addEvidence(OPENSSLV_H, "Vendor", "OpenSSL", Confidence.HIGHEST);
            dependency.getProductEvidence().addEvidence(OPENSSLV_H, "Product", "OpenSSL", Confidence.HIGHEST);
        } else {
            engine.getDependencies().remove(dependency);
        }
    }

    /**
     * Retrieves the contents of a given file.
     *
     * @param actualFile the file to read
     * @return the contents of the file
     * @throws AnalysisException thrown if there is an IO Exception
     */
    private String getFileContents(final File actualFile)
            throws AnalysisException {
        try {
            return FileUtils.readFileToString(actualFile, Charset.defaultCharset()).trim();
        } catch (IOException e) {
            throw new AnalysisException(
                    "Problem occurred while reading dependency file.", e);
        }
    }

    /**
     * Returns the setting for the analyzer enabled setting key.
     *
     * @return the setting for the analyzer enabled setting key
     */
    @Override
    protected String getAnalyzerEnabledSettingKey() {
        return Settings.KEYS.ANALYZER_OPENSSL_ENABLED;
    }
}
