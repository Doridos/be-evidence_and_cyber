package cz.fel.cvut.beevidence_and_cyber.service;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.sendmail.SendMailPostRequestBody;
import cz.fel.cvut.beevidence_and_cyber.config.NotificationProperties;
import cz.fel.cvut.beevidence_and_cyber.dao.DetectionFinding;
import cz.fel.cvut.beevidence_and_cyber.dao.EndpointDevice;
import cz.fel.cvut.beevidence_and_cyber.enumeration.SeverityLevelEnum;
import cz.fel.cvut.beevidence_and_cyber.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Sends email notifications for HIGH-severity detection findings
 * via the Microsoft Graph API (Office 365 / Exchange Online).
 *
 * <p>Recipients are resolved dynamically from system users that have the
 * <b>ADMIN</b> role assigned and have a non-blank email address configured.
 *
 * <p>Requires Azure AD application registration with the
 * <b>Mail.Send</b> Application permission granted (not delegated).
 *
 * <p>Email is sent asynchronously so it never blocks the detection pipeline.
 */
@Service
@Slf4j
public class EmailNotificationService {

    private static final String GRAPH_SCOPE = "https://graph.microsoft.com/.default";
    private static final String ADMIN_ROLE_CODE = "ADMIN";
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private final NotificationProperties properties;
    private final UserRepository userRepository;

    /**
     * Graph client is created lazily on first use and reused for all subsequent calls.
     * The Azure Identity SDK handles OAuth2 token acquisition and renewal internally.
     */
    private volatile GraphServiceClient graphServiceClient;

    public EmailNotificationService(NotificationProperties properties, UserRepository userRepository) {
        this.properties = properties;
        this.userRepository = userRepository;
    }

