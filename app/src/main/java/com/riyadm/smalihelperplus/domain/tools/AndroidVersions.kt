package com.riyadm.smalihelperplus.domain.tools

/** Android platform versions by API level, used for min-API pickers and DEX version hints. */
object AndroidVersions {
    data class Version(val api: Int, val release: String, val codename: String?) {
        /** e.g. "API 26 - Android 8.0 Oreo". */
        val label: String get() = "API $api – Android $release" + (codename?.let { " $it" } ?: "")
    }

    const val MIN_API = 1
    const val MAX_API = 37

    val all: List<Version> = listOf(
        Version(1, "1.0", null),
        Version(2, "1.1", null),
        Version(3, "1.5", "Cupcake"),
        Version(4, "1.6", "Donut"),
        Version(5, "2.0", "Eclair"),
        Version(6, "2.0.1", "Eclair"),
        Version(7, "2.1", "Eclair"),
        Version(8, "2.2", "Froyo"),
        Version(9, "2.3", "Gingerbread"),
        Version(10, "2.3.3", "Gingerbread"),
        Version(11, "3.0", "Honeycomb"),
        Version(12, "3.1", "Honeycomb"),
        Version(13, "3.2", "Honeycomb"),
        Version(14, "4.0", "Ice Cream Sandwich"),
        Version(15, "4.0.3", "Ice Cream Sandwich"),
        Version(16, "4.1", "Jelly Bean"),
        Version(17, "4.2", "Jelly Bean"),
        Version(18, "4.3", "Jelly Bean"),
        Version(19, "4.4", "KitKat"),
        Version(20, "4.4W", "KitKat Watch"),
        Version(21, "5.0", "Lollipop"),
        Version(22, "5.1", "Lollipop"),
        Version(23, "6.0", "Marshmallow"),
        Version(24, "7.0", "Nougat"),
        Version(25, "7.1", "Nougat"),
        Version(26, "8.0", "Oreo"),
        Version(27, "8.1", "Oreo"),
        Version(28, "9", "Pie"),
        Version(29, "10", null),
        Version(30, "11", null),
        Version(31, "12", null),
        Version(32, "12L", null),
        Version(33, "13", null),
        Version(34, "14", null),
        Version(35, "15", null),
        Version(36, "16", null),
        Version(37, "17", null),
    )

    fun byApi(api: Int): Version? = all.firstOrNull { it.api == api }

    fun label(api: Int): String = byApi(api)?.label ?: "API $api"

    /**
     * First Android release (API level) that understands the given DEX file version, per the DEX format document:
     * 035 is the original version, 037 came with Android 7.0, 038 with 8.0, 039 with 9, 040 with 10.
     * Version 041 is experimental (Android 16 container format) and is not meant for production code.
     */
    fun apiForDexVersion(version: String): Int? = when (version.trim()) {
        "035" -> 1
        "037" -> 24
        "038" -> 26
        "039" -> 28
        "040" -> 29
        "041" -> 36
        else -> null
    }
}
