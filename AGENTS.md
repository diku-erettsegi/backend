# Backend agent útmutató

- Olvasd: README.md, API.md és ../database/docs/HANDOFF.md, ARCHITECTURE.md, DECISIONS.md.
- Java21 / Spring Boot3.5 / MariaDB11.4. A Flyway SQL itt a séma egyetlen forrása; V1/V2/V3 migrációt ne írd át. A következő változás V4.
- JPA entitást ne adj közvetlenül API válaszként; használj DTO-t. Szerepkört a SecurityConfig, erőforrás-láthatóságot a szolgáltatás ellenőriz.
- CSRF-et ne kapcsold ki. Ne válts localStorage JWT-re. Login után session/token rotáció maradjon meg.
- A local seed nem mehet élesbe; valódi titkot, feltöltést, személyes adatot vagy logfájlt ne commitolj.
- A letiltás/role/password módosítás authVersiont váltson. Az utolsó aktív admin védelmét meg kell tartani.
- Fájlnév nem útvonal. UUID, privát storage, hitelesített attachment letöltés. A DB és fájl tranzakciós eltéréseit kezeld.
- Módosítás után releváns mvn verify és teljes stack smoke. A tesztekhez Docker kell; kihagyást ne állíts sikeres tesztnek.
- API változáskor a frontend típusokat/űrlapokat és API.md-t együtt frissítsd. UTC Instant és Budapest LocalDateTime jelentését őrizd meg.
- Üzemeltetési változást a database repóban dokumentálj. Éles deploy jelenleg nincs aktiválva.
- Ár: Europe/Budapest szerveróra, időszaki25/15/0 + barátonként5 százalékpont, legfeljebb35. Meghívott barát flag nem duplázhatja ugyanazt a kapcsolatot. A mentett snapshotok nem számolhatók újra; a baráti jogosultságot admin manuálisan ellenőrzi.
- Kapacitás: csoportsorzár és READ_COMMITTED tranzakció. Csak ACCEPTED foglal helyet; minden státuszváltás ugyanazt a zárolási sorrendet használja. UUID-idempotencia és payload fingerprint maradjon meg.
- A 2026-10-21 felhasználói döntés szerint nincs automatikus e-mail. Ne állítsd vissza SMTP/workert; az érdeklődőket admin kezeli NEW/CONTACTED/COMPLETED állapotokkal. A V2 mail_outbox történeti, nem használt tábla.
