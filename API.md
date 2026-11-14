# API szerződés – csoportok, összevont kedvezmények és manuális érdeklődőkezelés

## 2026-11-13 – jelentkezésindítás, V5

Az admin ízelítő DTO a `privacyAcceptedAt` és `privacyVersion` mezőket is visszaadja; ezek az admin részletezőben megjelennek. A nyilvános beküldési válasz továbbra is csak a visszaigazolást tartalmazza.

Ez a szakasz felülírja a korábbi pozitív befizetésre és piszkozat adatkezelési verzióra vonatkozó szabályokat. A meglévő nyilvános POST payloadok kompatibilisek; nincs online fizetés vagy levélküldés.

- Új jelentkezés `privacyVersion`: `launch-2026-11-13`; új ízelítőkérés: `programming-preview-2026-11-13`. Meglévő rekord verziója és időpontjai nem változnak. Az alkalmazás mezője a tájékoztató megismerését, az ízelítőé a kért egyszeri megkeresés hozzájárulását rögzíti; nincs marketingfeliratkozás.
- `Application.status` új értéke `WITHDRAWN` (visszavont), a státuszváltó PATCH ezt fogadja. Új admin DTO mező `closedAt: Instant|null`: első `REJECTED`/`WITHDRAWN` lezárás szerverideje. A lezárt rekord megjegyzésének módosítása nem tolja ki a határidőt; újranyitás nullázza.
- `FinancialSummary.minimumAdvanceTotal: Integer|null`: az igazolt képzési díj 20%-a, egész forintra felfelé kerekítve. A laptopdíj külön marad. Új elfogadáshoz írásos szerződésigazolás, pozitív befizetés és legalább ez az összeg szükséges. A pénzügyi módosítás nem hagyhat elfogadott rekordot a küszöb alatt. Korábbi ismeretlen alapdíjú rekordnál a küszöb null, továbbra is kézzel igazolt szerződés és pozitív befizetés kell. A korai kedvezmény csak a teljes összeg határidős befizetésekor igazolható, az eredeti ajánlat nem változik.
- `ProgrammingInterest.completedAt: Instant|null`: a lezárás első szerverideje, változatlan lezárt státusz melletti szerkesztés nem tolja ki; újranyitás nullázza. `COMPLETED` adminisztratív lezárást jelöl, nem bizonyít automatikus levélküldést.
- Új `DELETE /api/admin/programming-interests/{id}`: ADMIN + CSRF, siker 204, hiányzó rekord 404. A hozzájárulás visszavonásakor azonnal törli az érdeklődő rekordját és az esetleges történeti outbox kapcsolódó sorát. A műveleti napló csak az admin azonosítóját, `PROGRAMMING_INTEREST_DELETED` műveletet és `interest:id` célt őriz, a törölt nevet/címet nem.

### Automatikus adatmegőrzés

A `RetentionService` napi **03:15 Europe/Budapest** időpontban fut. `RETENTION_CRON` felülírhatja; `-` teljesen letiltja, ezért ez élesben kizárólag tudatos üzemeltetési döntés lehet. Futás után csak darabszámok kerülnek technikai naplóba. Nincs publikus vagy admin „futtasd most” végpont. Az egyes rekordok külön tranzakcióban, újraellenőrzött állapot és a meglévő csoport → jelentkezés → fiók sorrendű sorzárak mellett módosulnak.

| Adatkör | Szabály |
| --- | --- |
| Visszavont/elutasított, soha nem szerződött/fizetett jelentkezési szándék | `closedAt` után 90 nap, a következő napi futás törli. Kapcsolt fiók esetén nem töröl. |
| Elmaradt, nyitott jelentkezési szándék | A kurzus vége után a NEW/CONTACTED/WAITLISTED rekordot lezárja (`REJECTED`, `APPLICATION_EXPIRED` audit), ha soha nem volt hozzá szerződés vagy befizetés. Innen indul a 90 nap. |
| Szerződéses/pénzügyi jelentkezési adatok | **Nem** tartoznak a 90 napos automatikus törléshez. Bármely igazolt szerződés vagy pozitív befizetés maradandó belső védőjelölést állít; későbbi admin korrekció nem törli azt. A jogszabály szerinti törzsadat-, szerződés- és iratmegőrzés külön szervezői feladat. |
| Ízelítőkérés | Lezárt: `completedAt` + 90 nap; nyitott: `createdAt` + 90 nap. Visszavonási kérelemnél admin DELETE azonnal, nem kell kivárni a határidőt. |
| Tanulói fiók | Természetes hozzáférési időszak vége után 30 nap elteltével, vagy kifejezett hozzáférés-visszavonás/elfogadás-visszavonás után 30 nappal törlődik. Újbóli jogosultságadás megszünteti a visszavonás miatti törlési határidőt. Admin és soha hozzáféréssel nem rendelkező fiók nem törlődik automatikusan. |
| Szabad jelentkezési szövegek | A kurzusvége + 1 hónap hozzáférés + 30 nap után `message`, `notes`, `referralNote` kiürül. Pénzügyi mezők, név/törzsadat és eredeti ársnapshot megmarad. A felület nem külön pedagógiai napló; szerződést/bizonylatot ne a szabad megjegyzésben tárolj. |
| Admin műveleti napló | 30 nap; a biztonsági mentésekben lévő másolatokra a külön 30 napos mentéskezelés vonatkozik. |

