/*
 * Copyright (c) 2025 Software GmbH, Darmstadt, Germany and/or its subsidiaries and/or its affiliates and/or their licensors.
 */
package com.softwareag.adabas.auditing.logstash;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.softwareag.adabas.auditingparser.ALAParse;
import com.softwareag.adabas.collector.sdk.DataObject;

import co.elastic.logstash.api.Configuration;
import co.elastic.logstash.api.Context;
import co.elastic.logstash.api.Input;
import co.elastic.logstash.api.LogstashPlugin;
import co.elastic.logstash.api.PluginConfigSpec;

// class name must match plugin name
@LogstashPlugin(name = "adabas_auditing_file_input")
public class AdabasAuditingFileInput implements Input {

    // log4j2 logger
    private static final Logger logger = LogManager.getLogger(AdabasAuditingFileInput.class);

    public static final PluginConfigSpec<String> DIRECTORY_CONFIG = PluginConfigSpec.stringSetting("directory",
            "./data");
    public static final PluginConfigSpec<String> META_DIR_CONFIG = PluginConfigSpec.stringSetting("metaDir", "./meta");
    public static final PluginConfigSpec<String> TYPE_CONFIG = PluginConfigSpec.stringSetting("type",
            "adabas-auditing");

    private String id;

    // input plugin parameters
    private String directory;
    private String metaDir;
    private String pluginType;

    private final CountDownLatch done = new CountDownLatch(1);
    private volatile boolean stopped;

    // all plugins must provide a constructor that accepts id, Configuration, and
    // Context
    public AdabasAuditingFileInput(String id, Configuration config, Context context) {
        // constructors should validate configuration options
        this.id = id;

        directory = config.get(DIRECTORY_CONFIG);
        metaDir = config.get(META_DIR_CONFIG);
        pluginType = config.get(TYPE_CONFIG);
    }

    @Override
    public void start(Consumer<Map<String, Object>> consumer) {
        logger.debug("Starting Adabas Auditing file input plugin");
        logger.debug("(Adabas)Directory ............ {}", directory);
        logger.debug("(Adabas)Metadata Directory ... {}", metaDir);
        logger.debug("(Adabas)Type ................. {}", pluginType);

        // Check if metadata directory exists
        Path path = Paths.get(metaDir);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                logger.error("Failed to create metadata directory", e);
                return;
            }
        }

        // Instantiate ALA parser
        ALAParse parser = ALAParse.getInstance();
        parser.setMetaDataDirectory(metaDir);

        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            path = Paths.get(directory);
            path.register(watchService, ENTRY_CREATE, ENTRY_MODIFY);

            while (!stopped) {
                WatchKey key = watchService.poll(); // Non-blocking poll
                if (key == null) {
                    Thread.sleep(100); // Avoid busy-waiting
                    continue;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    logger.debug("Event kind: {}", event.kind());
                    logger.debug("Event context: {}", event.context());

                    try {
                        byte[] message = fileToByteArray(path.resolve((Path) event.context()));
                        if (message != null) {
                            ArrayList<DataObject> parsedMessage = parser.parseBytesAsIndividualUABIs(message);
                            for (DataObject obj : parsedMessage) {
                                HashMap<String, Object> map = convertToHashMap(obj);
                                consumer.accept(new HashMap<>(Collections.singletonMap("adabas-auditing", map)));
                            }
                        }
                    } catch (java.nio.file.AccessDeniedException e) {
                        logger.error("Access denied to file: {}.", event.context(), e);
                    } catch (Exception e) {
                        logger.error("Error processing file event", e);
                    }
                }

                if (!key.reset()) {
                    logger.warn("WatchKey could not be reset. Exiting watch loop.");
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("Error in Adabas Auditing file input plugin", e);
        } finally {
            stopped = true;
            done.countDown();
        }
    }

    @Override
    public void stop() {
        stopped = true; // set flag to request cooperative stop of input
    }

    @Override
    public void awaitStop() throws InterruptedException {
        done.await(); // blocks until input has stopped
    }

    @Override
    public Collection<PluginConfigSpec<?>> configSchema() {
        // should return a list of all configuration options for this plugin
        return Arrays.asList(DIRECTORY_CONFIG, META_DIR_CONFIG, TYPE_CONFIG);
    }

    @Override
    public String getId() {
        return this.id;
    }

    private byte[] fileToByteArray(Path path) throws IOException {
        return Files.readAllBytes(path);
    }

    private HashMap<String, Object> convertToHashMap(DataObject object) {
        HashMap<String, Object> map = new HashMap<>();
        for (Map.Entry<String, Object> entry : object.getList().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof DataObject) {
                Object obj = convertToHashMap((DataObject) value);
                map.put(key, obj);
            } else {
                if (value instanceof ArrayList<?>) {
                    ArrayList<Object> list = new ArrayList<>();
                    for (Object obj : (ArrayList<?>) value) {
                        if (obj instanceof DataObject) {
                            list.add(convertToHashMap((DataObject) obj));
                        } else {
                            list.add(obj);
                        }
                    }
                    map.put(key, list);
                } else {
                    map.put(key, value);
                }
            }
        }
        return map;
    }
}
