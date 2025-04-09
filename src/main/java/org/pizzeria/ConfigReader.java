package org.pizzeria;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigReader {
    private Properties properties = new Properties();

    public ConfigReader(String filePath) {

        try (InputStream input = getClass().getClassLoader().getResourceAsStream(filePath)) {
            properties.load(input);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }
}
