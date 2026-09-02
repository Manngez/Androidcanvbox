# Pocket Canvas för Android

En första lokal prototyp av ett Spool-liknande designverktyg för Android. Du beskriver en app, OpenAI skapar ett strukturerat flöde, och skärmarna placeras på en panorerbar och zoomningsbar canvas.

## Det som fungerar i version 0.1

- AI-generering av 3–7 appskärmar via Responses API och strikt JSON-schema.
- Panorering och pinch-zoom på en praktiskt taget obegränsad canvas.
- Flyttbara skärmramar och automatiska kopplingslinjer.
- Renderade rubriker, text, kort och knappar.
- Krypterad lokal lagring av API-nyckel för privat testning.
- Exempelflöde visas direkt utan API-nyckel.

## Starta

1. Öppna mappen `PocketCanvas` i Android Studio.
2. Låt Android Studio synkronisera Gradle och installera Android SDK 35 om det behövs.
3. Kör på en Android-enhet eller emulator med Android 8 eller senare.
4. Tryck **API-nyckel**, klistra in en OpenAI API-nyckel och spara.
5. Beskriv exempelvis: `En träningsapp för mig och min son med pass A och B, övningskort och träningslogg`.

## Viktig säkerhet

Direkt API-anrop från mobilen är bara avsett för privat prototypning. Krypterad lagring hindrar enkel läsning från telefonens filer, men kan inte skydda nyckeln i en distribuerad APK. Före publicering ska anropet flyttas till en serverproxy med inloggning, kostnadstak och hastighetsbegränsning.

## Nästa lämpliga steg

- Klickbart presentationsläge som navigerar via `target`.
- Visuell editor för element och länkar.
- Spara flera projekt lokalt med Room.
- Export till Jetpack Compose-kod och Git-repository.
- Serverproxy för säker publik användning.
