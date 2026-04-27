package cz.fel.cvut.beevidence_and_cyber.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for email notifications via Microsoft Graph API (Office 365).
 *
 * Required Azure app registration permissions: Mail.Send (Application)
 *
 * Example application.properties:
 *   app.notification.enabled=true
 *   app.notification.client-id=<azure-app-client-id>
 *   app.notification.client-secret=<azure-app-client-secret>
 *   app.notification.tenant-id=<azure-tenant-id>
 *   app.notification.sender=helpdesk@ulz.cz
 *
 * Recipients are resolved dynamically from system users with the ADMIN role.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.notification")
public class NotificationProperties {

    /** Master switch — when false, no emails are sent. */
    private boolean enabled = false;

    /** Azure AD application (client) ID. */
    private String clientId = "";

    /** Azure AD application client secret. */
    private String clientSecret = "";

    /** Azure AD tenant ID. */
    private String tenantId = "";

    /** Office 365 mailbox address used as the sender (From). Must have Mail.Send permission. */
    private String sender = "";
}
