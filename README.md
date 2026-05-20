# GNU Taler Android Code Repository

This git repository contains code for GNU Taler Android apps and libraries.
The official location is: 

    https://git.taler.net/taler-android.git
    
## Structure

* [**cashier**](/cashier) - an Android app that enables you to take cash and give out electronic cash
* [**merchant-lib**](/merchant-lib) - a library providing communication with a merchant backend
  to be used by the point of sale app below.
* [**merchant-terminal**](/merchant-terminal) - a merchant point of sale terminal Android app
  that allows sellers to
  process customers’ orders by adding or removing products,
  calculate the amount owed by the customer
  and let the customer make a Taler payment via QR code or NFC.
* [**taler-kotlin-android**](/taler-kotlin-android) - an Android library containing common code
  needed by more than one Taler Android app.
* [**wallet**](/wallet) - a GNU Taler wallet Android app

## Building

You can get a list of possible build tasks like this:
    
    $ ./gradlew tasks
    
See the [Taler developer manual](https://docs.taler.net/developers-manual.html#build-apps-from-source).
for more information about building individual apps.

## I18N (Internationalization)

The default source language is **English**. All translatable strings are defined in (one of
various) locations:

``res/values/strings.xml``

### Folder Structure

Translations follow the standard Android convention:

res/values/          -> English (source / reference)
res/values-de/       -> German
res/values-it/       -> Italian
res/values-fr/       -> French

etc.

### Check for Available Languages and See Translation Status

Use the helper script ``check_translations.py`` to analyze the current
translation coverage across all modules in the repository.

*Note:
The script automatically detects the git repos root via the .git folder
and scans for all modules (merchant-terminal, cashier, wallet etc.), aslo
when used from any sub folder of the git repository.*

From the git repository's root path, you can run the script as follows:

```bash
# Overview of all languages

./check-translations.py

# Show missing strings for a specific language; various examples:

./check-translations.py de     # German
./check-translations.py it     # Italian
./check-translations.py fr     # French
```
