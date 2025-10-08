# Donau Verify
The app verifies the donation statement made by a Donau.

## Testing
1. With provided QR-Codes in the root app directory
2. For test purposes, a string of a valid donation statement is already hard coded.
3. With the defined URI scheme following command can be used (developer mode must be enabled for `donau+http` URIs):
```bash
adb shell am start -a android.intent.action.VIEW -d "donau://example.com/megacharity/1234/2025/7560001010000/1234?total=EUR:15&sig=ED25519:H9PM3BW3P8MEKB34GZ0G1F7JSNVX7B8AHXRFFMS37QZM7TXZ5MWPXTEDZZGN1QRB1AFPKNCFXJB39NJHP3BAFGCZSCXHEYPHA1YJY28&pub=K641W1CZM7DRSV184M8CPM3Z8MZRBYYJMNYMJK70FTYJHBPX21J0"
```
## Future Work
The public key should be requested directly from the Donau over HTTPS,
 for this the Donau base url is needed -> pass it with the QR code?

## Building
### build requirements
- minimal Android SDK: 31
- gradle
### build from command line
Mac OS, Linux:
- chmod +x gradlew
- ./gradlew

Windows:
- gradlew.bat
0
