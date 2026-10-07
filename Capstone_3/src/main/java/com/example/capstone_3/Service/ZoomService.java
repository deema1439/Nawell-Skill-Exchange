package com.example.capstone_3.Service;

import com.example.capstone_3.Api.ApiException;
import com.example.capstone_3.DtoOut.ZoomMeetingDtoOut;
import com.example.capstone_3.Model.Session;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ZoomService {

    private final ObjectMapper objectMapper;

    @Value("${zoom.account-id:}")
    private String accountId;

    @Value("${zoom.client-id:}")
    private String clientId;

    @Value("${zoom.client-secret:}")
    private String clientSecret;

    @Value("${zoom.host-email:}")
    private String hostEmail;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    private static final String TIMEZONE = "Asia/Riyadh";

    private String getAccessToken() {

        if (accountId == null || accountId.isBlank() || clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank() || hostEmail == null || hostEmail.isBlank()) {
            throw new ApiException("Zoom credentials or host email are not configured");
        }

        String credentials = clientId.trim() + ":" + clientSecret.trim();
        String authorization = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        String body = "grant_type=account_credentials&account_id=" + URLEncoder.encode(accountId.trim(), StandardCharsets.UTF_8);

        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://zoom.us/oauth/token")).timeout(Duration.ofSeconds(30)).header("Authorization", "Basic " + authorization).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 429) {
                throw new ApiException("Too many Zoom requests. Please try again later");
            }

            if (response.statusCode() != 200) {
                throw new ApiException("Zoom authentication failed. Check the credentials and app activation");
            }

            JsonNode data = objectMapper.readTree(response.body());
            JsonNode token = data == null ? null : data.get("access_token");

            if (token == null || !token.isTextual() || token.asText().isBlank()) {
                throw new ApiException("Zoom did not return a valid access token");
            }

            return token.asText();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("Zoom authentication was interrupted");
        } catch (IOException e) {
            throw new ApiException("Unable to connect to Zoom authentication");
        }
    }

    public ZoomMeetingDtoOut createMeeting(Session session) {

        if (session == null || session.getId() == null) {
            throw new ApiException("Please save the session before creating a Zoom meeting");
        }

        if (!"ONLINE".equals(session.getMode()) || !"SCHEDULED".equals(session.getStatus())) {
            throw new ApiException("Only scheduled online sessions can have a Zoom meeting");
        }

        if (session.getZoomMeetingId() != null) {
            throw new ApiException("This session already has a Zoom meeting");
        }

        if (session.getTitle() == null || session.getTitle().isBlank() || session.getTitle().length() > 150 || session.getScheduledAt() == null || session.getDurationMinutes() == null || session.getDurationMinutes() < 1) {
            throw new ApiException("Session title, date, or duration is invalid");
        }

        if (!session.getScheduledAt().atZone(ZoneId.of(TIMEZONE)).toInstant().isAfter(java.time.Instant.now())) {
            throw new ApiException("The session date must be in the future");
        }

        String accessToken = getAccessToken();
        String encodedEmail = URLEncoder.encode(hostEmail.trim(), StandardCharsets.UTF_8).replace("+", "%20");

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("waiting_room", true);
        settings.put("join_before_host", false);
        settings.put("use_pmi", false);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("topic", session.getTitle().trim());
        input.put("type", 2);
        input.put("start_time", session.getScheduledAt().atZone(ZoneId.of(TIMEZONE)).toInstant().toString());
        input.put("duration", session.getDurationMinutes());
        input.put("timezone", TIMEZONE);
        input.put("settings", settings);

        try {
            String body = objectMapper.writeValueAsString(input);

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://api.zoom.us/v2/users/" + encodedEmail + "/meetings")).timeout(Duration.ofSeconds(45)).header("Authorization", "Bearer " + accessToken).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401) {
                throw new ApiException("Zoom access token was rejected");
            }

            if (response.statusCode() == 403) {
                throw new ApiException("Zoom denied meeting creation. Check the app scopes and host permissions");
            }

            if (response.statusCode() == 404) {
                throw new ApiException("Zoom host was not found in the connected account");
            }

            if (response.statusCode() == 429) {
                throw new ApiException("Zoom request limit reached. Please try again later");
            }

            if (response.statusCode() != 201) {
                throw new ApiException("Unable to create the Zoom meeting. Check Zoom before retrying. Status: " + response.statusCode());
            }

            JsonNode data = objectMapper.readTree(response.body());

            if (data == null) {
                throw new ApiException("Zoom returned an empty response. Check Zoom before retrying");
            }

            JsonNode meetingId = data.path("id");
            JsonNode meetingLink = data.path("join_url");

            if (!meetingId.isIntegralNumber() || !meetingId.canConvertToLong() || meetingId.longValue() <= 0 || !meetingLink.isTextual() || meetingLink.asText().isBlank() || meetingLink.asText().length() > 2000) {
                throw new ApiException("Zoom returned invalid meeting details. Check Zoom before retrying");
            }

            ZoomMeetingDtoOut result = new ZoomMeetingDtoOut();
            result.setSessionId(session.getId());
            result.setMeetingId(meetingId.longValue());
            result.setTitle(session.getTitle());
            result.setScheduledAt(session.getScheduledAt());
            result.setDurationMinutes(session.getDurationMinutes());
            result.setTimezone(TIMEZONE);
            result.setMeetingLink(meetingLink.asText());

            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("Zoom meeting creation was interrupted. Check Zoom before retrying");
        } catch (IOException e) {
            throw new ApiException("Unable to read the Zoom response. Check Zoom before retrying");
        }
    }

    public void deleteMeeting(Long meetingId) {

        if (meetingId == null || meetingId <= 0) {
            throw new ApiException("Invalid Zoom meeting ID");
        }

        String accessToken = getAccessToken();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.zoom.us/v2/meetings/" + meetingId))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + accessToken)
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 204 || response.statusCode() == 404) {
                return;
            }

            throw new ApiException(
                    "Could not delete Zoom meeting " + meetingId
                            + ". Status: " + response.statusCode()
                            + ". Check Zoom before retrying"
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                    "Zoom deletion was interrupted. Check meeting " + meetingId
            );

        } catch (IOException e) {
            throw new ApiException(
                    "Could not confirm Zoom deletion. Check meeting " + meetingId
            );
        }
    }
}