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
 * Copyright (c) 2016 IBM Corporation. All Rights Reserved.
 */
package org.owasp.dependencycheck.analyzer;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.owasp.dependencycheck.Engine;
import org.owasp.dependencycheck.analyzer.exception.AnalysisException;
import org.owasp.dependencycheck.dependency.Confidence;
import org.owasp.dependencycheck.dependency.Dependency;
import org.owasp.dependencycheck.dependency.EvidenceCollection;
import org.owasp.dependencycheck.utils.FileFilterBuilder;
import org.owasp.dependencycheck.utils.Settings;

/**
 * This analyzer is used to analyze SWIFT and Objective-C packages by collecting
 * information from .podspec files. CocoaPods dependency manager see
 * https://cocoapods.org/.
 *
 * @author Bianca Jiang (https://twitter.com/biancajiang)
 */
@Experimental
public class CocoaPodsAnalyzer extends AbstractFileTypeAnalyzer {

    /**
     * The logger.
     */
//    private static final Logger LOGGER = LoggerFactory.getLogger(CocoaPodsAnalyzer.class);
    /**
     * The name of the analyzer.
     */
    private static final String ANALYZER_NAME = "CocoaPods Package Analyzer";

    /**
     * The phase that this analyzer is intended to run in.
     */
    private static final AnalysisPhase ANALYSIS_PHASE = AnalysisPhase.INFORMATION_COLLECTION;

    /**
     * The file name to scan.
     */
    public static final String PODSPEC = "podspec";
    /**
     * Filter that detects files named "*.podspec".
     */
    private static final FileFilter PODSPEC_FILTER = FileFilterBuilder.newInstance().addExtensions(PODSPEC).build();

    /**
     * The capture group #1 is the block variable. e.g. "Pod::Spec.new do
     * |spec|"
     */
    private static final Pattern PODSPEC_BLOCK_PATTERN = Pattern.compile("Pod::Spec\\.new\\s+?do\\s+?\\|(.+?)\\|");

    /**
     * Returns the FileFilter
     *
     * @return the FileFilter
     */
    @Override
    protected FileFilter getFileFilter() {
        return PODSPEC_FILTER;
    }

    @Override
    protected void initializeFileTypeAnalyzer() {
        // NO-OP
    }

    /**
     * Returns the name of the analyzer.
     *
     * @return the name of the analyzer.
     */
    @Override
    public String getName() {
        return ANALYZER_NAME;
    }

    /**
     * Returns the phase that the analyzer is intended to run in.
     *
     * @return the phase that the analyzer is intended to run in.
     */
    @Override
    public AnalysisPhase getAnalysisPhase() {
        return ANALYSIS_PHASE;
    }

    /**
     * Returns the key used in the properties file to reference the analyzer's
     * enabled property.
     *
     * @return the analyzer's enabled property setting key
     */
    @Override
    protected String getAnalyzerEnabledSettingKey() {
        return Settings.KEYS.ANALYZER_COCOAPODS_ENABLED;
    }

    @Override
    protected void analyzeDependency(Dependency dependency, Engine engine)
            throws AnalysisException {

        String contents;
        try {
            contents = FileUtils.readFileToString(dependency.getActualFile(), Charset.defaultCharset());
        } catch (IOException e) {
            throw new AnalysisException(
                    "Problem occurred while reading dependency file.", e);
        }
        final Matcher matcher = PODSPEC_BLOCK_PATTERN.matcher(contents);
        if (matcher.find()) {
            contents = contents.substring(matcher.end());
            final String blockVariable = matcher.group(1);

            final EvidenceCollection vendor = dependency.getVendorEvidence();
            final EvidenceCollection product = dependency.getProductEvidence();
            final EvidenceCollection version = dependency.getVersionEvidence();

            final String name = addStringEvidence(product, contents, blockVariable, "name", "name", Confidence.HIGHEST);
            if (!name.isEmpty()) {
                vendor.addEvidence(PODSPEC, "name_project", name, Confidence.HIGHEST);
            }
            addStringEvidence(product, contents, blockVariable, "summary", "summary", Confidence.HIGHEST);

            addStringEvidence(vendor, contents, blockVariable, "author", "authors?", Confidence.HIGHEST);
            addStringEvidence(vendor, contents, blockVariable, "homepage", "homepage", Confidence.HIGHEST);
            addStringEvidence(vendor, contents, blockVariable, "license", "licen[cs]es?", Confidence.HIGHEST);

            addStringEvidence(version, contents, blockVariable, "version", "version", Confidence.HIGHEST);
        }

        setPackagePath(dependency);
    }

    /**
     * Extracts evidence from the contents and adds it to the given evidence
     * collection.
     *
     * @param evidences the evidence collection to update
     * @param contents the text to extract evidence from
     * @param blockVariable the block variable within the content to search for
     * @param field the name of the field being searched for
     * @param fieldPattern the field pattern within the contents to search for
     * @param confidence the confidence level of the evidence if found
     * @return the string that was added as evidence
     */
    private String addStringEvidence(EvidenceCollection evidences, String contents,
            String blockVariable, String field, String fieldPattern, Confidence confidence) {
        String value = "";

        //capture array value between [ ]
        final Matcher arrayMatcher = Pattern.compile(
                String.format("\\s*?%s\\.%s\\s*?=\\s*?\\{\\s*?(.*?)\\s*?\\}", blockVariable, fieldPattern),
                Pattern.CASE_INSENSITIVE).matcher(contents);
        if (arrayMatcher.find()) {
            value = arrayMatcher.group(1);
        } else { //capture single value between quotes
            final Matcher matcher = Pattern.compile(
                    String.format("\\s*?%s\\.%s\\s*?=\\s*?(['\"])(.*?)\\1", blockVariable, fieldPattern),
                    Pattern.CASE_INSENSITIVE).matcher(contents);
            if (matcher.find()) {
                value = matcher.group(2);
            }
        }
        if (value.length() > 0) {
            evidences.addEvidence(PODSPEC, field, value, confidence);
        }
        return value;
    }

    /**
     * Sets the package path on the given dependency.
     *
     * @param dep the dependency to update
     */
    private void setPackagePath(Dependency dep) {
        final File file = new File(dep.getFilePath());
        final String parent = file.getParent();
        if (parent != null) {
            dep.setPackagePath(parent);
        }
    }
}