    /**
     * Sends an email notification for a newly created HIGH-severity finding.
     *
     * <p>Admin recipient emails are resolved synchronously (on the calling thread,
     * within the active transaction) before handing off to an async thread —
     * this avoids any lazy-loading issues in the async context.
     *
     * @param finding     the newly created finding
     * @param device      the device the finding belongs to
     * @param title       the finding title
     * @param description the finding description
     */
    public void notifyNewHighFinding(DetectionFinding finding,
                                     EndpointDevice device,
                                     String title,
                                     String description) {
        if (!properties.isEnabled()) {
            log.debug("Email notifications disabled — skipping finding={}", finding.getId());
            return;
        }
        if (finding.getSeverity() != SeverityLevelEnum.HIGH) {
            return;
        }

        // Resolve admin recipients synchronously while still in the JPA session / transaction.
        List<String> recipients = userRepository.findEnabledUsersWithEmail(ADMIN_ROLE_CODE)
                .stream()
                .map(user -> user.getEmail().trim())
                .filter(email -> !email.isBlank())
                .distinct()
                .toList();

        if (recipients.isEmpty()) {
            log.warn(
                    "No ADMIN users with email found — email notification skipped. finding={}",
                    finding.getId()
            );
            return;
        }

        log.info(
                "Resolved {} ADMIN recipient(s) for finding={}. recipients={}",
                recipients.size(),
                finding.getId(),
                recipients
        );

        // Capture primitive/immutable values for the async lambda —
        // do NOT pass JPA-managed entities across thread boundaries.
        String findingId   = finding.getId() != null ? finding.getId().toString() : "-";
        String ruleCode    = finding.getRule() != null ? finding.getRule().getCode() : "-";
        String ruleName    = finding.getRule() != null ? finding.getRule().getName() : "-";
        SeverityLevelEnum severity  = finding.getSeverity();
        LocalDateTime firstSeenAt  = finding.getFirstSeenAt();
        String hostname    = device.getHostname();
        String fqdn        = device.getFqdn();
        String primaryIp   = device.getPrimaryIp();

        CompletableFuture.runAsync(() -> {
            try {
                sendEmail(findingId, ruleCode, ruleName, severity, firstSeenAt,
                        hostname, fqdn, primaryIp, title, description, recipients);
            } catch (Exception exception) {
                log.error(
                        "Failed to send email notification for finding={}. Error: {}",
                        findingId,
                        exception.getMessage(),
                        exception
                );
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal
    // ─────────────────────────────────────────────────────────────────────────

    private void sendEmail(String findingId,
                           String ruleCode,
                           String ruleName,
                           SeverityLevelEnum severity,
                           LocalDateTime firstSeenAt,
                           String hostname,
                           String fqdn,
                           String primaryIp,
                           String title,
                           String description,
                           List<String> recipients) {
        GraphServiceClient client = getOrCreateGraphClient();

        String subject = "[BEZPECNOSTNI NALEZ] " + severity.name()
                + ": " + safeValue(title)
                + " | " + safeValue(hostname);

        String htmlContent = buildHtmlBody(ruleCode, ruleName, severity, firstSeenAt,
                hostname, fqdn, primaryIp, title, description);

        List<Recipient> toRecipients = new ArrayList<>();
        for (String address : recipients) {
            EmailAddress emailAddress = new EmailAddress();
            emailAddress.setAddress(address);
            Recipient recipient = new Recipient();
            recipient.setEmailAddress(emailAddress);
            toRecipients.add(recipient);
        }

        ItemBody body = new ItemBody();
        body.setContentType(BodyType.Html);
        body.setContent(htmlContent);

        Message message = new Message();
        message.setSubject(subject);
        message.setBody(body);
        message.setToRecipients(toRecipients);

        SendMailPostRequestBody requestBody = new SendMailPostRequestBody();
        requestBody.setMessage(message);
        requestBody.setSaveToSentItems(false);

        log.info(
                "Sending HIGH finding email via Graph API. findingId={}, ruleCode={}, hostname={}, recipients={}",
                findingId, ruleCode, hostname, recipients
        );

        client.users().byUserId(properties.getSender()).sendMail().post(requestBody);

        log.info("Email notification sent successfully. findingId={}", findingId);
    }

    /**
     * Lazily initialises and returns the Graph client (thread-safe double-checked locking).
     */
    private GraphServiceClient getOrCreateGraphClient() {
        if (graphServiceClient == null) {
            synchronized (this) {
                if (graphServiceClient == null) {
                    log.info(
                            "Initialising Microsoft Graph client. tenantId={}, clientId={}",
                            properties.getTenantId(),
                            mask(properties.getClientId())
                    );
                    ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                            .tenantId(properties.getTenantId())
                            .clientId(properties.getClientId())
                            .clientSecret(properties.getClientSecret())
                            .build();
                    graphServiceClient = new GraphServiceClient(credential, GRAPH_SCOPE);
                }
            }
        }
        return graphServiceClient;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HTML email template
    // ─────────────────────────────────────────────────────────────────────────

    private String buildHtmlBody(String ruleCode,
                                  String ruleName,
                                  SeverityLevelEnum severity,
                                  LocalDateTime firstSeenAt,
                                  String hostname,
                                  String fqdn,
                                  String primaryIp,
                                  String title,
                                  String description) {
        String severityColor = "#c0392b";

        return "<!DOCTYPE html><html><head><meta charset='UTF-8'></head>"
                + "<body style='font-family:Arial,sans-serif;font-size:14px;color:#333;margin:0;padding:0;'>"
                + "<div style='max-width:640px;margin:20px auto;border:1px solid #ddd;"
                + "border-radius:6px;overflow:hidden;'>"

                // Header bar
                + "<div style='background:" + severityColor + ";padding:16px 24px;'>"
                + "<h2 style='margin:0;color:#fff;font-size:18px;'>Bezpecnostni nalez &mdash; "
                + escapeHtml(severity.name()) + "</h2>"
                + "</div>"

                // Table with finding details
                + "<div style='padding:24px;'>"
                + "<table style='width:100%;border-collapse:collapse;'>"
                + row("Nazev nalezu",   escapeHtml(safeValue(title)))
                + row("Popis",          escapeHtml(safeValue(description)))
                + row("Pravidlo",       escapeHtml(safeValue(ruleCode)) + " &ndash; " + escapeHtml(safeValue(ruleName)))
                + row("Zavaznost",
                        "<span style='color:" + severityColor + ";font-weight:bold;'>"
                                + escapeHtml(severity.name()) + "</span>")
                + row("Zarizeni",       escapeHtml(safeValue(hostname)))
                + row("FQDN",           escapeHtml(safeValue(fqdn)))
                + row("IP adresa",      escapeHtml(safeValue(primaryIp)))
                + row("Cas detekce",    escapeHtml(formatDateTime(firstSeenAt)))
                + "</table>"
                + "</div>"

                // Footer
                + "<div style='background:#f5f5f5;padding:12px 24px;font-size:12px;color:#888;"
                + "border-top:1px solid #eee;'>"
                + "Tato zprava byla automaticky vygenerovana systemem Evidence &amp; Cyber. "
                + "Neodpovidejte na tento email."
                + "</div>"
                + "</div></body></html>";
    }

    private String row(String label, String value) {
        return "<tr>"
                + "<td style='padding:8px 4px;font-weight:bold;width:160px;vertical-align:top;"
                + "color:#555;border-bottom:1px solid #f0f0f0;'>" + label + ":</td>"
                + "<td style='padding:8px 4px;border-bottom:1px solid #f0f0f0;'>" + value + "</td>"
                + "</tr>";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "-" : DATE_FORMATTER.format(dateTime);
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "-";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String safeValue(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private String mask(String value) {
        if (value == null || value.length() < 8) {
            return "***";
        }
        return value.substring(0, 4) + "****";
    }
}
