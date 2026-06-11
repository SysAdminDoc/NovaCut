package com.novacut.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class NetworkSecurityConfigTest {
    @Test
    fun manifestWiresAppWideCleartextBlock() {
        val application = parseXml(locate("AndroidManifest.xml")).documentElement
            .childElements("application")
            .single()

        assertEquals("false", application.getAttributeNS(ANDROID_NS, "usesCleartextTraffic"))
        assertEquals("@xml/network_security_config", application.getAttributeNS(ANDROID_NS, "networkSecurityConfig"))
    }

    @Test
    fun networkSecurityConfigDisablesCleartextByDefault() {
        val config = parseXml(locate("res/xml/network_security_config.xml")).documentElement
        val baseConfig = config.childElements("base-config").single()
        val trustAnchors = baseConfig.childElements("trust-anchors").single()
        val systemCertificates = trustAnchors.childElements("certificates")
            .firstOrNull { it.getAttribute("src") == "system" }

        assertEquals("false", baseConfig.getAttribute("cleartextTrafficPermitted"))
        assertNotNull(systemCertificates)
    }

    private fun locate(relativePath: String): File {
        val candidates = listOf(
            File("app/src/main/$relativePath"),
            File("src/main/$relativePath"),
            File("../app/src/main/$relativePath"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("$relativePath not found")
    }

    private fun parseXml(file: File) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(file)

    private fun Element.childElements(tagName: String): List<Element> {
        val nodes = getElementsByTagName(tagName)
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
