package hu.diku.api;

import static hu.diku.api.ApiDtos.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
class FileService {
    private final PlatformService service;
    private final Path root;
    private final LessonRepository lessons;
    private final LessonFileRepository files;
    FileService(PlatformService service, LessonRepository lessons, LessonFileRepository files, @Value("${diku.storage-path}") String location) throws IOException {
        this.service=service; this.lessons=lessons; this.files=files; root=Path.of(location).toAbsolutePath().normalize(); Files.createDirectories(root);
    }
    record Download(Path path, String name, long size) {}
    private Path path(String key) {
        if(!key.matches("[a-f0-9-]{36}")) throw PlatformService.missing();
        Path result=root.resolve(key).normalize();
        if(!result.getParent().equals(root)) throw PlatformService.missing();
        return result;
    }
    @Transactional(rollbackFor=IOException.class) FileDto upload(long lessonId, MultipartFile upload, String actor) throws IOException {
        var lesson=lessons.findById(lessonId).orElseThrow(PlatformService::missing);
        if(upload.isEmpty()) throw PlatformService.invalid("Üres fájl nem tölthető fel.");
        String name=upload.getOriginalFilename();
        if(name==null || name.isBlank()) name="tananyag";
        name=name.replaceAll("[\\p{Cntrl}\\\\/:]","_");
        if(name.length()>255) throw PlatformService.invalid("A fájlnév legfeljebb 255 karakter lehet.");
        var item=new LessonFile(); item.lesson=lesson; item.originalName=name;
        item.storageName=UUID.randomUUID().toString(); item.size=upload.getSize();
        item.contentType="application/octet-stream";
        Path target=path(item.storageName);
        // A rollback removes only this newly generated object, never a user-supplied path.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) { if(status!=STATUS_COMMITTED) removeOrphan(target); }
        });
        try(var input=upload.getInputStream()) { Files.copy(input,target); }
        files.saveAndFlush(item);
        service.audit(actor,"FILE_UPLOADED","file:"+item.id);
        return FileDto.of(item);
    }
    @Transactional(readOnly=true) Download download(long id, boolean admin) {
        var item=files.findById(id).orElseThrow(PlatformService::missing);
        if(!admin && !item.lesson.isPublished()) throw PlatformService.missing();
        Path stored=path(item.storageName);
        if(!Files.isRegularFile(stored,LinkOption.NOFOLLOW_LINKS)) throw PlatformService.missing();
        return new Download(stored,item.originalName,item.size);
    }
    private void removeAfterCommit(Path target) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { removeOrphan(target); }
        });
    }
    private static void removeOrphan(Path target) {
        try { Files.deleteIfExists(target); }
        catch(IOException e) { LoggerFactory.getLogger(FileService.class).warn("A storage object needs cleanup: {}",target.getFileName()); }
    }
    @Transactional void deleteFile(long id,String actor) {
        var item=files.findById(id).orElseThrow(PlatformService::missing);
        removeAfterCommit(path(item.storageName)); files.delete(item);
        service.audit(actor,"FILE_DELETED","file:"+id);
    }
    @Transactional void deleteLesson(long id,String actor) {
        var lesson=lessons.findById(id).orElseThrow(PlatformService::missing);
        for(var item:files.findByLessonIdOrderByIdAsc(id)) {
            removeAfterCommit(path(item.storageName)); files.delete(item);
        }
        files.flush(); lessons.delete(lesson);
        service.audit(actor,"LESSON_DELETED","lesson:"+id);
    }
}
