package com.fpclient.android.ui.navigation

/**
 * Interpretation of the optional `sharedUri` query argument on the [Routes.CREATE]
 * destination: whether a raw value may be treated as a pre-selected file.
 *
 * Navigation passes the query value through verbatim, so a navigation to the unfilled
 * route pattern delivers the literal `{sharedUri}` placeholder. Only scheme-bearing
 * strings (`content://…`, `file://…`) identify a real file; anything else means
 * "no file chosen" and the upload form must ask the user to pick one.
 */
object SharedUriArg {

    /** URI scheme grammar (letters first, then letters/digits/+/-/.), as in RFC 3986. */
    private val SCHEME = Regex("^[a-zA-Z]+[+\\w\\-.]*:")

    /** True when [value] is a URI carrying a scheme and can pre-select a file. */
    fun isFileUri(value: String?): Boolean = value != null && SCHEME.containsMatchIn(value)
}