# DIKU Spring Boot API

Java 21 · Spring Boot 3.5.16 · Spring Security · JPA · Flyway · MariaDB 11.4. A három klón közül ez kezeli a csoportokat, jelentkezéseket és rögzített árakat, manuális programozási érdeklődéseket, felhasználókat, órákat, privát fájlokat és audit eseményeket.

## Futtatás

A legegyszerűbb a szomszédos `database` mappában: `docker compose up -d --build --wait`. A teljes oldal http://localhost:8088, az API http://localhost:8080 alatt érhető el. Nem szükséges telepített Java vagy Maven.

Natív fejlesztéshez Java 21, Maven 3.9+ és a DB kell:

```powershell
# database mappában:
docker compose up -d db --wait
# backend mappában:
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

Alap DB: `jdbc:mariadb://localhost:3308/diku`, `diku` / `diku_local_password`. A környezetváltozók felülírják. Health: `/actuator/health`; nincs publikus metrics/env/config végpont.

## Helyi adatok

Csak `local` profil és üres felhasználótábla mellett jön létre:

- Admin: `admin@diku.local` / `DikuAdmin2026!`
- Tanuló: `diak@diku.local` / `DikuDiak2026!`
- Egy egyértelműen jelölt mintaóra, valódi dátum vagy értekezletlink nélkül.

A seed nem állítja vissza a megváltoztatott jelszavakat. Élesben a `local` profil tiltott. Az adatbázist nem szabad tesztfiókokkal átvinni élesre.

## Konfiguráció

| Változó | Szerep |
| --- | --- |
| `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` | MariaDB kapcsolat |
| `SPRING_PROFILES_ACTIVE` | Helyben `local`, élesben `production` |
| `STORAGE_PATH` | Privát upload könyvtár, konténerben `/app/uploads` |
| `SECURE_COOKIE` | Lokálisan false, HTTPS mögött true |
| `SERVER_FORWARD_HEADERS_STRATEGY` | Éles megbízható proxy mögött `framework`, alapból none |
| `MAX_FILE_SIZE/MAX_REQUEST_SIZE` | Alap 20MB / 21MB; Nginx limitet is egyeztesd |
| `LOCAL_ADMIN_EMAIL/PASSWORD`, `LOCAL_STUDENT_EMAIL/PASSWORD` | Opcionális helyi seed felülírás |
| `BOOTSTRAP_ADMIN_EMAIL/NAME/PASSWORD` | Nem-local profilnál, üres user táblán egyszeri admin |

Bootstrap: valós e-mail, név, legalább 12 karakteres, legfeljebb 72 UTF-8 bájtos véletlen jelszó. Az első sikeres belépés után vedd ki a bootstrap értékeket az éles környezetből. Meglévő felhasználóknál a bootstrap nem módosít adatot. A titkot ne tedd repóba vagy parancssori argumentumba.

## Viselkedés és korlátok

Session cookie + CSRF védelem; a jelentkezéshez is CSRF token szükséges. A frontend `/api` proxyt használ. Belépéskor sessionazonosító és token váltás történik. Letiltás, e-mail-, jelszó- vagy szerepkörmódosítás után a régi session következő kérésre érvénytelen. Az utolsó aktív admin nem tiltható le vagy fokozható le. Jelszavak BCrypt hashként tárolódnak. A session egy backend példány memóriájában él; újraindítás után új belépés kell.

Minden aktív diák minden közzétett órát lát. A jelentkezésből nem lesz automatikusan fiók. A fájlok UUID kulccsal, webrooton kívül tárolódnak; a letöltés jogosultságvizsgálatot és attachment/nosniff választ kap. A rendszer nem futtatja és nem csomagolja ki a tananyagot. A fájl és metaadat törlése tranzakcióhoz igazodik; sikertelen fizikai törlésnél elérhetetlen maradványfájl lehetséges, amire a log figyelmeztet.

Az alkalmazás memóriaalapú, egy példányos korlátozása: legfeljebb 8 sikertelen belépési kísérlet / 10 perc / IP, sikeres belépés törli a számlálót; jelentkezés 10 / óra / IP. Élesben megbízható proxy és további edge védelem szükséges. A listavégpontok az első kis kurzushoz lapozás nélküliek; auditból a legutóbbi 200 látszik.

## Csoportok, árak és érdeklődők

