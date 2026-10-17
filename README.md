# GNU Taler Android wallet with Signal integration

This is a fork of [GNU Taler Android](https://git.taler.net/taler-android.git) (wallet) that lets messengers such as Signal start and track peer-to-peer payments. It is a development fork, not an official GNU Taler release.

The `master` branch contains only the integration on top of upstream and is regularly rebased onto upstream `master`.

## What it adds

- **Payments from a messenger**: send or request money via `talerlink://` deep links, with a direction toggle, a compose confirmation screen and a verified jump back to the messenger after accepting or declining.
- **Group split**: split an amount across several recipients, with parallel purse creation, retry and polling, and a per-group transaction summary.
- **Structured payment data**: amount and subject are handed to the messenger as JSON instead of free text.
- **https payment links**: pay-push/pay-pull links can be shared as `https://wallet.taler.net/link#...` (DD 103) so they are clickable in messengers.

The messenger side lives in the matching fork of [Signal Android](https://github.com/signalapp/Signal-Android).

## License

Licensed under the GNU GPLv3: https://www.gnu.org/licenses/gpl-3.0.html, see [COPYING](COPYING).
