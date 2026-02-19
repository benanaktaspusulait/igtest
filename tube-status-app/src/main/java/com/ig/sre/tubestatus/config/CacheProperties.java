package com.ig.sre.tubestatus.config;

import com.ig.sre.tubestatus.common.AppConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = AppConstants.PropertyPrefixes.CACHE)
@Validated
public class CacheProperties {

    @Valid
    @NotNull
    private EntryProperties lineStatus = new EntryProperties(500L, Duration.ofMinutes(15));

    @Valid
    @NotNull
    private EntryProperties unplannedDisruptions = new EntryProperties(10L, Duration.ofMinutes(5));

    public EntryProperties getLineStatus() {
        return new EntryProperties(lineStatus);
    }

    public void setLineStatus(EntryProperties lineStatus) {
        this.lineStatus = new EntryProperties(Objects.requireNonNullElseGet(lineStatus, EntryProperties::new));
    }

    public EntryProperties getUnplannedDisruptions() {
        return new EntryProperties(unplannedDisruptions);
    }

    public void setUnplannedDisruptions(EntryProperties unplannedDisruptions) {
        this.unplannedDisruptions = new EntryProperties(
                Objects.requireNonNullElseGet(unplannedDisruptions, EntryProperties::new)
        );
    }

    public static class EntryProperties {

        @Positive
        private long maximumSize;

        @NotNull
        private Duration expireAfterWrite;

        public EntryProperties() {
        }

        public EntryProperties(long maximumSize, Duration expireAfterWrite) {
            this.maximumSize = maximumSize;
            this.expireAfterWrite = expireAfterWrite;
        }

        public EntryProperties(EntryProperties source) {
            Objects.requireNonNull(source, "source must not be null");
            this.maximumSize = source.maximumSize;
            this.expireAfterWrite = source.expireAfterWrite;
        }

        public long getMaximumSize() {
            return maximumSize;
        }

        public void setMaximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
        }

        public Duration getExpireAfterWrite() {
            return expireAfterWrite;
        }

        public void setExpireAfterWrite(Duration expireAfterWrite) {
            this.expireAfterWrite = expireAfterWrite;
        }
    }
}
