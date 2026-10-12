package dev.fireforge.sauron.domain

import java.text.Normalizer

object SlipNormalizer {

    fun normalize(text: String): String =
        Normalizer.normalize(text.replace(SARA_AM_DECOMPOSED, SARA_AM), Normalizer.Form.NFC)

    private const val SARA_AM_DECOMPOSED = "\u0E4D\u0E32"
    private const val SARA_AM = "\u0E33"
}