Az archivált szerződések, számlák, felnőttképzési nyilvántartások és jogi igények teljes életciklusát ez az alkalmazás nem kezeli. Kézi megőrzés és jogszerű törlés a szervező feladata. A V5 migráció nem töröl történeti adatot: ismeretlen régi lezárási időnél a migráció idejét rögzíti. Törölt rekord idempotenciakulcsa is törlődik; azonos kulcs megőrzési időn túli újraküldése új kérésnek számíthat. Visszaállítás után a megőrzési feladatot és időközbeni törlési kérelmeket is újra alkalmazni kell.

## 2026-11-03 – V4 szerződés (az alábbi októberi leírás módosításai)

- Új jelentkezés: `ageGroup: ADULT | MINOR_14_17` kötelező. Kiskorúnál `parentName` és `guardianConfirmed:true` kell. `waitlistRequested:true` kifejezett várólista-kérés, csak betelt csoportnál kerül `WAITLISTED` állapotba. Korábbi azonos idempotens kérés az új mezők nélkül is visszaolvasható.
- Course kiegészítés: `waitlistCapacity`, `waitlistCount`, `availableWaitlistSeats`, `waitlistOpen`, `registrationClosesOn`, `endDate`, `acceptingApplications` (nyers admin kapcsoló), `sessionDates` (ISO dátumtömb). `registrationOpen` a tényleges szabad helyet és indulás+14 nap határidőt is figyelembe veszi. Betelt csoporthoz legfeljebb 5 várólistás; státuszváltás is zárolt.
- Receipt kiegészítés: `status`, `baseTotal`, `laptopRentalFee`. Új laptopigény 30000 Ft a teljes képzésre, nélküle 0, régi rekordnál null. `quotedTotal` továbbra is kizárólag képzési ajánlat, a bérlet külön hozzáadandó. Az ajánlat feltételes, nem fizetési visszaigazolás.
- Application kiegészítés: `ageGroup`, `guardianConfirmed`, `finance`. Finance: `{contractConfirmed,paidTrainingTotal,fullPaymentDate,verifiedFriendCount,discountPercent,periodDiscountPercent,trainingTotal,laptopRentalFee,trainingBalance}`. Régi alapdíj nélküli rekordnál az összeg és egyenleg null.
- `PUT /admin/applications/{id}/finance`: `{contractConfirmed,paidTrainingTotal,fullPaymentDate?,verifiedFriendCount}`. Összeg egész forint 0–10000000; igazolt barát 0–10. A teljes befizetési dátum jelentkezés és mai Budapest nap között, csak a teljes számított díj beérkezésekor. A korai kedvezményt a beküldési és teljes befizetési dátum együtt határozza meg. Befizetett barátokat admin igazolja; plafon 35%. Minden mentés auditált. A történeti quote változatlan.
- `PATCH /admin/applications/{id}`: új ACCEPTED státusz előtt szerződésigazolás és pozitív képzési befizetés szükséges. Részletfizetés elfogadható. Már elfogadott rekordnál pénzügyi igazolás visszavonásához előbb státuszváltás kell.
- `PUT /admin/applications/{id}/course`: `{courseId,swapApplicationId?}`. Szabad célhely vagy két elfogadott jelentkezés atomikus helycseréje. Növekvő csoport- és alkalmazássorzárak. Eredeti beküldési idő/ár változatlan; kapcsolt fiók hozzáférése az új csoportot követi.
- `PUT /admin/courses/{id}`: `{sessionDates:[20 növekvő ISO dátum],registrationOpen:boolean}`. Első dátum = eredeti indulás, utolsó = endDate. Az admin kapcsolót az `acceptingApplications` mezőből töltsd vissza, ne a telítettségfüggő `registrationOpen` értékből.
- LessonInput: új `courseId`, közzétételhez kötelező. Lesson válasz: `courseId`, `courseName`. Régi csoport nélküli órák csak adminnak láthatók. `/lessons` diáknál csak saját csoport, elfogadott jelentkezés és aktív hozzáférés mellett ad publikált órákat. Admin tanulói nézetben minden publikált órát láthat.
- `GET /access`: saját `{userId,applicationId,courseId,courseName,expiresOn,active}`. `GET /admin/enrollments`: azonos DTO-k listája.
- `PUT /admin/users/{id}/enrollment`: `{applicationId}`; csak STUDENT fiók + elfogadott jelentkezés + ismert kurzusvég. Egy jelentkezés egy fiókhoz. `DELETE` ugyanitt 204, visszavonás. Mindkettő érvényteleníti a régi sessiont és auditált.
- `GET /files/{id}/download`: a fenti csoport/státusz/lejárat ellenőrzés minden letöltésen is megtörténik. Nem jogosultnak 404, nem adat- vagy fájlnévszivárgás. Admin olvashat piszkozatot is. Lejárat: kurzus vége + 1 hónap, az utolsó nap végéig Europe/Budapest szerint.

