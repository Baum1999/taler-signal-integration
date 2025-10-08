/*
  This file is part of TALER
  Copyright (C) 2024 Taler Systems SA

  TALER is free software; you can redistribute it and/or modify it under the
  terms of the GNU General Public License as published by the Free Software
  Foundation; either version 3, or (at your option) any later version.

  TALER is distributed in the hope that it will be useful, but WITHOUT ANY
  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
  A PARTICULAR PURPOSE.  See the GNU General Public License for more details.

  You should have received a copy of the GNU General Public License along with
  TALER; see the file COPYING.  If not, see <http://www.gnu.org/licenses/>
*/

#include <jni.h>
#include <string>
#include <android/log.h>
#include <sys/endian.h>

#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO, "verification", __VA_ARGS__)

// needed for libsodium
#include <sodium/crypto_sign.h>
#include <sodium/crypto_hash_sha256.h>

/**
 * Maximum legal 'value' for an amount, based on IEEE double (for JavaScript compatibility).
 */
#define TALER_AMOUNT_MAX_VALUE (1LLU << 52)

/**
 * Define as empty, GNUNET_PACKED should suffice, but this won't work on W32
 */
#define GNUNET_NETWORK_STRUCT_BEGIN

/**
 * Define as empty, GNUNET_PACKED should suffice, but this won't work on W32;
 */
#define GNUNET_NETWORK_STRUCT_END

/**
 * gcc-ism to get packed structs.
 */
#define GNUNET_PACKED __attribute__ ((packed))

#define TALER_CURRENCY_LEN 12

/**
 * @brief The "fraction" value in a `struct TALER_Amount` represents which
 * fraction of the "main" value?
 *
 * Note that we need sub-cent precision here as transaction fees might
 * be that low, and as we want to support microdonations.
 *
 * An actual `struct Amount a` thus represents
 * "a.value + (a.fraction / #TALER_AMOUNT_FRAC_BASE)" units of "a.currency".
 */
#define TALER_AMOUNT_FRAC_BASE 100000000

enum GNUNET_GenericReturnValue
{
    GNUNET_SYSERR = -1,
    GNUNET_NO = 0,
    GNUNET_OK = 1,
    /* intentionally identical to #GNUNET_OK! */
    GNUNET_YES = 1,
};

/**
 * Public ECC key (always for curve Ed25519) encoded in a format
 * suitable for network transmission and EdDSA signatures.  Refer
 * to section 5.1.3 of rfc8032, for a thorough explanation of how
 * this value maps to the x- and y-coordinates.
 */
struct GNUNET_CRYPTO_EddsaPublicKey
{
    /**
     * Point Q consists of a y-value mod p (256 bits); the x-value is
     * always positive. The point is stored in Ed25519 standard
     * compact format.
     */
    unsigned char q_y[256 / 8];
};

/**
 * @brief an ECC signature using EdDSA.
 * See cr.yp.to/papers.html#ed25519
 */
struct GNUNET_CRYPTO_EddsaSignature
{
    /**
     * R value.
     */
    unsigned char r[256 / 8];

    /**
     * S value.
     */
    unsigned char s[256 / 8];
};

/**
 * Regular online message signing key used by Donau.
 */
struct DONAU_DonauPublicKeyP
{
    /**
     * Donau uses EdDSA for non-blind signing.
     */
    struct GNUNET_CRYPTO_EddsaPublicKey eddsa_pub;

};

/**
 * @brief Type of signature used by the donau for non-blind signatures.
 */
struct DONAU_DonauSignatureP
{
    /**
     * Donau uses EdDSA for for non-blind signatures.
     */
    struct GNUNET_CRYPTO_EddsaSignature eddsa_sig;
};

/**
 * @brief header of what an ECC signature signs
 *        this must be followed by "size - 8" bytes of
 *        the actual signed data
 */
struct GNUNET_CRYPTO_EccSignaturePurpose
{
    /**
     * How many bytes does this signature sign?
     * (including this purpose header); in network
     * byte order (!).
     */
    uint32_t size GNUNET_PACKED;

    /**
     * What does this signature vouch for?  This
     * must contain a GNUNET_SIGNATURE_PURPOSE_XXX
     * constant (from gnunet_signatures.h).  In
     * network byte order!
     */
    uint32_t purpose GNUNET_PACKED;
};

