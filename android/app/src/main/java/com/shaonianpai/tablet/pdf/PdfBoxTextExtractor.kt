package com.shaonianpai.tablet.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.io.StringWriter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class TextGlyphBox(
    val text: String,
    val rect: RectNorm
)

data class PageTextIndex(
    val pageIndex: Int,
    val glyphs: List<TextGlyphBox>,
    val fullText: String
) {
    /**
     * Given a drag rectangle in normalized page coordinates, returns tight line-merged bounding boxes
     * of intersecting PDF text glyphs plus the captured text string.
     */
    fun selectByDrag(dragRect: RectNorm): Pair<List<RectNorm>, String> {
        if (glyphs.isEmpty()) return emptyList<RectNorm>() to ""
        val d = dragRect.normalized()
        // Expand vertically slightly for comfortable finger selection
        val hit = glyphs.filter { g ->
            val r = g.rect
            val interL = max(r.left, d.left)
            val interR = min(r.right, d.right)
            val interT = max(r.top, d.top)
            val interB = min(r.bottom, d.bottom)
            interR > interL && interB > interT
        }
        if (hit.isEmpty()) return emptyList<RectNorm>() to ""

        // Merge glyphs on the same visual line into clean horizontal markup bars
        val sorted = hit.sortedWith(compareBy<TextGlyphBox> { (it.rect.top * 80).toInt() }.thenBy { it.rect.left })
        val merged = mutableListOf<RectNorm>()
        val textSb = StringBuilder()

        var cur: RectNorm? = null
        for (g in sorted) {
            textSb.append(g.text)
            val c = cur
            if (c == null) {
                cur = g.rect
            } else {
                val cMid = (c.top + c.bottom) * 0.5f
                val gMid = (g.rect.top + g.rect.bottom) * 0.5f
                val lineH = max(c.bottom - c.top, g.rect.bottom - g.rect.top)
                if (abs(cMid - gMid) <= max(lineH * 0.75f, 0.012f) && g.rect.left <= c.right + 0.045f) {
                    cur = RectNorm(
                        min(c.left, g.rect.left),
                        min(c.top, g.rect.top),
                        max(c.right, g.rect.right),
                        max(c.bottom, g.rect.bottom)
                    )
                } else {
                    merged.add(c)
                    cur = g.rect
                }
            }
        }
        if (cur != null) merged.add(cur)
        return merged to textSb.toString().trim()
    }
}

data class OutlineEntry(
    val title: String,
    val pageIndex: Int,
    val depth: Int
)

data class SearchHit(
    val pageIndex: Int,
    val snippet: String,
    val rects: List<RectNorm>
)

sealed class PdfOpenCheck {
    data class Ready(val renderFile: File, val password: String?) : PdfOpenCheck()
    object PasswordRequired : PdfOpenCheck()
    data class Error(val message: String) : PdfOpenCheck()
}

object PdfBoxTextExtractor {
    @Volatile
    private var initialized = false

