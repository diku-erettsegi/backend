# DIKU Spring Boot API

## Jelentkezésindítás – 2026. november 13., V5

Új jelentkezési tájékoztató-verzió és egyszeri ízelítőkérés-verzió; `WITHDRAWN` státusz és külön lezárási időpontok. Elfogadáshoz írásos szerződésigazolás és legalább az igazolt képzési díj 20%-ának befizetése kell. A teljes határidős befizetéshez kötött korai kedvezmény, a változatlan eredeti ársnapshot és a külön laptopdíj megmaradt. Az admin az érdeklődő hozzájárulásának visszavonásakor azonnali adattörlést tud indítani.

A napi 03:15-ös budapesti adatmegőrzési feladat törli a lezárt, soha nem szerződött/fizetett jelentkezési szándékokat 90 nap után, az ízelítőket 90 nap után, a lejárt/visszavont tanulói hozzáférés fiókját 30 nap után és a műveleti napló 30 napnál régebbi sorait. A lezáratlan, el nem indult jelentkezési szándék a kurzus végén automatikusan lezárul; elintézetlen ízelítőkérés legfeljebb 90 napig marad. A lejárt kurzushoz tartozó opcionális szövegek is törlődnek, a szerződéses/pénzügyi rekordok megmaradnak. Az admin korábban rögzített szerződés/befizetés visszavonásával sem teszi automatikusan törölhetővé a pénzügyi bizonyítékot. A pontos határok, API mezők és a külön szervezői iratmegőrzés az [API.md](API.md) V5 szakaszában olvashatók.

`RETENTION_CRON` alapértéke `0 15 3 * * *`; `-` letiltja az ütemezést (integrációs tesztekben használjuk). A V5 csak hozzáad mezőket és történeti lezárási/védőjelöléseket, a migráció maga nem töröl adatot. A régi V1–V4 migráció változatlan. A nyilvános POST szerződés kompatibilis, SMTP vagy automatikus számlázás nem került be.

2026-11-14 ellenőrzés: Dockerben futó `mvn -q verify`, valódi Testcontainers MariaDB 11.4: **37 teszt, 0 hiba, 0 kihagyás** (11 ár-, 4 platform-, 22 regisztrációs/adatmegőrzési teszt). Az új esetek ellenőrzik a 20%-os küszöböt, visszamenőleges pénzügyi védelem megtartását, 90/30 napos határokat, újranyitást/újraengedélyezést, régi outbox törlését, a lejárt fiók sessionjének megszűnését és az admin ízelítőtörlés CSRF/szerepkör-védelmét. A korábbi Docker Desktop-megszakadás után egy visszavont, már jelentkezéshez nem kapcsolt fiók null projekciójának kezelése javítva; a teljes új futás sikeres. Éles aktiválás és teljes Compose-smoke állapota a közös HANDOFF/DEPLOY-STATUS dokumentumban követhető, nem ez a teszteredmény bizonyítja.

Az alábbi dátumozott részek korábbi állapotokat írnak le; az új megőrzési és előlegszabályokat ez a V5 szakasz felülírja.

## Novemberi működés – V4

Várólista (5 fő), kiskorú jelentkező szülői nyilatkozata, 20 dátumos szerkeszthető naptár, manuális szerződés/befizetés-igazolás, helycsere és csoporthoz kötött tananyag-hozzáférés. A tanuló csak saját elfogadott jelentkezésének csoportját láthatja a kurzus végét követő egy hónapig; közvetlen letöltés is ellenőrzött. Az eredeti díjajánlat változatlan; a teljes határidős befizetéshez és fizető barátokhoz kötött igazolt díj külön szerepel. API-részletek: API.md. A korábbi általános diákláthatóságot ez felülírja.

Az alábbi dátumozott leírások a korábbi állapot történetét is tartalmazzák.

Java 21 · Spring Boot 3.5.16 · Spring Security · JPA · Flyway · MariaDB 11.4. A három klón közül ez kezeli a csoportokat, jelentkezéseket és rögzített árakat, manuális programozási érdeklődéseket, felhasználókat, órákat, privát fájlokat és audit eseményeket.

2026-11-03 ellenőrzés: `mvn verify` Dockerben, valódi Testcontainers MariaDB 11.4-en: 29 teszt, 0 hiba, 0 kihagyás. V1/V2→V4 megőrzés, fizetési határidő, kiskorú, 5 fős párhuzamos várólista, csoportcsere és fájl/lejárat tesztelve. Teljes helyi Nginx→API→DB smoke is sikeres.

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

A diák csak elfogadott jelentkezéséhez rendelt csoportjának közzétett óráit látja, a kurzus végét követő egy hónapig. A jelentkezésből nem lesz automatikusan fiók. A fájlok UUID kulccsal, webrooton kívül tárolódnak; a letöltés jogosultságvizsgálatot és attachment/nosniff választ kap. A rendszer nem futtatja és nem csomagolja ki a tananyagot. A fájl és metaadat törlése tranzakcióhoz igazodik; sikertelen fizikai törlésnél elérhetetlen maradványfájl lehetséges, amire a log figyelmeztet.

Az alkalmazás memóriaalapú, egy példányos korlátozása: legfeljebb 8 sikertelen belépési kísérlet / 10 perc / IP, sikeres belépés törli a számlálót; jelentkezés 10 / óra / IP. Élesben megbízható proxy és további edge védelem szükséges. A listavégpontok az első kis kurzushoz lapozás nélküliek; auditból a legutóbbi 200 látszik.

