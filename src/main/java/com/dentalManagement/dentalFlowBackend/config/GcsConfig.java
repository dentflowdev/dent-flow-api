package com.dentalManagement.dentalFlowBackend.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GcsConfig {

    @Bean
    public Storage storage() {
        // On Cloud Run, ADC picks up the service account automatically
        // No credentials file needed
        return StorageOptions.getDefaultInstance().getService();
    }
}
