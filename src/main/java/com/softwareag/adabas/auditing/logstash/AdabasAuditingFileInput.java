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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public static final PluginConfigSpec<String> DIRECTORY_CONFIG = PluginConfigSpec.stringSetting("directory", "./data");
    public static final PluginConfigSpec<String> META_DIR_CONFIG = PluginConfigSpec.stringSetting("metaDir", "./meta");
    public static final PluginConfigSpec<String> REST_URL_CONFIG = PluginConfigSpec.stringSetting("restURL", "http://localhost:8080/metadata/JSON");
    public static final PluginConfigSpec<String> TYPE_CONFIG = PluginConfigSpec.stringSetting("type", "adabas");

    private String id;

    // input plugin parameters
    private String directory;
    private String metaDir;
    private String restURL;
    private String pluginType;

    // Metadata API
    private MetadataThread metadataApi;

    private final CountDownLatch done = new CountDownLatch(1);
    private volatile boolean stopped;

    // all plugins must provide a constructor that accepts id, Configuration, and
    // Context
    public AdabasAuditingFileInput(String id, Configuration config, Context context) {
        // constructors should validate configuration options
        this.id = id;

        directory = config.get(DIRECTORY_CONFIG);
        metaDir = config.get(META_DIR_CONFIG);
        restURL = config.get(REST_URL_CONFIG);
        pluginType = config.get(TYPE_CONFIG);
    }

    @Override
    public void start(Consumer<Map<String, Object>> consumer) {
        if (restURL.equals("")) {
            restURL = "http://localhost:8080/metadata/JSON";
        }

        logger.info("Starting Adabas Auditing file input plugin");
        logger.info("Directory ............ {}", directory);
        logger.info("Metadata Directory ... {}", metaDir);
        logger.info("REST URL ............. {}", restURL);
        logger.info("Type ................. {}", pluginType);

        // check if metadata directory exists
        Path path = Paths.get(metaDir);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // set environment variable for metadata directory
        System.setProperty("REST_PATH", metaDir);

        // starts local REST API
        if (restURL.contains("localhost")) { 
            String regexPort = ":[0-9]+";
            Pattern pattern = Pattern.compile(regexPort);
            Matcher matcher = pattern.matcher(restURL);
            matcher.find();
            String url = matcher.group().split(":")[1];
            int port = Integer.valueOf(url);
            metadataApi = new MetadataThread(port);
            metadataApi.run();
        }

        // The start method should push Map<String, Object> instances to the supplied
        // QueueWriter
        // instance. Those will be converted to Event instances later in the Logstash
        // event
        // processing pipeline.
        //
        // Inputs that operate on unbounded streams of data or that poll indefinitely
        // for new
        // events should loop indefinitely until they receive a stop request. Inputs
        // that produce
        // a finite sequence of events should loop until that sequence is exhausted or
        // until they
        // receive a stop request, whichever comes first.

        // instantiate ALA parser
        ALAParse parser = ALAParse.getInstance();
        parser.setRestURL(restURL);
        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            path = Paths.get(directory);
            path.register(watchService, ENTRY_CREATE, ENTRY_MODIFY);
            while (!stopped) {
                boolean poll = true;
                while (poll) {
                    WatchKey key = watchService.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        byte[] message = fileToByteArray(path.resolve((Path) event.context()));
                        if (message != null) {
                            ArrayList<DataObject> parsedMessage = parser.parseBytesAsIndividualUABIs(message);
                            for (DataObject obj : parsedMessage) {
                                consumer.accept(convertToHashMap(obj, pluginType));
                            }
                        }
                    }
                    poll = key.reset();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
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
        return Arrays.asList(DIRECTORY_CONFIG, META_DIR_CONFIG, REST_URL_CONFIG, TYPE_CONFIG);
    }

    @Override
    public String getId() {
        return this.id;
    }

    private byte[] fileToByteArray(Path path) throws IOException {
        return Files.readAllBytes(path);
    }

    private HashMap<String, Object> convertToHashMap(DataObject object) {
        return convertToHashMap(object, null);
    }
    private HashMap<String, Object> convertToHashMap(DataObject object, String pluginType) {
        final HashMap<String, Object> map = new HashMap<>();
        if (pluginType != null && !pluginType.isEmpty()) {
            map.put("type", pluginType);
        }
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
        map.put("type", pluginType);
        return map;
    }
}
