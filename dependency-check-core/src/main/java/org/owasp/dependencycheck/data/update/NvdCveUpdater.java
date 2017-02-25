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
package org.owasp.dependencycheck.data.update;

import java.net.MalformedURLException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.owasp.dependencycheck.data.nvdcve.CveDB;
import org.owasp.dependencycheck.data.nvdcve.DatabaseException;
import org.owasp.dependencycheck.data.nvdcve.DatabaseProperties;
import static org.owasp.dependencycheck.data.nvdcve.DatabaseProperties.MODIFIED;
import org.owasp.dependencycheck.data.update.exception.InvalidDataException;
import org.owasp.dependencycheck.data.update.exception.UpdateException;
import org.owasp.dependencycheck.data.update.nvd.DownloadTask;
import org.owasp.dependencycheck.data.update.nvd.NvdCveInfo;
import org.owasp.dependencycheck.data.update.nvd.ProcessTask;
import org.owasp.dependencycheck.data.update.nvd.UpdateableNvdCve;
import org.owasp.dependencycheck.utils.DateUtil;
import org.owasp.dependencycheck.utils.Downloader;
import org.owasp.dependencycheck.utils.DownloadFailedException;
import org.owasp.dependencycheck.utils.InvalidSettingException;
import org.owasp.dependencycheck.utils.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class responsible for updating the NVD CVE data.
 *
 * @author Jeremy Long
 */
public class NvdCveUpdater extends BaseUpdater implements CachedWebDataSource {

    /**
     * The logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(NvdCveUpdater.class);
    /**
     * The thread pool size to use for CPU-intense tasks.
     */
    private static final int PROCESSING_THREAD_POOL_SIZE = 1;
    /**
     * The thread pool size to use when downloading files.
     */
    private static final int DOWNLOAD_THREAD_POOL_SIZE = Settings.getInt(Settings.KEYS.MAX_DOWNLOAD_THREAD_POOL_SIZE, 50);
    /**
     * ExecutorService for CPU-intense processing tasks.
     */
    private ExecutorService processingExecutorService = null;
    /**
     * ExecutorService for tasks that involve blocking activities and are not very CPU-intense, e.g. downloading files.
     */
    private ExecutorService downloadExecutorService = null;

    /**
     * Downloads the latest NVD CVE XML file from the web and imports it into
     * the current CVE Database.
     *
     * @throws UpdateException is thrown if there is an error updating the
     * database
     */
    @Override
    public void update() throws UpdateException {
        try {
            if (!Settings.getBoolean(Settings.KEYS.UPDATE_NVDCVE_ENABLED, true)) {
                return;
            }
        } catch (InvalidSettingException ex) {
            LOGGER.trace("invalid setting UPDATE_NVDCVE_ENABLED", ex);
        }

        try {
            initializeExecutorServices();
            openDataStores();
            boolean autoUpdate = true;
            try {
                autoUpdate = Settings.getBoolean(Settings.KEYS.AUTO_UPDATE);
            } catch (InvalidSettingException ex) {
                LOGGER.debug("Invalid setting for auto-update; using true.");
            }
            if (autoUpdate && checkUpdate()) {
                final UpdateableNvdCve updateable = getUpdatesNeeded();
                if (updateable.isUpdateNeeded()) {
                    performUpdate(updateable);
                }
                getProperties().save(DatabaseProperties.LAST_CHECKED, Long.toString(System.currentTimeMillis()));
            }
        } catch (MalformedURLException ex) {
            throw new UpdateException("NVD CVE properties files contain an invalid URL, unable to update the data to use the most current data.", ex);
        } catch (DownloadFailedException ex) {
            LOGGER.warn(
                    "Unable to download the NVD CVE data; the results may not include the most recent CPE/CVEs from the NVD.");
            if (Settings.getString(Settings.KEYS.PROXY_SERVER) == null) {
                LOGGER.info(
                        "If you are behind a proxy you may need to configure dependency-check to use the proxy.");
            }
            throw new UpdateException("Unable to download the NVD CVE data.", ex);
        } finally {
            shutdownExecutorServices();
            closeDataStores();
        }
    }

