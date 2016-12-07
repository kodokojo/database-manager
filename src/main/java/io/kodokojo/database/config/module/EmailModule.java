package io.kodokojo.database.config.module;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.kodokojo.commons.config.AwsConfig;
import io.kodokojo.commons.config.EmailConfig;
import io.kodokojo.commons.config.properties.PropertyConfig;
import io.kodokojo.commons.config.properties.PropertyResolver;
import io.kodokojo.commons.config.properties.provider.PropertyValueProvider;
import io.kodokojo.commons.service.EmailSender;
import io.kodokojo.commons.service.NoopEmailSender;
import io.kodokojo.commons.service.SmtpEmailSender;
import io.kodokojo.commons.service.aws.SesEmailSender;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmailModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailModule.class);

    @Override
    protected void configure() {
        //  Nothing to do.
    }

    @Provides
    @Singleton
    EmailConfig provideEmailConfig(PropertyValueProvider valueProvider) {
        return createConfig(EmailConfig.class, valueProvider);
    }

    @Provides
    @Singleton
    AwsConfig provideAwsConfig(PropertyValueProvider valueProvider) {
        return createConfig(AwsConfig.class, valueProvider);
    }

    @Provides
    @Singleton
    EmailSender provideEmailSender(AwsConfig awsConfig, EmailConfig emailConfig) {
        if (StringUtils.isBlank(emailConfig.smtpHost())) {
            AWSCredentials credentials = getAwsCredentials();
            if (credentials == null) {
                return new NoopEmailSender();
            } else {
                return new SesEmailSender(emailConfig.smtpFrom(), Region.getRegion(Regions.fromName(awsConfig.region())));
            }
        } else {
            return new SmtpEmailSender(emailConfig.smtpHost(), emailConfig.smtpPort(), emailConfig.smtpUsername(), emailConfig.smtpPassword(), emailConfig.smtpFrom());
        }
    }

    private AWSCredentials getAwsCredentials() {
        AWSCredentials credentials = null;
        try {
            DefaultAWSCredentialsProviderChain defaultAWSCredentialsProviderChain = new DefaultAWSCredentialsProviderChain();
            credentials = defaultAWSCredentialsProviderChain.getCredentials();
            if (credentials == null) {
                InstanceProfileCredentialsProvider instanceProfileCredentialsProvider = new InstanceProfileCredentialsProvider(true);
                credentials = instanceProfileCredentialsProvider.getCredentials();
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Unable to retrieve AWS credentials.");
        }
        return credentials;
    }


    private <T extends PropertyConfig> T createConfig(Class<T> configClass, PropertyValueProvider valueProvider) {
        PropertyResolver resolver = new PropertyResolver(valueProvider);
        return resolver.createProxy(configClass);
    }
}
