# Donau Verify
The app verifies the donation statement made by a Donau.

## Testing
1. With provided QR-Codes in the root app directory
2. For test purposes, a string of a valid donation statement is already hard coded.
3. With the defined URI scheme following command can be used:
```bash
adb shell am start -a android.intent.action.VIEW -d "donau://example.com/megacharity/1234/2024/7560001010000/1234?total=EUR:15&sig=ED25519:SAAM5BA1F9H4VT6T78CFC3X63HAMY2TXB597XBVZ0EMXEZ90QPJ3000BXDBJ3ECHGB8AEX9FFQ5BAXVSF6X6NXM98PY353F2R99PP1R&pub=E24CDJHGSPZG20ZSSTMTBREGCCP495WKETQYCYA9C93EPMZN4FEG"
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
