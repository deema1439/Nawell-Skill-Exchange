package com.example.capstone_3.Service;

import com.example.capstone_3.Api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Service
public class BrevoEmailService {

    private final RestClient restClient;
    private final String apiKey;
    private final String senderEmail;
    private final String senderName;

    public BrevoEmailService(@Value("${brevo.api-key}") String apiKey, @Value("${brevo.sender-email}") String senderEmail, @Value("${brevo.sender-name}") String senderName) {

        this.apiKey = apiKey;
        this.senderEmail = senderEmail;
        this.senderName = senderName;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(15000);

        this.restClient = RestClient.builder().baseUrl("https://api.brevo.com/v3").requestFactory(factory).build();
    }

    public void sendVerificationEmail(String recipientEmail, String verificationLink) {

        if (apiKey.isBlank() || senderEmail.isBlank()) {
            throw new ApiException("Email service is not configured");
        }

        Map<String, Object> body = Map.of(
                "sender", Map.of("name", senderName, "email", senderEmail),
                "to", List.of(Map.of("email", recipientEmail)),
                "subject", "Verify your Skill Exchange email",
                "textContent", "Click the link below to verify your account:\n\n" + verificationLink + "\n\nThis link expires in 24 hours. If you did not request it, ignore this email."
        );

        try {
            restClient.post().uri("/smtp/email").header("api-key", apiKey).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            throw new ApiException("Could not send verification email. Please try again later");
        }
    }

    // ارسال ايميل عام (نعطيه العنوان والنص) لاشعارات التوكنز
    public void sendEmail(String recipientEmail, String subject, String text) {
        if (apiKey.isBlank() || senderEmail.isBlank()) {
            throw new ApiException("Email service is not configured");
        }

        Map<String, Object> body = Map.of(
                "sender", Map.of(
                        "name", senderName,
                        "email", senderEmail
                ),
                "to", List.of(
                        Map.of("email", recipientEmail)
                ),
                "subject", subject,
                "textContent", text
        );

        try {
            restClient.post()
                    .uri("/smtp/email")
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientException exception) {
            throw new ApiException("Could not send email");
        }
    }

    public void sendSessionEmail(
            String recipientEmail,
            String subject,
            String message) {

        if (apiKey.isBlank() || senderEmail.isBlank()) {
            throw new ApiException("Email service is not configured");
        }

        Map<String, Object> body = Map.of(
                "sender", Map.of(
                        "name", senderName,
                        "email", senderEmail
                ),
                "to", List.of(
                        Map.of("email", recipientEmail)
                ),
                "subject", subject,
                "textContent", message
        );

        try {
            restClient.post()
                    .uri("/smtp/email")
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientException exception) {
            throw new ApiException(
                    "Could not send email. Please try again later"
            );
        }
    }
}