Minden új admin végpont ADMIN + CSRF védett. Nincs automatikus fizetés, számlázás vagy e-mail. Csoport, pénzügy és hozzáférés módosítása auditált. Részletes üzleti döntések: [novemberi válaszok](../database/docs/LAUNCH-ANSWERS-2026-11.md).


Állapot: 2026-10-21. Minden végpont `/api` alatt, JSON UTF-8. Nincs `/v1` prefix. A csoportválasztás bevezetése miatt a jelentkezés POST új kötelező mezőket kapott: a frontend és backend kiadása összehangolandó. Ugyanazon origin, session cookie (`JSESSIONID`), állapotmódosításkor CSRF fejléc. Minden API válasz `no-store`.

1. `GET /auth/csrf` → `{token,headerName}`; a cookie-t őrizd meg.
2. Módosító kéréshez küldd az itt kapott fejlécet és a cookie-t.
3. `POST /auth/login` `{email,password}` → User; belépés után új CSRF token kell.
4. `GET /auth/me` → User vagy 401.
5. `POST /auth/logout` → 204, session megszűnik.

| Végpont | Jogosultság | Kérés / eredmény |
| --- | --- | --- |
| `GET /courses` | nyilvános | Course[] délelőtt/délután sorrendben |
| `POST /applications` | nyilvános + CSRF | ApplicationInput → 201 ApplicationReceipt |
| `POST /programming-interests` | nyilvános + CSRF | `{name,email,privacyAccepted:true,requestId}` → 201 `{id,message}` |
| `GET /lessons` | belépett | Közzétett Lesson[] (adminnak is a tanulói nézet) |
| `GET /files/{id}/download` | belépett, erőforrásvizsgálat | Bináris attachment; diáknak piszkozatfájl 404 |
| `GET /admin/applications?courseId=1` | ADMIN | Application[] legújabb elöl; a szűrő opcionális |
| `PATCH /admin/applications/{id}` | ADMIN | `{status,notes?}` → Application |
| `GET /admin/users` | ADMIN | User[] |
| `POST /admin/users` | ADMIN | `{name,email,password,role,enabled?}` → 201 User |
| `PATCH /admin/users/{id}` | ADMIN | opcionális `{name,email,password,role,enabled}` → User |
| `GET /admin/lessons` | ADMIN | minden Lesson[] |
| `POST /admin/lessons` | ADMIN | LessonInput → 201 Lesson |
| `PUT /admin/lessons/{id}` | ADMIN | teljes LessonInput → Lesson |
| `DELETE /admin/lessons/{id}` | ADMIN | óra és csatolmányai törlése → 204 |
| `POST /admin/lessons/{id}/files` | ADMIN | multipart `file` mező → 201 File |
| `DELETE /admin/files/{id}` | ADMIN | 204 |
| `GET /admin/audit` | ADMIN | legújabb 200 Audit[] |
| `GET /admin/programming-interests` | ADMIN | ProgrammingInterest[] legújabb elöl |
| `PATCH /admin/programming-interests/{id}` | ADMIN | `{status,notes?}` → ProgrammingInterest |

