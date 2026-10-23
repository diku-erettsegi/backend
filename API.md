# API szerződés – csoportok, összevont kedvezmények és manuális érdeklődőkezelés

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