GNUNET_NETWORK_STRUCT_BEGIN

/**
 * @brief Amount, encoded for network transmission.
 */
struct TALER_AmountNBO
{
    /**
     * Value in the main currency, in NBO.
     */
    uint64_t value GNUNET_PACKED;

    /**
     * Fraction (integer multiples of #TALER_AMOUNT_FRAC_BASE), in NBO.
     */
    uint32_t fraction GNUNET_PACKED;

    /**
     * Type of the currency being represented. Length is 12.
     */
    char currency[TALER_CURRENCY_LEN];
};

GNUNET_NETWORK_STRUCT_END

/**
 * @brief Representation of monetary value in a given currency.
 */
struct TALER_Amount
{
    /**
     * Value (numerator of fraction)
     */
    uint64_t value;

    /**
     * Fraction (integer multiples of #TALER_AMOUNT_FRAC_BASE).
     */
    uint32_t fraction;

    /**
     * Currency string, left adjusted and padded with zeros.  All zeros
     * for "invalid" values.
     */
    char currency[TALER_CURRENCY_LEN];
};

int
TALER_amount_is_valid (const struct TALER_Amount *amount)
{
    if (amount->value > TALER_AMOUNT_MAX_VALUE)
    {
        return -1;
    }
    return ('\0' != amount->currency[0]) ? 1 : 0;
}

uint64_t
GNUNET_htonll (uint64_t n)
{
    return (((uint64_t) htonl (n)) << 32) + htonl (n >> 32);
    //return n;
}

void
TALER_amount_hton (struct TALER_AmountNBO *res,
                   const struct TALER_Amount *d)
{
    if (1 != TALER_amount_is_valid (d)) {
        res = NULL;
        return;
    }
    res->value = GNUNET_htonll (d->value);
    res->fraction = htonl (d->fraction);
    for (unsigned int i = 0; i<TALER_CURRENCY_LEN; i++)
        res->currency[i] = d->currency[i];
}

/**
 * Set @a a to "invalid".
 *
 * @param[out] a amount to set to invalid
 */
static void
invalidate (struct TALER_Amount *a)
{
    memset (a,
            0,
            sizeof (struct TALER_Amount));
}

enum GNUNET_GenericReturnValue
TALER_check_currency (const char *str)
{
    if (strlen (str) >= TALER_CURRENCY_LEN)
    {
        return GNUNET_SYSERR;
    }
    /* validate str has only legal characters in it! */
    for (unsigned int i = 0; '\0' != str[i]; i++)
    {
        if ( ('A' > str[i]) || ('Z' < str[i]) )
        {
            return GNUNET_SYSERR;
        }
    }
    return GNUNET_OK;
}

enum GNUNET_GenericReturnValue
TALER_string_to_amount (const char *str,
                        struct TALER_Amount *amount)
{
    int n;
    uint32_t b;
    const char *colon;
    const char *value;

    /* skip leading whitespace */
    while (isspace ( (unsigned char) str[0]))
        str++;
    if ('\0' == str[0])
    {
        invalidate (amount);
        return GNUNET_SYSERR;
    }

    /* parse currency */
    colon = strchr (str, (int) ':');
    if ( (NULL == colon) ||
         (colon == str) ||
         ((colon - str) >= TALER_CURRENCY_LEN) )
    {
        invalidate (amount);
        return GNUNET_SYSERR;
    }

    if (TALER_CURRENCY_LEN <= (colon - str)) return GNUNET_SYSERR;
    for (unsigned int i = 0; i<colon - str; i++)
        amount->currency[i] = str[i];
    /* 0-terminate *and* normalize buffer by setting everything to '\0' */
    memset (&amount->currency [colon - str],
            0,
            TALER_CURRENCY_LEN - (colon - str));
    if (GNUNET_OK !=
        TALER_check_currency (amount->currency))
        return GNUNET_SYSERR;
    /* skip colon */
    value = colon + 1;
    if ('\0' == value[0])
    {
        invalidate (amount);
        return GNUNET_SYSERR;
    }

    amount->value = 0;
    amount->fraction = 0;

