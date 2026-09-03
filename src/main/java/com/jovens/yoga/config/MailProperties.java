package com.jovens.yoga.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {

    /** Never a secret — this is the "From" address shown to recipients, not the SMTP login. */
    private String fromAddress = "no-reply@jovensyoga.com";
    private String fromName = "Jovens Yoga";

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }
}