## Csoportok, árak és érdeklődők

2027. január 9-én két szombati csoport indul: 09:00–13:00 és 14:00–18:00. Mindkettő 24 helyes, 20 alkalom × 4 darab 45 perces tanóra. A csoportok Flyway V2 migrációból kerülnek az adatbázisba, újraindulás nem állítja vissza őket. A 280000 Ft alapár 80 tanórára vonatkozik.

A szerver Budapest szerinti dátuma adja a 25%-os (2026. november 15. nap végéig), majd 15%-os (december 15. nap végéig) időszaki kedvezményt. Ehhez barátonként 5 százalékpont adódik, összesen legfeljebb 35%. Meghívott barátként 5% jár akkor is, ha a jelentkező maga nem hoz másik barátot; ugyanazt a kapcsolatot nem számoljuk kétszer (`max(friendCount,referredByFriend?1:0)`). Példák: 25+5=30% → 196000 Ft, 25+10=35% → 182000 Ft. Az admin manuálisan ellenőrzi a baráti kapcsolatot. A szerver az időszaki/baráti/végső százalékot, árat és tanóraárat menti; későbbi státuszváltás vagy árváltozás nem írja át a snapshotot. Új jelentkezésnél a laptopigény 30000 Ft külön mentett díj a teljes képzésre; nem automatikus készletfoglalás.

Új jelentkezésnél kötelező csoportot választani. Csak `ACCEPTED` státusz foglal helyet; a 24 fős limitet párhuzamos adminműveletnél is tranzakciós csoportsorzár védi. Minden nyilvános beküldés UUID idempotenciakulcsot kér: változatlan kérés ismétlése ugyanazt az eredményt adja, megváltoztatott tartalom ugyanazzal a kulccsal 409. A régi csoport nélküli rekordok megmaradnak.

A programozási érdeklődők külön táblába kerülnek, az admin `NEW` → `CONTACTED` → `COMPLETED` állapotot és megjegyzést kezelhet. A kapcsolatfelvétel manuális. Nincs automatikus e-mail, SMTP függőség, e-mail worker vagy hírlevél-feliratkozás. Érdeklődőűrlap-limit 10/óra/IP és 3/óra/e-mail-cím; a limitkulcs hash, a cím nem kerül logba.

## Teszt és séma

`mvn clean verify`: integrációs tesztek külön, Testcontainers által indított MariaDB-ben (futó Docker kell). A tesztek valódi Flyway migrációt/régi rekord megőrzést, Budapest éjfél árhatárokat, összeadódó kedvezményt és 35%-os plafont, párhuzamos kapacitás/idempotenciavédelmet, manuális érdeklődőkezelést, szerepköröket, CSRF-et, fájlláthatóságot, session visszavonást és utolsó admin védelmét ellenőrzik. A helyi fejlesztési adatbázist nem törlik. A teljes Nginx–API–DB teszt: `node scripts/smoke-test.mjs` a `database` könyvtárból, futó Compose mellett.

2026-10-21 ellenőrzés: 23/23 teszt sikeres, 0 kihagyás (11 árazási példa, 4 platform- és 8 regisztrációs integráció). A natív Budapest-időzónás Java futtatás külön hibát fogott: a csoport SQL TIME mezői hagyományos JDBC időkonverzióval egy órával eltolódtak. Ezek most explicit JDBC 4.2 `LOCAL_DATE` / `LOCAL_TIME` mappinget kapnak, így a 09:00 és 14:00 falióraidő a JVM időzónájától függetlenül megmarad. A mentett ár és `createdAt` ugyanabból a szerveroldali `Instant` értékből származik.

Migrációk: `src/main/resources/db/migration`. V1 kezdeti séma; V2 csoportok/árak/érdeklődők; V3 összeadódó kedvezmények és manuális érdeklődőállapot. V1/V2 változatlan, a következő változás V4. A V2 korábbi `mail_outbox` táblája történeti okból megmarad, de az alkalmazás már nem olvassa, írja vagy dolgozza fel; nincs kapcsolódó JPA entitás vagy worker. A V3 nem számol újra régi árajánlatokat. Hibernate `validate`; nincs automatikus sémaátírás.

API szerződés: [API.md](API.md). Közös architektúra/átadás: `../database/docs`. A `main` CI tesztel, majd GHCR image-et publikál; a VPS frissítését a database repó deploy szkriptje kezeli.

2026-10-24: az első Rackforest VPS-deploy sikeres, a frontend, backend, MariaDB és Cloudflare Tunnel egyaránt healthy. Az első backend GitHub Actions CI-ben 23/23 teszt sikeres. A belső Nginx → API útvonalon az adminbelépés, sessionrotáció, Secure/HttpOnly cookie, CSRF-védelem, 401/403 válaszok és kijelentkezés ellenőrzése sikeres. A bootstrap értékeket kivettük a környezetből, majd a backend újra healthy állapotba került.

A publikus domain névszerverváltását a felhasználó későbbre halasztotta; most kihagyjuk. A nyilvános HTTPS-útvonal ellenőrzése ezért későbbi lépés. Az automatizáció és az auth-/restore-próbák aktuális, ellenőrzött állapotának mindig a [DEPLOY-STATUS](../database/docs/DEPLOY-STATUS.md) az irányadó forrása.

Nincs e-mail-küldés, jelszó-visszaállítás vagy online fizetés. A V4 manuális pénzügyi igazolást és csoporthoz kötött, lejáró tananyag-hozzáférést biztosít.