    fun ensureInit(context: Context) {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    PDFBoxResourceLoader.init(context.applicationContext)
                    initialized = true
                }
            }
        }
    }

    /**
     * Checks if PDF is encrypted; if encrypted and password is provided, writes an unencrypted temp copy
     * so Android's native PdfRenderer can render it smoothly.
     */
    fun prepareRenderableFile(context: Context, source: File, password: String?): PdfOpenCheck {
        ensureInit(context)
        return try {
            val doc = if (password.isNullOrEmpty()) PDDocument.load(source) else PDDocument.load(source, password)
            doc.use { pd ->
                if (!pd.isEncrypted) {
                    PdfOpenCheck.Ready(source, null)
                } else {
                    pd.isAllSecurityToBeRemoved = true
                    val tmp = File(context.cacheDir, "unlocked_${source.name}")
                    pd.save(tmp)
                    PdfOpenCheck.Ready(tmp, password)
                }
            }
        } catch (_: InvalidPasswordException) {
            PdfOpenCheck.PasswordRequired
        } catch (e: Exception) {
            // Fall back to direct file if PDFBox failed on an unusual non-encrypted PDF
            PdfOpenCheck.Ready(source, null)
        }
    }

    fun extractPageText(context: Context, file: File, pageIndex: Int, password: String? = null): PageTextIndex {
        ensureInit(context)
        return try {
            val doc = if (password.isNullOrEmpty()) PDDocument.load(file) else PDDocument.load(file, password)
            doc.use { pd ->
                extractPageTextFromDoc(pd, pageIndex)
            }
        } catch (_: Exception) {
            PageTextIndex(pageIndex, emptyList(), "")
        }
    }

    private fun extractPageTextFromDoc(pd: PDDocument, pageIndex: Int): PageTextIndex {
        if (pageIndex < 0 || pageIndex >= pd.numberOfPages) {
            return PageTextIndex(pageIndex, emptyList(), "")
        }
        val page = pd.getPage(pageIndex)
        val box = page.cropBox ?: page.mediaBox
        val pw = box.width.coerceAtLeast(1f)
        val ph = box.height.coerceAtLeast(1f)
        val glyphs = mutableListOf<TextGlyphBox>()
        val fullSb = StringBuilder()

        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
                if (textPositions == null) return
                for (tp in textPositions) {
                    val ch = tp.unicode ?: continue
                    if (ch.isEmpty()) continue
                    fullSb.append(ch)
                    if (ch.isBlank()) continue
                    val x = (tp.xDirAdj / pw).coerceIn(0f, 1f)
                    val w = (tp.widthDirAdj / pw).coerceAtLeast(0.004f)
                    val h = (tp.heightDir / ph).coerceAtLeast(0.012f)
                    val top = ((tp.yDirAdj - tp.heightDir * 0.92f) / ph).coerceIn(0f, 1f)
                    val bot = ((tp.yDirAdj + tp.heightDir * 0.22f) / ph).coerceIn(0f, 1f)
                    glyphs.add(
                        TextGlyphBox(
                            text = ch,
                            rect = RectNorm(x, top, min(1f, x + w), max(bot, top + h * 0.8f))
                        )
                    )
                }
            }
        }
        stripper.startPage = pageIndex + 1
        stripper.endPage = pageIndex + 1
        stripper.writeText(pd, StringWriter())
        return PageTextIndex(pageIndex, glyphs, fullSb.toString())
    }

    fun extractOutline(context: Context, file: File, password: String? = null): List<OutlineEntry> {
        ensureInit(context)
        val result = mutableListOf<OutlineEntry>()
        return try {
            val doc = if (password.isNullOrEmpty()) PDDocument.load(file) else PDDocument.load(file, password)
            doc.use { pd ->
                val root = pd.documentCatalog.documentOutline ?: return result
                fun walk(item: PDOutlineItem?, depth: Int) {
                    var curr = item
                    while (curr != null) {
                        val node = curr
                        val destPage = runCatching { node.findDestinationPage(pd) }.getOrNull()
                        val idx = if (destPage != null) pd.pages.indexOf(destPage) else -1
                        if (idx >= 0) {
                            result.add(OutlineEntry(node.title ?: "章节", idx, depth))
                        }
                        walk(node.firstChild, depth + 1)
                        curr = node.nextSibling
                    }
                }
                walk(root.firstChild, 0)
                result
            }
        } catch (_: Exception) {
            result
        }
    }

    fun searchAllPages(context: Context, file: File, query: String, password: String? = null): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        ensureInit(context)
        val hits = mutableListOf<SearchHit>()
        return try {
            val doc = if (password.isNullOrEmpty()) PDDocument.load(file) else PDDocument.load(file, password)
            doc.use { pd ->
                val qLower = q.lowercase()
                for (p in 0 until pd.numberOfPages) {
                    val idx = extractPageTextFromDoc(pd, p)
                    if (idx.glyphs.isEmpty()) continue
                    // Build dense non-whitespace index for reliable Chinese/English substring search
                    val denseChars = StringBuilder()
                    val glyphRef = ArrayList<TextGlyphBox>(idx.glyphs.size)
                    for (g in idx.glyphs) {
                        for (c in g.text) {
                            if (!c.isWhitespace()) {
                                denseChars.append(c.lowercaseChar())
                                glyphRef.add(g)
                            }
                        }
                    }
                    val denseQuery = qLower.filter { !it.isWhitespace() }
                    if (denseQuery.isEmpty()) continue
                    val hay = denseChars.toString()
                    var pos = hay.indexOf(denseQuery)
                    while (pos >= 0) {
                        val end = min(pos + denseQuery.length, glyphRef.size)
                        val rects = mutableListOf<RectNorm>()
                        var cur: RectNorm? = null
                        for (i in pos until end) {
                            val r = glyphRef[i].rect
                            val c = cur
                            if (c == null) {
                                cur = r
                            } else if (abs((c.top + c.bottom) - (r.top + r.bottom)) < 0.03f) {
                                cur = RectNorm(min(c.left, r.left), min(c.top, r.top), max(c.right, r.right), max(c.bottom, r.bottom))
                            } else {
                                rects.add(c)
                                cur = r
                            }
                        }
                        if (cur != null) rects.add(cur)
                        val snipStart = max(0, pos - 8)
                        val snipEnd = min(hay.length, end + 14)
                        val snippet = hay.substring(snipStart, snipEnd)
                        hits.add(SearchHit(p, snippet, rects))
                        pos = hay.indexOf(denseQuery, end)
                    }
                }
                hits
            }
        } catch (_: Exception) {
            hits
        }
    }
}
