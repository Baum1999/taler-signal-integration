package net.taler.wallet.link

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DD-103-Format (https://<host>/link#<aktion>), siehe
 * docs/superpowers/specs/2026-10-16-taler-dd103-links-design.md. wrap()-Tests
 * verweisen auf TalerLinkHosts.WRAP_HOST statt auf ein Literal, damit sie auch
 * auf dem downstream-Branch (anderer WRAP_HOST) gruen bleiben.
 */
class TalerHttpsLinkTest {

    @Test
    fun wrapsTalerUriInShortFragmentForm() {
        assertEquals(
            "https://${TalerLinkHosts.WRAP_HOST}/link#pay-push/exchange.example/ABC",
            TalerHttpsLink.wrap("taler://pay-push/exchange.example/ABC")
        )
    }

    @Test
    fun wrapIgnoresSchemeCase() {
        assertEquals(
            "https://${TalerLinkHosts.WRAP_HOST}/link#pay-pull/exchange.example/XYZ",
            TalerHttpsLink.wrap("TALER://pay-pull/exchange.example/XYZ")
        )
    }

    @Test
    fun wrapKeepsExtTalerInLongForm() {
        assertEquals(
            "https://${TalerLinkHosts.WRAP_HOST}/link#ext+taler://pay-push/e/A",
            TalerHttpsLink.wrap("ext+taler://pay-push/e/A")
        )
    }

    @Test
    fun unwrapsAllThreeDd103Forms() {
        val expected = "taler://pay-push/exchange.example/ABC"
        assertEquals(expected, TalerHttpsLink.unwrap("https://wallet.taler.net/link#pay-push/exchange.example/ABC"))
        assertEquals(expected, TalerHttpsLink.unwrap("https://wallet.taler.net/link#/pay-push/exchange.example/ABC"))
        assertEquals(expected, TalerHttpsLink.unwrap("https://wallet.taler.net/link#taler://pay-push/exchange.example/ABC"))
        assertEquals(expected, TalerHttpsLink.unwrap("https://wallet.taler.net/link/#pay-push/exchange.example/ABC"))
    }

    @Test
    fun unwrapKeepsExtTaler() {
        assertEquals(
            "ext+taler://pay-push/x/A",
            TalerHttpsLink.unwrap("https://wallet.taler.net/link#ext+taler://pay-push/x/A")
        )
    }

    @Test
    fun unwrapIsCaseInsensitiveForSchemeAndHost() {
        assertEquals(
            "taler://pay-push/exchange.example/ABC",
            TalerHttpsLink.unwrap("HTTPS://WALLET.TALER.NET/link#pay-push/exchange.example/ABC")
        )
        assertEquals(
            "TALER://pay-push/x/A",
            TalerHttpsLink.unwrap("https://wallet.taler.net/link#TALER://pay-push/x/A")
        )
    }

    @Test
    fun returnsInputUnchangedForNonLinks() {
        listOf(
            "taler://pay-push/exchange.example/ABC",
            "https://wallet.taler.net/link#",
            "https://wallet.taler.net/link#https://evil.example",
            "https://wallet.taler.net/link#javascript:alert(1)",
            "https://wallet.taler.net/link#/javascript:alert(1)",
            "https://wallet.taler.net/link?x=1#pay-push/x/A",
            "https://wallet.taler.net:443/link#pay-push/x/A",
            "https://wallet.taler.net/linkx#pay-push/x/A",
            "https://wallet.taler.net.evil.example/link#pay-push/x/A",
            "https://wallet.taler.net@evil.example/link#pay-push/x/A",
            "https://evil.example/link#pay-push/x/A",
            "https://wallet.taler.net/r?u=taler://pay-push/x/A",
        ).forEach { input ->
            assertEquals(input, TalerHttpsLink.unwrap(input))
        }
    }

    @Test
    fun roundTripsPushAndPull() {
        listOf(
            "taler://pay-push/exchange.example/ABC",
            "taler://pay-pull/exchange.example/XYZ",
        ).forEach { original ->
            assertEquals(original, TalerHttpsLink.unwrap(TalerHttpsLink.wrap(original)))
        }
    }
}