    /* parse value */
    while ('.' != *value)
    {
        if ('\0' == *value)
        {
            /* we are done */
            return GNUNET_OK;
        }
        if ( (*value < '0') ||
             (*value > '9') )
        {
            invalidate (amount);
            return GNUNET_SYSERR;
        }
        n = *value - '0';
        if ( (amount->value * 10 < amount->value) ||
             (amount->value * 10 + n < amount->value) ||
             (amount->value > TALER_AMOUNT_MAX_VALUE) ||
             (amount->value * 10 + n > TALER_AMOUNT_MAX_VALUE) )
        {
            invalidate (amount);
            return GNUNET_SYSERR;
        }
        amount->value = (amount->value * 10) + n;
        value++;
    }

    /* skip the dot */
    value++;


    /* parse fraction */
    if ('\0' == *value)
    {
        invalidate (amount);
        return GNUNET_SYSERR;
    }
    b = TALER_AMOUNT_FRAC_BASE / 10;
    while ('\0' != *value)
    {
        if (0 == b)
        {
            invalidate (amount);
            return GNUNET_SYSERR;
        }
        if ( (*value < '0') ||
             (*value > '9') )
        {
            invalidate (amount);
            return GNUNET_SYSERR;
        }
        n = *value - '0';
        amount->fraction += n * b;
        b /= 10;
        value++;
    }
    return GNUNET_OK;
}

enum GNUNET_GenericReturnValue
TALER_string_to_amount_nbo (const char *str,
                            struct TALER_AmountNBO *amount_nbo)
{
    struct TALER_Amount amount;

    if (GNUNET_OK !=
        TALER_string_to_amount (str,
                                &amount))
        return GNUNET_SYSERR;
    TALER_amount_hton (amount_nbo,
                       &amount);
    return GNUNET_OK;
}

/**
  * Donor's hashed and salted unique donation identifier.
  */
struct DONAU_HashDonorTaxId
{
    unsigned char hash[256 / 8];
};


GNUNET_NETWORK_STRUCT_BEGIN

/**
 * @brief Format used to generate the signature/donation statement
 * over the total amount and a donor identifier of a year.
 */
struct DONAU_DonationStatementConfirmationPS
{
    /**
     * Purpose must be #DONAU_SIGNATURE_DONAU_DONATION_STATEMENT. Signed
     * by a `struct DONAU_DonauPublicKeyP` using EdDSA.
     */
    struct GNUNET_CRYPTO_EccSignaturePurpose purpose;

    /**
     * Total amount donated of a specific @a year.
     */
    struct TALER_AmountNBO amount_tot;

    /**
     * The hash of the identifier of the donor.
     */
    struct DONAU_HashDonorTaxId i;

    /**
     * The corresponding year.
     */
    uint32_t year;

};

GNUNET_NETWORK_STRUCT_END


/**
 * Get the decoded value corresponding to a character according to Crockford
 * Base32 encoding.
 *
 * @param a a character
 * @return corresponding numeric value
 */
static unsigned int
getValue__ (unsigned char a)
{
    unsigned int dec;

    switch (a)
    {
        case 'O':
        case 'o':
            a = '0';
            break;

        case 'i':
        case 'I':
        case 'l':
        case 'L':
            a = '1';
            break;

            /* also consider U to be V */
        case 'u':
        case 'U':
            a = 'V';
            break;

        default:
            break;
    }
    if ((a >= '0') && (a <= '9'))
        return a - '0';
    if ((a >= 'a') && (a <= 'z'))
        a = toupper (a);
    /* return (a - 'a' + 10); */
    dec = 0;
    if ((a >= 'A') && (a <= 'Z'))
    {
        if ('I' < a)
            dec++;
        if ('L' < a)
            dec++;
        if ('O' < a)
            dec++;
        if ('U' < a)
            dec++;
        return(a - 'A' + 10 - dec);
    }
    return -1;
}