    protected void initializeExecutorServices() {
        processingExecutorService = Executors.newFixedThreadPool(PROCESSING_THREAD_POOL_SIZE);
        downloadExecutorService = Executors.newFixedThreadPool(DOWNLOAD_THREAD_POOL_SIZE);
        LOGGER.debug("#download   threads: {}", DOWNLOAD_THREAD_POOL_SIZE);
        LOGGER.debug("#processing threads: {}", PROCESSING_THREAD_POOL_SIZE);
    }

    private void shutdownExecutorServices() {
        if (processingExecutorService != null) {
            processingExecutorService.shutdownNow();
        }
        if (downloadExecutorService != null) {
            downloadExecutorService.shutdownNow();
        }
    }

    /**
     * Checks if the NVD CVE XML files were last checked recently. As an
     * optimization, we can avoid repetitive checks against the NVD. Setting
     * CVE_CHECK_VALID_FOR_HOURS determines the duration since last check before
     * checking again. A database property stores the timestamp of the last
     * check.
     *
     * @return true to proceed with the check, or false to skip
     * @throws UpdateException thrown when there is an issue checking for
     * updates
     */
    private boolean checkUpdate() throws UpdateException {
        boolean proceed = true;
        // If the valid setting has not been specified, then we proceed to check...
        final int validForHours = Settings.getInt(Settings.KEYS.CVE_CHECK_VALID_FOR_HOURS, 0);
        if (dataExists() && 0 < validForHours) {
            // ms Valid = valid (hours) x 60 min/hour x 60 sec/min x 1000 ms/sec
            final long msValid = validForHours * 60L * 60L * 1000L;
            final long lastChecked = Long.parseLong(getProperties().getProperty(DatabaseProperties.LAST_CHECKED, "0"));
            final long now = System.currentTimeMillis();
            proceed = (now - lastChecked) > msValid;
            if (!proceed) {
                LOGGER.info("Skipping NVD check since last check was within {} hours.", validForHours);
                LOGGER.debug("Last NVD was at {}, and now {} is within {} ms.",
                        lastChecked, now, msValid);
            }
        }
        return proceed;
    }

    /**
     * Checks the CVE Index to ensure data exists and analysis can continue.
     *
     * @return true if the database contains data
     */
    private boolean dataExists() {
        CveDB cve = null;
        try {
            cve = new CveDB();
            cve.open();
            return cve.dataExists();
        } catch (DatabaseException ex) {
            return false;
        } finally {
            if (cve != null) {
                cve.close();
            }
        }
    }

    /**
     * Downloads the latest NVD CVE XML file from the web and imports it into
     * the current CVE Database.
     *
     * @param updateable a collection of NVD CVE data file references that need
     * to be downloaded and processed to update the database
     * @throws UpdateException is thrown if there is an error updating the
     * database
     */
    private void performUpdate(UpdateableNvdCve updateable) throws UpdateException {
        int maxUpdates = 0;
        for (NvdCveInfo cve : updateable) {
            if (cve.getNeedsUpdate()) {
                maxUpdates += 1;
            }
        }
        if (maxUpdates <= 0) {
            return;
        }
        if (maxUpdates > 3) {
            LOGGER.info("NVD CVE requires several updates; this could take a couple of minutes.");
        }

        final Set<Future<Future<ProcessTask>>> downloadFutures = new HashSet<Future<Future<ProcessTask>>>(maxUpdates);
        for (NvdCveInfo cve : updateable) {
            if (cve.getNeedsUpdate()) {
                final DownloadTask call = new DownloadTask(cve, processingExecutorService, getCveDB(), Settings.getInstance());
                downloadFutures.add(downloadExecutorService.submit(call));
            }
        }

        //next, move the future future processTasks to just future processTasks
        final Set<Future<ProcessTask>> processFutures = new HashSet<Future<ProcessTask>>(maxUpdates);
        for (Future<Future<ProcessTask>> future : downloadFutures) {
            Future<ProcessTask> task;
            try {
                task = future.get();
            } catch (InterruptedException ex) {
                LOGGER.debug("Thread was interrupted during download", ex);
                throw new UpdateException("The download was interrupted", ex);
            } catch (ExecutionException ex) {
                LOGGER.debug("Thread was interrupted during download execution", ex);
                throw new UpdateException("The execution of the download was interrupted", ex);
            }
            if (task == null) {
                LOGGER.debug("Thread was interrupted during download");
                throw new UpdateException("The download was interrupted; unable to complete the update");
            } else {
                processFutures.add(task);
            }
        }

        for (Future<ProcessTask> future : processFutures) {
            try {
                final ProcessTask task = future.get();
                if (task.getException() != null) {
                    throw task.getException();
                }
            } catch (InterruptedException ex) {
                LOGGER.debug("Thread was interrupted during processing", ex);
                throw new UpdateException(ex);
            } catch (ExecutionException ex) {
                LOGGER.debug("Execution Exception during process", ex);
                throw new UpdateException(ex);
            }
        }

        if (maxUpdates >= 1) { //ensure the modified file date gets written (we may not have actually updated it)
            getProperties().save(updateable.get(MODIFIED));
            LOGGER.info("Begin database maintenance.");
            getCveDB().cleanupDatabase();
            LOGGER.info("End database maintenance.");
        }
    }

