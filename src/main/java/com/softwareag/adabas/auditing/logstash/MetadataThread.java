/*
 * Copyright (c) 2025 Software GmbH, Darmstadt, Germany and/or its subsidiaries and/or its affiliates and/or their licensors.
 */
package com.softwareag.adabas.auditing.logstash;

import com.softwareag.adabas.adamfmetadatarest.web.MetaDataController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class MetadataThread extends Thread {

    private int apiPort;
    private static final Logger logger = LogManager.getLogger();

    public MetadataThread(int apiPort) {
        this.apiPort = apiPort;
    }

    @Override
    public void run() {
        try {
            MetaDataController.startAPI(apiPort, false);
        } catch (IOException e) {
            logger.error("", e);
        }
    }
}