int
GNUNET_STRINGS_string_to_data (const char *enc,
                               size_t enclen,
                               void *out,
                               size_t out_size)
{
    size_t rpos;
    size_t wpos;
    unsigned int bits;
    unsigned int vbit;
    int ret;
    int shift;
    unsigned char *uout;
    size_t encoded_len;

    if (0 == enclen)
    {
        if (0 == out_size)
            return 0;
        return -1;
    }
    if (out_size >= SIZE_MAX) return -1;
    encoded_len = out_size * 8;
    uout = (unsigned char *) out;
    wpos = out_size;
    rpos = enclen;
    if ((encoded_len % 5) > 0)
    {
        vbit = encoded_len % 5;   /* padding! */
        shift = 5 - vbit;
        bits = (ret = getValue__ (enc[--rpos])) >> shift;
    }
    else
    {
        vbit = 5;
        shift = 0;
        bits = (ret = getValue__ (enc[--rpos]));
    }
    if ((encoded_len + shift) / 5 != enclen)
        return -1;
    if (-1 == ret)
        return -1;
    while (wpos > 0)
    {
        if (0 == rpos)
        {
            return -1;
        }
        bits = ((ret = getValue__ (enc[--rpos])) << vbit) | bits;
        if (-1 == ret)
            return -1;
        vbit += 5;
        if (vbit >= 8)
        {
            uout[--wpos] = (unsigned char) bits;
            bits >>= 8;
            vbit -= 8;
        }
    }
    if ((0 != rpos) || (0 != vbit))
        return -1;
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_net_taler_donauverificator_Results_ed25519_1verify(
        JNIEnv *env, jobject thiz,
        jstring year,
        jstring totalAmount,
        jstring taxId,
        jstring taxIdSalt,
        jstring signature,
        jstring public_key) {

    const char *const year_string = reinterpret_cast<const char *const>(env->GetStringUTFChars(
            year, 0));
    const char *const s_str = reinterpret_cast<const char *const>(env->GetStringUTFChars(
            signature, 0));
    const char *const pub_str = reinterpret_cast<const char *const>(env->GetStringUTFChars(
            public_key, 0));
    const char* total_amount = env->GetStringUTFChars(totalAmount, 0);
    const unsigned char* tax_id = reinterpret_cast<const unsigned char*>(env->GetStringUTFChars(taxId, 0));
    const unsigned char* salt = reinterpret_cast<const unsigned char*>(env->GetStringUTFChars(taxIdSalt, 0));

    unsigned long long donation_year;
    char dummy;
    if ( (NULL == year_string) ||
         (1 != sscanf (year_string,
                       "%llu%c",
                       &donation_year,
                       &dummy)) )
    {
        return -6;
    }

    struct DONAU_DonauPublicKeyP pub;
    struct DONAU_DonauSignatureP sig;
    struct DONAU_HashDonorTaxId h_donor_tax_id;

    crypto_hash_sha256_state state;
    crypto_hash_sha256_init(&state);

    unsigned int tax_length;
    for (tax_length = 0; tax_id[tax_length]!= '\0'; ++tax_length);
    unsigned int salt_length;
    for (salt_length = 0; salt[salt_length]!= '\0'; ++salt_length);

    crypto_hash_sha256_update(&state, tax_id, tax_length);
    crypto_hash_sha256_update(&state, salt, salt_length);

    crypto_hash_sha256_final(&state, h_donor_tax_id.hash);

    struct DONAU_DonationStatementConfirmationPS confirm = {
            .purpose.purpose = htonl (1500),
            .purpose.size = htonl (sizeof (confirm)),
            .year = htonl (donation_year),
            .i = h_donor_tax_id
    };
    if (GNUNET_OK != TALER_string_to_amount_nbo (total_amount,
                                         &confirm.amount_tot)) {
        return -3;
    }
    if (0 !=
        GNUNET_STRINGS_string_to_data (s_str,
                                       strlen (s_str),
                                       &sig,
                                       sizeof (sig)))
    {
        return -4;
    }
    if (0 !=
        GNUNET_STRINGS_string_to_data (pub_str,
                                       strlen (pub_str),
                                       &pub,
                                       sizeof (pub)))
    {
        return -5;
    }
    size_t mlen = ntohl (confirm.purpose.size);
    const unsigned char *m = (const unsigned char *) &confirm.purpose;
    const unsigned char *s = (const unsigned char *) &sig.eddsa_sig;
    const unsigned char *eddsa_pub = (const unsigned char*) &pub.eddsa_pub.q_y;
    //verify function from libsodium (also used by GNUNET)
    return crypto_sign_verify_detached (s, m, mlen, eddsa_pub);
}