    /**
     * Determines if the index needs to be updated. This is done by fetching the
     * NVD CVE meta data and checking the last update date. If the data needs to
     * be refreshed this method will return the NvdCveUrl for the files that
     * need to be updated.
     *
     * @return the collection of files that need to be updated
     * @throws MalformedURLException is thrown if the URL for the NVD CVE Meta
     * data is incorrect
     * @throws DownloadFailedException is thrown if there is an error.
     * downloading the NVD CVE download data file
     * @throws UpdateException Is thrown if there is an issue with the last
     * updated properties file
     */
    protected final UpdateableNvdCve getUpdatesNeeded() throws MalformedURLException, DownloadFailedException, UpdateException {
        LOGGER.info("starting getUpdatesNeeded() ...");
        UpdateableNvdCve updates;
        try {
            updates = retrieveCurrentTimestampsFromWeb();
        } catch (InvalidDataException ex) {
            final String msg = "Unable to retrieve valid timestamp from nvd cve downloads page";
            LOGGER.debug(msg, ex);
            throw new DownloadFailedException(msg, ex);
        } catch (InvalidSettingException ex) {
            LOGGER.debug("Invalid setting found when retrieving timestamps", ex);
            throw new DownloadFailedException("Invalid settings", ex);
        }

        if (updates == null) {
            throw new DownloadFailedException("Unable to retrieve the timestamps of the currently published NVD CVE data");
        }
        if (!getProperties().isEmpty()) {
            try {
                final int startYear = Settings.getInt(Settings.KEYS.CVE_START_YEAR, 2002);
                final int endYear = Calendar.getInstance().get(Calendar.YEAR);
                boolean needsFullUpdate = false;
                for (int y = startYear; y <= endYear; y++) {
                    final long val = Long.parseLong(getProperties().getProperty(DatabaseProperties.LAST_UPDATED_BASE + y, "0"));
                    if (val == 0) {
                        needsFullUpdate = true;
                    }
                }

                final long lastUpdated = Long.parseLong(getProperties().getProperty(DatabaseProperties.LAST_UPDATED, "0"));
                final long now = System.currentTimeMillis();
                final int days = Settings.getInt(Settings.KEYS.CVE_MODIFIED_VALID_FOR_DAYS, 7);
                if (!needsFullUpdate && lastUpdated == updates.getTimeStamp(MODIFIED)) {
                    updates.clear(); //we don't need to update anything.
                } else if (!needsFullUpdate && DateUtil.withinDateRange(lastUpdated, now, days)) {
                    for (NvdCveInfo entry : updates) {
                        if (MODIFIED.equals(entry.getId())) {
                            entry.setNeedsUpdate(true);
                        } else {
                            entry.setNeedsUpdate(false);
                        }
                    }
                } else { //we figure out which of the several XML files need to be downloaded.
                    for (NvdCveInfo entry : updates) {
                        if (MODIFIED.equals(entry.getId())) {
                            entry.setNeedsUpdate(true);
                        } else {
                            long currentTimestamp = 0;
                            try {
                                currentTimestamp = Long.parseLong(getProperties().getProperty(DatabaseProperties.LAST_UPDATED_BASE
                                        + entry.getId(), "0"));
                            } catch (NumberFormatException ex) {
                                LOGGER.debug("Error parsing '{}' '{}' from nvdcve.lastupdated",
                                        DatabaseProperties.LAST_UPDATED_BASE, entry.getId(), ex);
                            }
                            if (currentTimestamp == entry.getTimestamp()) {
                                entry.setNeedsUpdate(false);
                            }
                        }
                    }
                }
            } catch (NumberFormatException ex) {
                LOGGER.warn("An invalid schema version or timestamp exists in the data.properties file.");
                LOGGER.debug("", ex);
            }
        }
        return updates;
    }

