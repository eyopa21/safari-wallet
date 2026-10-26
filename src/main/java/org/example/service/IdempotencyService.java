package org.example.service;

import org.example.domain.Now;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.api.ApiException;
import org.example.api.ErrorCode;
import org.example.domain.IdempotencyRecord;
import org.example.domain.IdempotencyRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.function.Supplier;

// Same key + same body: the stored response comes back, the action does not run again.
// Same key + different body: 409 IDEMPOTENCY_CONFLICT. The record is written in the same transaction as the
// action, so a concurrent duplicate rolls back entirely and then replays the winner.
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRepository records;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    public <T> ResponseEntity<String> run(String key, String endpoint, Object request, HttpStatus status,
                                          Class<T> responseType, Supplier<T> action) {
        String hash = hash(endpoint, request);
        IdempotencyRecord existing = records.findById(key).orElse(null);
        if (existing != null) {
            return replay(existing, endpoint, hash);
        }
        try {
            String body = tx.execute(s -> {
                String b = toJson(action.get());
                records.save(new IdempotencyRecord(key, endpoint, hash, status.value(), b, Now.instant()));
                return b;
            });
            return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
        } catch (DataIntegrityViolationException e) {
            // lost the race against a request with the same key
            IdempotencyRecord winner = records.findById(key)
                    .orElseThrow(() -> new ApiException(ErrorCode.REQUEST_IN_PROGRESS, "a request with this Idempotency-Key is still running"));
            return replay(winner, endpoint, hash);
        }
    }

    private ResponseEntity<String> replay(IdempotencyRecord r, String endpoint, String hash) {
        if (!r.sameRequest(endpoint, hash)) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT, "Idempotency-Key was already used with a different body");
        }
        return ResponseEntity.status(r.getStatusCode()).contentType(MediaType.APPLICATION_JSON).body(r.getResponseBody());
    }

    private String hash(String endpoint, Object request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((endpoint + "\n" + json.writeValueAsString(request)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String toJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
