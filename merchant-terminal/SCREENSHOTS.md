# Merchant Terminal screenshots

This module now has a debug-only screenshot mode for deterministic captures of real app screens.

## What it does

- Launches the app directly into a named scenario using an intent extra.
- Seeds fixed merchant config, products, totals, and product thumbnails in-process.
- Captures the resulting screen with `adb exec-out screencap`.

Bundled fixture product images are generated locally by `merchant-terminal/scripts/generate-screenshot-product-images.sh`, so the default workflow does not depend on third-party image licenses.

## Supported scenarios

- `amount-entry`
- `order`
- `payment`
- `payment-success`

## Generate fixture product images

```bash
merchant-terminal/scripts/generate-screenshot-product-images.sh
```

The images are written to `merchant-terminal/src/debug/assets/screenshot-products/`.

## Capture screenshots

Connect an emulator or device, then run:

```bash
merchant-terminal/scripts/capture-screenshots.sh
```

Custom output directory:

```bash
merchant-terminal/scripts/capture-screenshots.sh /tmp/merchant-terminal-shots
```

Specific scenarios only:

```bash
merchant-terminal/scripts/capture-screenshots.sh /tmp/merchant-terminal-shots order payment
```

## Intent hook

The debug hook is activated with:

```text
--es taler_pos_screenshot_scenario <scenario>
```

Example:

```bash
adb shell am start -W \
  -n net.taler.merchantpos/.MainActivity \
  --es taler_pos_screenshot_scenario order
```

## If you want real photos later

For external product photos, use sources with clear reuse terms and record the exact asset URLs and license notes alongside the files you import.

Reasonable options:

- Pexels license: https://www.pexels.com/license/
- Unsplash license: https://unsplash.com/license
- Wikimedia Commons reuse guide: https://commons.wikimedia.org/wiki/Commons:Simple_media_reuse_guide

For maximum simplicity in a repo, prefer public-domain or CC0 assets, or keep using locally generated fixture images for screenshot automation.