2027. január 9-én két szombati csoport indul: 09:00–13:00 és 14:00–18:00. Mindkettő 24 helyes, 20 alkalom × 4 darab 45 perces tanóra. A csoportok Flyway V2 migrációból kerülnek az adatbázisba, újraindulás nem állítja vissza őket. A 280000 Ft alapár 80 tanórára vonatkozik.

A szerver Budapest szerinti dátuma adja a 25%-os (2026. november 15. nap végéig), majd 15%-os (december 15. nap végéig) időszaki kedvezményt. Ehhez barátonként 5 százalékpont adódik, összesen legfeljebb 35%. Meghívott barátként 5% jár akkor is, ha a jelentkező maga nem hoz másik barátot; ugyanazt a kapcsolatot nem számoljuk kétszer (`max(friendCount,referredByFriend?1:0)`). Példák: 25+5=30% → 196000 Ft, 25+10=35% → 182000 Ft. Az admin manuálisan ellenőrzi a baráti kapcsolatot. A szerver az időszaki/baráti/végső százalékot, árat és tanóraárat menti; későbbi státuszváltás vagy árváltozás nem írja át a snapshotot. A laptopigény nem jelent automatikus készletfoglalást vagy árazást.

Új jelentkezésnél kötelező csoportot választani. Csak `ACCEPTED` státusz foglal helyet; a 24 fős limitet párhuzamos adminműveletnél is tranzakciós csoportsorzár védi. Minden nyilvános beküldés UUID idempotenciakulcsot kér: változatlan kérés ismétlése ugyanazt az eredményt adja, megváltoztatott tartalom ugyanazzal a kulccsal 409. A régi csoport nélküli rekordok megmaradnak.

A programozási érdeklődők külön táblába kerülnek, az admin `NEW` → `CONTACTED` → `COMPLETED` állapotot és megjegyzést kezelhet. A kapcsolatfelvétel manuális. Nincs automatikus e-mail, SMTP függőség, e-mail worker vagy hírlevél-feliratkozás. Érdeklődőűrlap-limit 10/óra/IP és 3/óra/e-mail-cím; a limitkulcs hash, a cím nem kerül logba.

## Teszt és séma

`mvn clean verify`: integrációs tesztek külön, Testcontainers által indított MariaDB-ben (futó Docker kell). A tesztek valódi Flyway migrációt/régi rekord megőrzést, Budapest éjfél árhatárokat, összeadódó kedvezményt és 35%-os plafont, párhuzamos kapacitás/idempotenciavédelmet, manuális érdeklődőkezelést, szerepköröket, CSRF-et, fájlláthatóságot, session visszavonást és utolsó admin védelmét ellenőrzik. A helyi fejlesztési adatbázist nem törlik. A teljes Nginx–API–DB teszt: `node scripts/smoke-test.mjs` a `database` könyvtárból, futó Compose mellett.

2026-10-21 ellenőrzés: 23/23 teszt sikeres, 0 kihagyás (11 árazási példa, 4 platform- és 8 regisztrációs integráció). A natív Budapest-időzónás Java futtatás külön hibát fogott: a csoport SQL TIME mezői hagyományos JDBC időkonverzióval egy órával eltolódtak. Ezek most explicit JDBC 4.2 `LOCAL_DATE` / `LOCAL_TIME` mappinget kapnak, így a 09:00 és 14:00 falióraidő a JVM időzónájától függetlenül megmarad. A mentett ár és `createdAt` ugyanabból a szerveroldali `Instant` értékből származik.

Migrációk: `src/main/resources/db/migration`. V1 kezdeti séma; V2 csoportok/árak/érdeklődők; V3 összeadódó kedvezmények és manuális érdeklődőállapot. V1/V2 változatlan, a következő változás V4. A V2 korábbi `mail_outbox` táblája történeti okból megmarad, de az alkalmazás már nem olvassa, írja vagy dolgozza fel; nincs kapcsolódó JPA entitás vagy worker. A V3 nem számol újra régi árajánlatokat. Hibernate `validate`; nincs automatikus sémaátírás.

API szerződés: [API.md](API.md). Közös architektúra/átadás: `../database/docs`. A `main` CI tesztel, majd GHCR image-et publikál. A VPS frissítését a database repó készíti elő. Jelenleg nincs éles telepítés, e-mail-küldés, jelszó-visszaállítás, fizetés vagy a tananyagokhoz csoportonkénti hozzáférés. A csoport jelenleg jelentkezési/kapacitási adat, minden aktív diák továbbra is minden publikált órát lát.
