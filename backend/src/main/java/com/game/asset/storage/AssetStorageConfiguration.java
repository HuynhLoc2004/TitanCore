package com.game.asset.storage;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.Arrays;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AssetStorageProperties.class)
public class AssetStorageConfiguration {

    @Bean
    public InitializingBean assetStorageConfigurationValidator(
            AssetStorageProperties properties,
            Environment environment
    ) {
        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        return () -> AssetStoragePropertiesValidator.validate(properties, production);
    }

    @Bean(destroyMethod = "close")
    @DependsOn("assetStorageConfigurationValidator")
    @ConditionalOnProperty(name = "app.asset-storage.enabled", havingValue = "true")
    public S3Client assetS3Client(AssetStorageProperties properties) {
        StaticCredentialsProvider credentials = credentials(properties);
        S3Configuration serviceConfiguration = serviceConfiguration(properties);
        return S3Client.builder()
                .endpointOverride(AssetStoragePropertiesValidator.endpoint(properties))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentials)
                .serviceConfiguration(serviceConfiguration)
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(properties.getConnectTimeout())
                        .socketTimeout(properties.getReadTimeout()))
                .build();
    }

    @Bean(destroyMethod = "close")
    @DependsOn("assetStorageConfigurationValidator")
    @ConditionalOnProperty(name = "app.asset-storage.enabled", havingValue = "true")
    public S3Presigner assetS3Presigner(AssetStorageProperties properties) {
        return S3Presigner.builder()
                .endpointOverride(AssetStoragePropertiesValidator.endpoint(properties))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(serviceConfiguration(properties))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.asset-storage.enabled", havingValue = "true")
    public ObjectStoragePort objectStoragePort(
            S3Client assetS3Client,
            S3Presigner assetS3Presigner,
            AssetStorageProperties properties
    ) {
        return new S3ObjectStorageAdapter(assetS3Client, assetS3Presigner, properties);
    }

    private StaticCredentialsProvider credentials(AssetStorageProperties properties) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                properties.getAccessKey(),
                properties.getSecretKey()
        ));
    }

    private S3Configuration serviceConfiguration(AssetStorageProperties properties) {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(properties.isPathStyle())
                .build();
    }
}
