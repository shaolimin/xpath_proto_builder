package com.yahoo.xpathproto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.xpathproto.dataobject.Config;

/**
 * The Class ConfigLoader - It is used to load the config from a file the first time and then uses caching for
 * subsequent calls to load the same config. Works with absolute file paths and resources within the project as well.
 */
public class ConfigLoader implements Callable<Config> {

    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private String configPath;

    /**
     * Instantiates a new config loader.
     *
     * @param configPath - the path to the config file or resource
     */
    public ConfigLoader(String configPath) {
        this.configPath = configPath;
    }

    @Override
    public Config call() throws Exception {
        logger.info("Loaded config: {}", configPath);
        InputStream configstream = null;

        try {
            configstream = new FileInputStream(configPath);
        } catch (FileNotFoundException e) {
            logger.info("File does not exist at: {}. Trying with resource..", configPath);
            configstream = ProtoBuilder.class.getResourceAsStream(configPath);
            if (configstream == null) {
                throw new IllegalArgumentException("Failed to load config: " + configPath);
            }
        }

        IOException exception = null;
        Config config = null;
        try {
            config = mapper.readValue(configstream, Config.class);
            validateAndDenormalize(config);
        } catch (IOException e) {
            exception = e;
        } finally {
            try {
                configstream.close();
            } catch (IOException f) {
                logger.warn("Unable to close stream for file: " + configPath, f);
            }
        }
        if (exception != null) {
            throw new RuntimeException("Failed to load transform config from: " + configPath, exception);
        }
        return config;
    }

    private void validateAndDenormalize(Config config) {
        Iterator<Map.Entry<String, Config.Definition>> entries = config.definitions.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, Config.Definition> entry = entries.next();

            Config.Definition definition = entry.getValue();
            for (Config.Entry transform : definition.getTransforms()) {
                if (transform.getPath() == null) {
                    transform.setPath(transform.getField());
                }

                // field must have been null too if path is still null
                if (transform.getPath() == null) {
                    throw new IllegalArgumentException("target field or the path must be specified in transforms list");
                }

                if (transform.getHandler() != null) {
                    transform.setHandler(transform.getHandler());
                }
            }
        }
    }

}