**User:** `{id,name,email,role,enabled}`. Role `ADMIN | STUDENT`. Hash/jelszó soha nincs válaszban. Név max120, email254, jelszó12–72 karakter és legfeljebb72 UTF-8 bájt. PATCH-nél változatlan jelszót hagyd ki, ne küldj üres stringet.

**Course:** `{id,slug,name,startDate,startTime,endTime,sessions,lessonsPerSession,capacity,acceptedCount,availableSeats,registrationOpen,price}`. Két rögzített csoport: `szombat-delelott` (09:00–13:00), `szombat-delutan` (14:00–18:00). Kezdés `2027-01-09`, 20 alkalom × 4 tanóra = 80 tanóra, 45 perc/tanóra, 24 hely/csoport. Dátum `YYYY-MM-DD`, idő `HH:mm:ss`, Budapest helyi idő. Az id-t a lekérésből használd, ne a slugból számold.

**Price:** `{baseTotal,discountPercent,periodDiscountPercent,friendDiscountPercent,discountLabel,total,perLesson,validUntil}`. Az összegek egész forintok, nem fillérek. Alap 280000 Ft / 80 = 3500 Ft/tanóra. A szerver Europe/Budapest dátuma szerint 2026-11-15 nap végéig 25%, 2026-12-15 nap végéig 15%, utána 0% az időszaki kedvezmény. A `validUntil` az aktuális időszak utolsó napja vagy null. A publikus csoportár 0 baráttal számol.

A kedvezmények összeadódnak: `effectiveFriendCount=max(friendCount,referredByFriend?1:0)`, `friendDiscountPercent=5*effectiveFriendCount`, `discountPercent=min(35,periodDiscountPercent+friendDiscountPercent)`. A `friendDiscountPercent` a plafon előtti komponens; például tíz barátnál 50, miközben a végső kedvezmény legfeljebb 35. A meghívó és a meghívott is jogosult 5%-ra; a meghívottság jelölése nem számolja ugyanazt a barátot kétszer. Példák: 25+5=30% → 196000 Ft / 2450 Ft/tanóra; 25+10=35% → 182000 Ft / 2275 Ft/tanóra. A baráti kapcsolatokat az admin manuálisan ellenőrzi. A tárolt ár ezt a feltételes kedvezményt már tartalmazza, nem fizetési visszaigazolás.

**ApplicationInput:** `{studentName,email,phone,parentName?,message?,privacyAccepted:true,courseId,requestId,friendCount,referralNote?,laptopRentalRequested,referredByFriend?}`. A `requestId` kliens által generált UUID, a `courseId` pozitív egész. `friendCount` kötelező 0–10 egész, `referredByFriend` opcionális boolean, alap false; `laptopRentalRequested` kötelező boolean. `referralNote` max500, nevek max120, email254, telefon40, üzenet4000. A laptopigény rögzítés, a rendszer nem állít hozzá bérleti árat vagy automatikus készletfoglalást.

**ApplicationReceipt:** `{id,message,courseName,quotedTotal,quotedPerLesson,discountLabel,discountPercent,periodDiscountPercent,friendDiscountPercent}`. A válasz a ténylegesen mentett szerverárat tartalmazza. A frontend sikeroldalon ebből jelenítse meg az összeget; az előzetes kliensoldali kalkuláció nem írhatja felül.

**Application:** `{id,studentName,email,phone,parentName,message,status,notes,createdAt,privacyAcceptedAt,privacyVersion,courseId,courseName,friendCount,referralNote,laptopRentalRequested,referredByFriend,friendDiscountStatus,baseTotal,discountPercent,periodDiscountPercent,friendDiscountPercent,discountLabel,quotedTotal,quotedPerLesson}`. Status `NEW | CONTACTED | ACCEPTED | REJECTED`; notes max4000. `friendDiscountStatus` = `PENDING_ADMIN`, ha barátot vagy meghívottságot jelöltek, különben `NOT_REQUESTED`. Ez jelzés, nincs külön automatikus ajánlásellenőrzés vagy kedvezményjóváhagyási workflow.

