# Backend agent útmutató

- 2026-11-13: V5 launch privacy/retention. V1–V5 ne módosuljon, következő V6. API.md legfelső szakasz az irányadó: új WITHDRAWN, lezárási időpontok, legalább20% igazolt előleg, ADMIN+CSRF ízelítőtörlés, napi03:15Budapest RetentionService. Soha ne törölj szerződéses/pénzügyi jelentkezést a90napos szándékretentionnel; retentionProtected maradandó. Publicűrlap beküldési mezői változatlanok, verzió új rekordokra launch-2026-11-13/programming-preview-2026-11-13. Tests RETENTION_CRON=-, cleanupot kontrolláltórával hívják. Ütemezés visszaállításakor a technikai logok/mentések retentionje külön databasefeladat.

- Olvasd: README.md, API.md és ../database/docs/HANDOFF.md, ARCHITECTURE.md, DECISIONS.md.
- Java21 / Spring Boot3.5 / MariaDB11.4. A Flyway SQL itt a séma egyetlen forrása; V1/V2/V3/V4/V5 migrációt ne írd át. A következő változás V6.
- JPA entitást ne adj közvetlenül API válaszként; használj DTO-t. Szerepkört a SecurityConfig, erőforrás-láthatóságot a szolgáltatás ellenőriz.
- CSRF-et ne kapcsold ki. Ne válts localStorage JWT-re. Login után session/token rotáció maradjon meg.
- A local seed nem mehet élesbe; valódi titkot, feltöltést, személyes adatot vagy logfájlt ne commitolj.
- A letiltás/role/password módosítás authVersiont váltson. Az utolsó aktív admin védelmét meg kell tartani.
- Fájlnév nem útvonal. UUID, privát storage, hitelesített attachment letöltés. A DB és fájl tranzakciós eltéréseit kezeld.
- Módosítás után releváns mvn verify és teljes stack smoke. A tesztekhez Docker kell; kihagyást ne állíts sikeres tesztnek.
- API változáskor a frontend típusokat/űrlapokat és API.md-t együtt frissítsd. UTC Instant és Budapest LocalDateTime jelentését őrizd meg.
- Üzemeltetési változást a database repóban dokumentálj. 2026-10-24: az első VPS-deploy sikeres, mind a négy szolgáltatás healthy. Az automatizáció, auth- és restore-próbák aktuális állapotának forrása: ../database/docs/DEPLOY-STATUS.md. A publikus domain névszerverváltását a felhasználó későbbre halasztotta; most ne végezd el.
- Ár: Europe/Budapest szerveróra, időszaki25/15/0 + barátonként5 százalékpont, legfeljebb35. Meghívott barát flag nem duplázhatja ugyanazt a kapcsolatot. A mentett snapshotok nem számolhatók újra; a baráti jogosultságot admin manuálisan ellenőrzi.
- Kapacitás: csoportsorzár és READ_COMMITTED tranzakció. Csak ACCEPTED foglal helyet; minden státuszváltás ugyanazt a zárolási sorrendet használja. UUID-idempotencia és payload fingerprint maradjon meg.
- A 2026-10-21 felhasználói döntés szerint nincs automatikus e-mail. Ne állítsd vissza SMTP/workert; az érdeklődőket admin kezeli NEW/CONTACTED/COMPLETED állapotokkal. A V2 mail_outbox történeti, nem használt tábla.

- 2026-11-03: V4 várólista/korcsoport/finanszírozás/naptár/csoporthozzáférés. Eredeti quote megmarad, FinancialSummary külön igazolt díj; teljes határidős befizetés kell a korai kedvezményhez. Laptop 30000 Ft / teljes képzés, külön snapshot. EnrollmentService listánál és közvetlen letöltésnél is ellenőriz; csoport nélkül fail-closed. Proxyzott bean és lazy entitás mezőit ne olvasd közvetlenül másik beanből: getter vagy injektált szolgáltatás kell. Részletek: API.md, ../database/docs/LAUNCH-ANSWERS-2026-11.md.
