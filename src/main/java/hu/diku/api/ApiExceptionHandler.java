package hu.diku.api;

import java.util.Map;
import java.util.TreeMap;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation(MethodArgumentNotValidException e) {
        Map<String,String> errors=new TreeMap<>();
        e.getBindingResult().getFieldErrors().forEach(error -> errors.put(error.getField(),"Ellenőrizd a mező értékét."));
        return ResponseEntity.badRequest().body(Map.of("message","Ellenőrizd a megadott adatokat.","errors",errors));
    }
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"A kérés nem teljesíthető.":e.getReason()));
    }
    @ExceptionHandler(AuthenticationException.class) ResponseEntity<?> authentication() {
        return ResponseEntity.status(401).body(Map.of("message","Hibás e-mail-cím vagy jelszó."));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class) ResponseEntity<?> invalid() {
        return ResponseEntity.badRequest().body(Map.of("message","Érvénytelen kérésformátum."));
    }
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict() {
        return ResponseEntity.status(409).body(Map.of("message","Az adat már létezik vagy más adatok hivatkoznak rá."));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<?> tooLarge() {
        return ResponseEntity.status(413).body(Map.of("message","A fájl meghaladja az engedélyezett feltöltési méretet."));
    }
}