    /**
     * Retrieves the timestamps from the NVD CVE meta data file.
     *
     * @return the timestamp from the currently published nvdcve downloads page
     * @throws MalformedURLException thrown if the URL for the NVD CCE Meta data
     * is incorrect.
     * @throws DownloadFailedException thrown if there is an error downloading
     * the nvd cve meta data file
     * @throws InvalidDataException thrown if there is an exception parsing the
     * timestamps
     * @throws InvalidSettingException thrown if the settings are invalid
     */
    private UpdateableNvdCve retrieveCurrentTimestampsFromWeb()
            throws MalformedURLException, DownloadFailedException, InvalidDataException, InvalidSettingException {


        final int start = Settings.getInt(Settings.KEYS.CVE_START_YEAR);
        final int end = Calendar.getInstance().get(Calendar.YEAR);

        final Map<String, Long> lastModifiedDates = retrieveLastModifiedDates(start, end);

        final UpdateableNvdCve updates = new UpdateableNvdCve();

        final String baseUrl20 = Settings.getString(Settings.KEYS.CVE_SCHEMA_2_0);
        final String baseUrl12 = Settings.getString(Settings.KEYS.CVE_SCHEMA_1_2);
        for (int i = start; i <= end; i++) {
            final String url = String.format(baseUrl20, i);
            updates.add(Integer.toString(i), url, String.format(baseUrl12, i),
                    lastModifiedDates.get(url), true);
        }

        final String url = Settings.getString(Settings.KEYS.CVE_MODIFIED_20_URL);
        updates.add(MODIFIED, url, Settings.getString(Settings.KEYS.CVE_MODIFIED_12_URL),
                lastModifiedDates.get(url), false);

        return updates;
    }

    /**
     * Retrieves the timestamps from the NVD CVE meta data file.
     *
     * @param startYear the first year whose item to check for the timestamp
     * @param endYear the last year whose item to check for the timestamp
     * @return the timestamps from the currently published nvdcve downloads page
     * @throws MalformedURLException thrown if the URL for the NVD CCE Meta data
     * is incorrect.
     * @throws DownloadFailedException thrown if there is an error downloading
     * the nvd cve meta data file
     */
    private Map<String, Long> retrieveLastModifiedDates(int startYear, int endYear)
            throws MalformedURLException, DownloadFailedException {

        final Set<String> urls = new HashSet<String>();
        final String baseUrl20 = Settings.getString(Settings.KEYS.CVE_SCHEMA_2_0);
        for (int i = startYear; i <= endYear; i++) {
            final String url = String.format(baseUrl20, i);
            urls.add(url);
        }
        urls.add(Settings.getString(Settings.KEYS.CVE_MODIFIED_20_URL));

        final Map<String, Future<Long>> timestampFutures = new HashMap<String, Future<Long>>();
        for (String url : urls) {
            final TimestampRetriever timestampRetriever = new TimestampRetriever(url);
            final Future<Long> future = downloadExecutorService.submit(timestampRetriever);
            timestampFutures.put(url, future);
        }

        final Map<String, Long> lastModifiedDates = new HashMap<String, Long>();
        for (String url : urls) {
            final Future<Long> timestampFuture = timestampFutures.get(url);
            final long timestamp;
            try {
                timestamp = timestampFuture.get(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new DownloadFailedException(e);
            }
            lastModifiedDates.put(url, timestamp);
        }

        return lastModifiedDates;
    }

    /**
     * Retrieves the last modified timestamp from a NVD CVE meta data file.
     */
    private static class TimestampRetriever implements Callable<Long> {

        private String url;

        TimestampRetriever(String url) {
            this.url = url;
        }

        @Override
        public Long call() throws Exception {
            LOGGER.debug("Checking for updates from: {}", url);
            try {
                Settings.initialize();
                return Downloader.getLastModified(new URL(url));
            } finally {
                Settings.cleanup(false);
            }
        }
    }
}
