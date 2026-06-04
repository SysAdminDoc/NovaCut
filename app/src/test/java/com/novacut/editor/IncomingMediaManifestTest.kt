package com.novacut.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class IncomingMediaManifestTest {

    @Test
    fun mainActivityDeclaresSharesheetSendFiltersForMediaTypes() {
        val filters = mainActivityIntentFilters()
        assertSendFilter(filters, "android.intent.action.SEND")
        assertSendFilter(filters, "android.intent.action.SEND_MULTIPLE")
    }

    @Test
    fun mainActivityDeclaresSpecificDocumentImportFiltersWithoutCatchAll() {
        val filters = mainActivityIntentFilters()
        val expected = setOf(
            "application/octet-stream",
            "application/json",
            "application/zip",
            "application/xml",
            "text/xml",
            "text/plain"
        )

        val viewFilter = filters.firstOrNull { element ->
            element.childElements("action").any { it.androidName == "android.intent.action.VIEW" } &&
                element.mimeTypes().containsAll(expected)
        } ?: error("Missing document ACTION_VIEW filter")
        assertEquals(expected, viewFilter.mimeTypes())
        assertEquals(setOf("content"), viewFilter.dataSchemes().toSet())

        assertDocumentSendFilter(filters, "android.intent.action.SEND", expected)
        assertDocumentSendFilter(filters, "android.intent.action.SEND_MULTIPLE", expected)

        val allMimeTypes = filters.flatMap { it.childElements("data") }
            .mapNotNull { it.getAttributeNS(ANDROID_NS, "mimeType").takeIf(String::isNotBlank) }
        assertTrue("Document imports must not register an unrestricted */* filter", "*/*" !in allMimeTypes)
    }

    private fun assertSendFilter(filters: List<Element>, action: String) {
        val filter = filters.firstOrNull { element ->
            element.childElements("action").any { it.androidName == action }
        } ?: error("Missing $action intent filter")

        val mimeTypes = filter.childElements("data")
            .mapNotNull { it.getAttributeNS(ANDROID_NS, "mimeType").takeIf(String::isNotBlank) }
            .toSet()
        val dataSchemes = filter.childElements("data")
            .mapNotNull { it.getAttributeNS(ANDROID_NS, "scheme").takeIf(String::isNotBlank) }

        assertEquals(setOf("video/*", "image/*", "audio/*"), mimeTypes)
        assertTrue("$action Sharesheet filter must not require a data scheme", dataSchemes.isEmpty())
    }

    private fun assertDocumentSendFilter(filters: List<Element>, action: String, expected: Set<String>) {
        val filter = filters.firstOrNull { element ->
            element.childElements("action").any { it.androidName == action } &&
                element.mimeTypes().containsAll(expected)
        } ?: error("Missing document $action filter")
        assertEquals(expected, filter.mimeTypes())
        assertTrue("$action document Sharesheet filter must not require a data scheme", filter.dataSchemes().isEmpty())
    }

    private fun Element.mimeTypes(): Set<String> {
        return childElements("data")
            .mapNotNull { it.getAttributeNS(ANDROID_NS, "mimeType").takeIf(String::isNotBlank) }
            .toSet()
    }

    private fun Element.dataSchemes(): List<String> {
        return childElements("data")
            .mapNotNull { it.getAttributeNS(ANDROID_NS, "scheme").takeIf(String::isNotBlank) }
    }

    private fun mainActivityIntentFilters(): List<Element> {
        val manifest = File("src/main/AndroidManifest.xml")
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(manifest)
        val activities = document.getElementsByTagName("activity")
        for (index in 0 until activities.length) {
            val activity = activities.item(index) as? Element ?: continue
            if (activity.androidName == ".MainActivity") {
                return activity.childElements("intent-filter")
            }
        }
        error("MainActivity not found in manifest")
    }

    private fun Element.childElements(tagName: String): List<Element> {
        val nodes = getElementsByTagName(tagName)
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private val Element.androidName: String
        get() = getAttributeNS(ANDROID_NS, "name")

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
