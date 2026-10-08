# GNU Taler für Signal

Dies ist ein Fork von [GNU Taler Android](https://git.taler.net/taler-android.git) (wallet), nur für eigene Entwicklungszwecke gedacht, nicht offiziell und nicht für die Allgemeinheit.

## Was wurde geändert

- **Zahlungen aus Signal**: Senden/Anfordern über `talerlink://`-Deep-Links mit Richtungs-Toggle (Senden/Anfordern), Compose-Bestätigungsscreen, Annehmen/Ablehnen-Rücksprung zu Signal.
- **Gruppen-Split**: Zahlungsbetrag auf mehrere Empfänger aufteilen, parallele Purse-Erstellung mit Retry/Polling, Transaktionszusammenfassung je Gruppe.
- **Vertragsdaten für Signal**: Beträge/Zweck als JSON an Signal übergeben, statt nur als Freitext.

## Lizenz

Lizenziert unter der GNU GPLv3: https://www.gnu.org/licenses/gpl-3.0.html — siehe [COPYING](COPYING).
