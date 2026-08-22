package com.piercingxx.txxt.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Asserts the Android resource layer (T2) matches the pure-Kotlin token model
 * (T1). The resources in colors.xml / themes.xml are the concrete ARGB values
 * the running UI reads; this test locks them to the ThemePreset/ThemeTokens
 * derivation so the resource layer and the model never drift apart.
 *
 * A plain JVM unit test cannot load Android resources (that needs Robolectric,
 * which is not in the offline cache), so like ReceiverManifestTest it reads the
 * XML files from disk and parses them with the JDK's DOM parser.
 */
class ThemeResourceTest {

    // Gradle unit tests run with the module directory (app/) as the working
    // directory; fall back to the workspace-root-relative path for robustness.
    private val colorsFile: File =
        sequenceOf(
            File("src/main/res/values/colors.xml"),
            File("app/src/main/res/values/colors.xml"),
        ).first { it.exists() }

    private val themesFile: File =
        sequenceOf(
            File("src/main/res/values/themes.xml"),
            File("app/src/main/res/values/themes.xml"),
        ).first { it.exists() }

    private fun parse(file: File): Document =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)

    /** Format an ARGB long (0xAARRGGBB) as the #AARRGGBB hex a resource uses. */
    private fun Long.asHex(): String = "#%08X".format(this)

    private fun elementsNamed(node: Element, tag: String): Map<String, Element> =
        node.getElementsByTagName(tag)
            .let { nodes ->
                buildMap {
                    for (i in 0 until nodes.length) {
                        val el = nodes.item(i) as Element
                        put(el.getAttribute("name"), el)
                    }
                }
            }

    // ---- colors.xml defines the seven semantic tokens at their AMOLED values ----

    @Test
    fun `colors xml defines every semantic token with the AMOLED Night value`() {
        val colors = elementsNamed(parse(colorsFile).documentElement, "color")
        val expected = mapOf(
            "theme_background" to "#FF000000",
            "theme_surface" to "#FF0A0A0A",
            "theme_text" to "#E6FFFFFF",
            "theme_muted" to "#80FFFFFF",
            "theme_line" to "#1AFFFFFF",
            "theme_accent" to "#FFFFFFFF",
            "theme_accent_on" to "#FF000000",
        )
        assertEquals("colors.xml must define exactly the seven semantic tokens", expected.size, colors.size)
        for ((name, hex) in expected) {
            val el = colors[name]
            assertTrue("colors.xml must define $name", el != null)
            assertEquals("value of $name", hex, el!!.textContent.trim())
        }
    }

    @Test
    fun `resource colors match the T1 AMOLED Night token derivation`() {
        val t = deriveTokens(ThemePreset.AMOLED_NIGHT)
        val colors = elementsNamed(parse(colorsFile).documentElement, "color")
        // The resource layer and the model must agree on the shipped default:
        // each ARGB long (0xAARRGGBB) formatted as #AARRGGBB.
        assertEquals(t.background.asHex(), colors["theme_background"]!!.textContent.trim())
        assertEquals(t.surface.asHex(), colors["theme_surface"]!!.textContent.trim())
        assertEquals(t.text.asHex(), colors["theme_text"]!!.textContent.trim())
        assertEquals(t.muted.asHex(), colors["theme_muted"]!!.textContent.trim())
        assertEquals(t.line.asHex(), colors["theme_line"]!!.textContent.trim())
        assertEquals(t.accent.asHex(), colors["theme_accent"]!!.textContent.trim())
        assertEquals(t.accentOn.asHex(), colors["theme_accent_on"]!!.textContent.trim())
    }

    // ---- themes.xml declares the base theme and the runtime retheme seam ----

    @Test
    fun `themes xml declares the base Theme TxxT`() {
        val styles = elementsNamed(parse(themesFile).documentElement, "style")
        val style = styles["Theme.TxxT"]
        assertTrue("themes.xml must define Theme.TxxT", style != null)
    }

    @Test
    fun `themes xml declares all seven themeable attributes`() {
        val attrs = elementsNamed(parse(themesFile).documentElement, "attr")
        val expected = setOf(
            "txxtBackground",
            "txxtSurface",
            "txxtText",
            "txxtMuted",
            "txxtLine",
            "txxtAccent",
            "txxtAccentOn",
        )
        assertEquals("themes.xml must declare exactly the seven themeable attributes", expected.size, attrs.size)
        for (name in expected) {
            assertTrue("themes.xml must declare attr $name", attrs.containsKey(name))
        }
    }

    @Test
    fun `the base theme binds every attribute to its default color`() {
        val doc = parse(themesFile)
        val style = elementsNamed(doc.documentElement, "style")["Theme.TxxT"]!!
        val items = elementsNamed(style, "item")
        val expected = mapOf(
            "txxtBackground" to "@color/theme_background",
            "txxtSurface" to "@color/theme_surface",
            "txxtText" to "@color/theme_text",
            "txxtMuted" to "@color/theme_muted",
            "txxtLine" to "@color/theme_line",
            "txxtAccent" to "@color/theme_accent",
            "txxtAccentOn" to "@color/theme_accent_on",
        )
        for ((attr, res) in expected) {
            val item = items[attr]
            assertTrue("Theme.TxxT must bind $attr", item != null)
            assertEquals("binding of $attr", res, item!!.textContent.trim())
        }
    }
}