Az elfogadott jelentkezések foglalnak helyet; `NEW`/`CONTACTED` nem. Admin `ACCEPTED` váltásnál tranzakciós csoportsorzár és számlálás védi a 24 fős limitet, túllépés 409. Már elfogadott rekord ismételt elfogadása nem foglal még egy helyet. Elfogadás visszavonása felszabadít egy helyet. Betelt vagy lezárt csoportba új nyilvános beküldés 409; a `registrationOpen` válasz betelt csoportnál false. A csoportazonosító az admin státuszmódosítással nem változtatható.

A létrehozás időpontját és az ársnapshotot a szerver adja, az admin státuszváltás nem számolja újra. A V2 előtti csoport nélküli jelentkezések megmaradnak: csoport és ár mezőik null; a frontend ezt „Korábbi, csoport nélküli jelentkezés” megjelöléssel kezelje. A V2-ben mentett korábbi árak sem változnak visszamenőleg. Adatkezelési verzió `draft-2026-10`; a végleges szöveggel együtt kell cserélni.

**Idempotencia:** azonos UUID és azonos normalizált üzleti tartalom ugyanazt a rekordot és eredeti ársnapshotot adja vissza 201-gyel. Azonos UUID, eltérő tartalom 409. A kliens hálózati hiba után ugyanazzal az UUID-val és változatlan adatokkal próbálkozzon újra; szerkesztés után új UUID kell. Dupla kattintás vagy párhuzamos ismétlés nem hoz létre több rekordot. Régi kérés ismétlése sem írja felül az admin állapotát. Az API a privát admin adatokat a nyilvános visszaigazolásban nem adja vissza.

**ProgrammingInterest:** `{id,name,email,createdAt,status,notes,updatedAt}`. Status `NEW | CONTACTED | COMPLETED`, notes max4000. A nyilvános űrlap name max120, email max254, kifejezett `privacyAccepted:true` és UUID `requestId` mezőt kér. Az érdeklődés tartósan mentődik, az admin manuálisan veszi fel a kapcsolatot; az alkalmazás nem küld e-mailt, nincs SMTP worker, retry végpont vagy hírlevél-feliratkozás. Az adatkezelési verzió `programming-preview-draft-2026-10`, kizárólag a megkereséshez. Állapotmódosítás új `updatedAt` szerveridőt kap, az eredeti `createdAt` változatlan. Az idempotenciaszabály a jelentkezéssel megegyezik.

**Korlátozás:** jelentkezés 10/óra/IP; programozási érdeklődés 10/óra/IP és 3/óra/normalizált e-mail-cím. Az ismétlések is beleszámítanak. A limit egy backend memóriájában él, újrainduláskor alaphelyzetbe áll; élesben megbízható proxy és edge védelem is szükséges.

**LessonInput:** `{title,description,startsAt,meetingUrl,published}`. Title kötelező max160, description max6000, meetingUrl max2048, üres/null megengedett. Link csak HTTPS, valódi hosttal, userinfo nélkül. `startsAt` nullable ISO LocalDateTime, pl. `2026-11-01T15:00:00`: mindig **Europe/Budapest szerinti falióra**, nem UTC. Az admin datetime-local mező ezt szerkeszti. Külföldi felhasználónál sem értelmezhető át a készülék időzónájába; időzónás eseménykezelés külön későbbi migráció.

**Lesson:** LessonInput + `{id,files:File[]}`. **File:** `{id,originalName,size,contentType}`; `size` bájt, tárolási kulcs nem nyilvános. Max20MB, nem üres; tetszőleges oktatási formátum letölthető, automatikus futtatás/kicsomagolás nincs. A letöltés `application/octet-stream`.

**Audit:** `{id,actor,action,target,createdAt}`. A létrehozási/audit/hozzájárulási dátumok UTC `Instant` (`Z`), a frontend Budapestre formázza őket. Actor e-mail vagy `public`/`bootstrap`, target csak típus és ID. Nincs naplózott jelszó vagy jelentkezési üzenet. Műveletek: LOGIN, USER_CREATED/UPDATED, APPLICATION_CREATED/UPDATED, PROGRAMMING_INTEREST_CREATED/UPDATED, LESSON_CREATED/UPDATED/DELETED, FILE_UPLOADED/DELETED. Kilépési eseményt a jelenlegi audit nem tárol.

**Hibák:** `{message,errors?}`. 400 hibás adat; 401 nincs/lejárt session vagy hibás belépés; 403 szerepkör/CSRF; 404 hiányzó/nem látható erőforrás; 409 ütközés; 413 fájlméret; 429 korlátozás. Nincs publikus stacktrace. A frontend nem feltételezhet sikeres műveletet hálózati hiba esetén.
