package android.net

import android.os.Parcel

object FakeUri : Uri() {
    override fun buildUpon(): Uri.Builder = throw UnsupportedOperationException()
    override fun getAuthority(): String? = null
    override fun getEncodedAuthority(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getEncodedPath(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getEncodedSchemeSpecificPart(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun getFragment(): String? = null
    override fun getHost(): String? = null
    override fun getLastPathSegment(): String? = "test"
    override fun getPath(): String? = "/test"
    override fun getPathSegments(): List<String> = listOf("test")
    override fun getPort(): Int = -1
    override fun getQuery(): String? = null
    override fun getScheme(): String? = "test"
    override fun getSchemeSpecificPart(): String? = "clip"
    override fun getUserInfo(): String? = null
    override fun isHierarchical(): Boolean = false
    override fun isRelative(): Boolean = false
    override fun toString(): String = "test://clip"
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) = Unit
}
