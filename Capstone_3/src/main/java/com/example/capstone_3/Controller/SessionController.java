package com.example.capstone_3.Controller;

import com.example.capstone_3.Api.ApiResponse;
import com.example.capstone_3.DtoIn.CreateSessionDtoIn;
import com.example.capstone_3.DtoIn.SessionDtoIn;
import com.example.capstone_3.Service.SessionService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/session")
public class SessionController {

    private final SessionService sessionService;

    @GetMapping("/get")
    public ResponseEntity<?> get() {
        return ResponseEntity.status(200).body(sessionService.get());
    }

    @PostMapping("/add")
    public ResponseEntity<?> add(@RequestBody @Valid SessionDtoIn sessionDtoIn) {
        sessionService.add(sessionDtoIn);
        return ResponseEntity.status(200).body(new ApiResponse("session added"));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody @Valid SessionDtoIn sessionDtoIn) {
        sessionService.update(id, sessionDtoIn);
        return ResponseEntity.status(200).body(new ApiResponse("session updated"));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        sessionService.delete(id);
        return ResponseEntity.status(200).body(new ApiResponse("session deleted"));
    }


    @PostMapping("/create/{offerId}")
    public ResponseEntity<?> createSession(@PathVariable Integer offerId, @RequestBody @Valid CreateSessionDtoIn createSessionDtoIn, HttpSession session) {
        return ResponseEntity.status(201).body(sessionService.createSession((Integer) session.getAttribute("accountId"), offerId, createSessionDtoIn));
    }

    @PostMapping("/{sessionId}/join/{exchangeId}")
    public ResponseEntity<?> joinSession(@PathVariable Integer sessionId, @PathVariable Integer exchangeId, HttpSession session) {
        sessionService.joinSession((Integer) session.getAttribute("accountId"), sessionId, exchangeId);
        return ResponseEntity.status(200).body(new ApiResponse("You joined the session successfully"));
    }

    @PutMapping("/{sessionId}/attendance/{exchangeId}")
    public ResponseEntity<?> updateAttendance(@PathVariable Integer sessionId, @PathVariable Integer exchangeId, @RequestBody Map<String, String> body, HttpSession session) {
        sessionService.updateAttendance((Integer) session.getAttribute("accountId"), sessionId, exchangeId, body.get("status"));
        return ResponseEntity.status(200).body(new ApiResponse("Attendance updated"));
    }

    @GetMapping("/offer/{offerId}")
    public ResponseEntity<?> getSessionsByOffer(@PathVariable Integer offerId) {
        return ResponseEntity.status(200).body(sessionService.getSessionsByOffer(offerId));
    }



    @GetMapping("/exchange/{exchangeId}")
    public ResponseEntity<?> getSessionsByExchange(@PathVariable Integer exchangeId, HttpSession session) {
        return ResponseEntity.status(200).body(sessionService.getSessionsByExchange((Integer) session.getAttribute("accountId"), exchangeId));
    }

    @PostMapping("/{sessionId}/zoom")
    public ResponseEntity<?> createZoomMeeting(@PathVariable Integer sessionId, HttpSession session) {

        return ResponseEntity.status(200).body(sessionService.createZoomMeeting((Integer) session.getAttribute("accountId"), sessionId));
    }

}
