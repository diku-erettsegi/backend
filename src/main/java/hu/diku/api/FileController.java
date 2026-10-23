package hu.diku.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api")
class FileController {
    private final FileService files;
    FileController(FileService files) { this.files=files; }
    @PostMapping("/admin/lessons/{id}/files") @ResponseStatus(HttpStatus.CREATED)
    ApiDtos.FileDto upload(@PathVariable long id,@RequestParam("file") MultipartFile file, Authentication auth) throws IOException {
        return files.upload(id,file,auth.getName());
    }
    @GetMapping("/files/{id}/download") ResponseEntity<Resource> download(@PathVariable long id, Authentication auth) {
        boolean admin=auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        var item=files.download(id,admin);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(item.size())
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(item.name(),StandardCharsets.UTF_8).build().toString())
            .header(HttpHeaders.CACHE_CONTROL,"no-store").body(new FileSystemResource(item.path()));
    }
    @DeleteMapping("/admin/files/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable long id, Authentication auth) { files.deleteFile(id,auth.getName()); }
    @DeleteMapping("/admin/lessons/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeLesson(@PathVariable long id, Authentication auth) { files.deleteLesson(id,auth.getName()); }
}
