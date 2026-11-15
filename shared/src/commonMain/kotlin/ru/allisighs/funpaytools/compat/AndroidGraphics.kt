package ru.allisighs.funpaytools

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.RRect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import org.jetbrains.skia.TextLine
import org.jetbrains.skia.Rect as SkRect
import androidx.compose.ui.graphics.nativeCanvas as composeNativeCanvas

/** android.graphics.Bitmap backed by a Skia bitmap. */
class Bitmap internal constructor(internal val sk: org.jetbrains.skia.Bitmap) {
    enum class Config { ARGB_8888, RGB_565, ALPHA_8, ARGB_4444, HARDWARE, RGBA_F16 }
    enum class CompressFormat { JPEG, PNG, WEBP, WEBP_LOSSY, WEBP_LOSSLESS }

    val width: Int get() = sk.width
    val height: Int get() = sk.height
    val byteCount: Int get() = sk.width * sk.height * 4
    val allocationByteCount: Int get() = byteCount
    val config: Config get() = Config.ARGB_8888
    var isRecycled: Boolean = false
        private set
    val isMutable: Boolean get() = true
    var density: Int = 0

    fun recycle() { isRecycled = true }
    fun hasAlpha(): Boolean = true

    fun getPixel(x: Int, y: Int): Int = sk.getColor(x.coerceIn(0, width - 1), y.coerceIn(0, height - 1))
    fun setPixel(x: Int, y: Int, color: Int) {
        val c = Canvas(this)
        c.drawPoint(x.toFloat(), y.toFloat(), Paint().also { it.color = color })
    }
    fun getPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, w: Int, h: Int) {
        for (row in 0 until h) for (col in 0 until w) pixels[offset + row * stride + col] = sk.getColor(x + col, y + row)
    }
    fun eraseColor(color: Int) { Canvas(this).drawColor(color) }

    fun compress(format: CompressFormat, quality: Int, out: OutputStream): Boolean {
        val img = Image.makeFromBitmap(sk)
        val fmt = when (format) {
            CompressFormat.PNG -> EncodedImageFormat.PNG
            CompressFormat.JPEG -> EncodedImageFormat.JPEG
            else -> EncodedImageFormat.WEBP
        }
        val data = img.encodeToData(fmt, quality) ?: img.encodeToData(EncodedImageFormat.PNG) ?: return false
        out.write(data.bytes)
        return true
    }

    fun copy(config: Config?, mutable: Boolean): Bitmap = createBitmap(this)
    fun asImageBitmap(): ImageBitmap = sk.asComposeImageBitmap()
    fun asComposeImageBitmap(): ImageBitmap = sk.asComposeImageBitmap()
    fun extractAlpha(): Bitmap = this
    fun prepareToDraw() {}
    fun sameAs(o: Bitmap?): Boolean = o === this

    companion object {
        fun createBitmap(w: Int, h: Int, config: Config = Config.ARGB_8888): Bitmap {
            val b = org.jetbrains.skia.Bitmap()
            b.allocPixels(ImageInfo.makeN32(w.coerceAtLeast(1), h.coerceAtLeast(1), ColorAlphaType.PREMUL))
            b.erase(0)
            return Bitmap(b)
        }
        fun createBitmap(src: Bitmap): Bitmap {
            val out = createBitmap(src.width, src.height)
            Canvas(out).drawBitmap(src, 0f, 0f, null)
            return out
        }
        fun createBitmap(src: Bitmap, x: Int, y: Int, w: Int, h: Int): Bitmap {
            val out = createBitmap(w, h)
            Canvas(out).drawBitmap(src, Rect(x, y, x + w, y + h), Rect(0, 0, w, h), null)
            return out
        }
        fun createBitmap(src: Bitmap, x: Int, y: Int, w: Int, h: Int, m: Matrix?, filter: Boolean): Bitmap {
            if (m == null) return createBitmap(src, x, y, w, h)
            val pts = floatArrayOf(0f, 0f, w.toFloat(), 0f, 0f, h.toFloat(), w.toFloat(), h.toFloat())
            m.mapPoints(pts)
            val minX = pts.filterIndexed { i, _ -> i % 2 == 0 }.min(); val maxX = pts.filterIndexed { i, _ -> i % 2 == 0 }.max()
            val minY = pts.filterIndexed { i, _ -> i % 2 == 1 }.min(); val maxY = pts.filterIndexed { i, _ -> i % 2 == 1 }.max()
            val out = createBitmap((maxX - minX).toInt().coerceAtLeast(1), (maxY - minY).toInt().coerceAtLeast(1))
            val c = Canvas(out)
            c.translate(-minX, -minY)
            c.concat(m)
            c.drawBitmap(src, Rect(x, y, x + w, y + h), Rect(0, 0, w, h), Paint().also { it.isFilterBitmap = filter })
            return out
        }
        fun createScaledBitmap(src: Bitmap, w: Int, h: Int, filter: Boolean): Bitmap {
            val out = createBitmap(w, h)
            Canvas(out).drawBitmap(src, Rect(0, 0, src.width, src.height), Rect(0, 0, w, h), Paint().also { it.isFilterBitmap = filter })
            return out
        }
    }
}

fun ImageBitmap.asAndroidBitmap(): Bitmap = Bitmap(this.asSkiaBitmap())

object BitmapFactory {
    class Options {
        var inJustDecodeBounds = false
        var inSampleSize = 1
        var inPreferredConfig: Bitmap.Config? = null
        var inMutable = false
        var inScaled = true
        var outWidth = 0
        var outHeight = 0
        var outMimeType: String? = null
        var inDensity = 0
        var inTargetDensity = 0
    }

    fun decodeByteArray(data: ByteArray?, offset: Int, length: Int): Bitmap? = decodeByteArray(data, offset, length, null)

    fun decodeByteArray(data: ByteArray?, offset: Int, length: Int, opts: Options?): Bitmap? {
        if (data == null || length <= 0) return null
        return try {
            val bytes = if (offset == 0 && length == data.size) data else data.copyOfRange(offset, offset + length)
            val img = Image.makeFromEncoded(bytes)
            if (opts != null) { opts.outWidth = img.width; opts.outHeight = img.height }
            if (opts?.inJustDecodeBounds == true) return null
            val sample = (opts?.inSampleSize ?: 1).coerceAtLeast(1)
            val w = (img.width / sample).coerceAtLeast(1)
            val h = (img.height / sample).coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h)
            val surfaceCanvas = org.jetbrains.skia.Canvas(bmp.sk)
            surfaceCanvas.drawImageRect(img, SkRect.makeWH(img.width.toFloat(), img.height.toFloat()), SkRect.makeWH(w.toFloat(), h.toFloat()), SamplingMode.LINEAR, null, true)
            surfaceCanvas.close()
            bmp
        } catch (e: Exception) {
            null
        }
    }

    fun decodeStream(input: InputStream?): Bitmap? = input?.let { s -> s.readBytes().let { decodeByteArray(it, 0, it.size) } }
    fun decodeStream(input: InputStream?, pad: Rect?, opts: Options?): Bitmap? = input?.let { s -> s.readBytes().let { decodeByteArray(it, 0, it.size, opts) } }
    fun decodeFile(path: String?): Bitmap? = decodeFile(path, null)
    fun decodeFile(path: String?, opts: Options?): Bitmap? {
        if (path == null) return null
        val f = File(path)
        if (!f.exists()) return null
        val b = f.readBytes()
        return decodeByteArray(b, 0, b.size, opts)
    }
    fun decodeResource(res: Any?, id: Int): Bitmap? = null
}

class Rect(var left: Int = 0, var top: Int = 0, var right: Int = 0, var bottom: Int = 0) {
    fun width() = right - left
    fun height() = bottom - top
    fun centerX() = (left + right) / 2
    fun centerY() = (top + bottom) / 2
    fun set(l: Int, t: Int, r: Int, b: Int) { left = l; top = t; right = r; bottom = b }
    fun exactCenterX() = (left + right) / 2f
    fun exactCenterY() = (top + bottom) / 2f
    internal fun sk() = SkRect.makeLTRB(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
}

class RectF(var left: Float = 0f, var top: Float = 0f, var right: Float = 0f, var bottom: Float = 0f) {
    constructor(r: Rect) : this(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())
    fun width() = right - left
    fun height() = bottom - top
    fun centerX() = (left + right) / 2f
    fun centerY() = (top + bottom) / 2f
    fun set(l: Float, t: Float, r: Float, b: Float) { left = l; top = t; right = r; bottom = b }
    fun inset(dx: Float, dy: Float) { left += dx; top += dy; right -= dx; bottom -= dy }
    fun offset(dx: Float, dy: Float) { left += dx; right += dx; top += dy; bottom += dy }
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
    internal fun sk() = SkRect.makeLTRB(left, top, right, bottom)
}

class PointF(var x: Float = 0f, var y: Float = 0f)

class Matrix {
    // row-major 3x3
    internal var m = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    private fun post(o: FloatArray) { m = mul(o, m) }
    private fun pre(o: FloatArray) { m = mul(m, o) }
    private fun mul(a: FloatArray, b: FloatArray): FloatArray {
        val r = FloatArray(9)
        for (i in 0..2) for (j in 0..2) { var s = 0f; for (k in 0..2) s += a[i * 3 + k] * b[k * 3 + j]; r[i * 3 + j] = s }
        return r
    }
    private fun scaleM(sx: Float, sy: Float, px: Float = 0f, py: Float = 0f) = floatArrayOf(sx, 0f, px - sx * px, 0f, sy, py - sy * py, 0f, 0f, 1f)
    private fun transM(dx: Float, dy: Float) = floatArrayOf(1f, 0f, dx, 0f, 1f, dy, 0f, 0f, 1f)
    private fun rotM(deg: Float, px: Float = 0f, py: Float = 0f): FloatArray {
        val r = deg.toDouble() / 180.0 * kotlin.math.PI
        val c = kotlin.math.cos(r).toFloat(); val s = kotlin.math.sin(r).toFloat()
        return floatArrayOf(c, -s, px - c * px + s * py, s, c, py - s * px - c * py, 0f, 0f, 1f)
    }

    fun reset() { m = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f) }
    fun set(o: Matrix) { m = o.m.copyOf() }
    fun setScale(sx: Float, sy: Float) { m = scaleM(sx, sy) }
    fun setScale(sx: Float, sy: Float, px: Float, py: Float) { m = scaleM(sx, sy, px, py) }
    fun setTranslate(dx: Float, dy: Float) { m = transM(dx, dy) }
    fun setRotate(d: Float) { m = rotM(d) }
    fun setRotate(d: Float, px: Float, py: Float) { m = rotM(d, px, py) }
    fun postScale(sx: Float, sy: Float): Boolean { post(scaleM(sx, sy)); return true }
    fun postScale(sx: Float, sy: Float, px: Float, py: Float): Boolean { post(scaleM(sx, sy, px, py)); return true }
    fun postTranslate(dx: Float, dy: Float): Boolean { post(transM(dx, dy)); return true }
    fun postRotate(d: Float): Boolean { post(rotM(d)); return true }
    fun postRotate(d: Float, px: Float, py: Float): Boolean { post(rotM(d, px, py)); return true }
    fun preScale(sx: Float, sy: Float): Boolean { pre(scaleM(sx, sy)); return true }
    fun preTranslate(dx: Float, dy: Float): Boolean { pre(transM(dx, dy)); return true }
    fun preRotate(d: Float): Boolean { pre(rotM(d)); return true }
    fun postConcat(o: Matrix): Boolean { post(o.m); return true }
    fun preConcat(o: Matrix): Boolean { pre(o.m); return true }
    fun mapPoints(pts: FloatArray) {
        var i = 0
        while (i + 1 < pts.size) {
            val x = pts[i]; val y = pts[i + 1]
            pts[i] = m[0] * x + m[1] * y + m[2]
            pts[i + 1] = m[3] * x + m[4] * y + m[5]
            i += 2
        }
    }
    internal fun sk() = Matrix33(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8])
}

class BlurMaskFilter(val radius: Float, val style: Blur) {
    enum class Blur { NORMAL, SOLID, OUTER, INNER }
}

class Typeface private constructor(internal val sk: org.jetbrains.skia.Typeface?, val style: Int) {
    val isBold: Boolean get() = style and BOLD != 0
    val isItalic: Boolean get() = style and ITALIC != 0
    companion object {
        const val NORMAL = 0
        const val BOLD = 1
        const val ITALIC = 2
        const val BOLD_ITALIC = 3

        private fun family(name: String?, style: Int): Typeface {
            val fs = when (style) { BOLD -> FontStyle.BOLD; ITALIC -> FontStyle.ITALIC; BOLD_ITALIC -> FontStyle.BOLD_ITALIC; else -> FontStyle.NORMAL }
            val tf = try { FontMgr.default.matchFamilyStyle(name, fs) ?: FontMgr.default.matchFamilyStyle(null, fs) } catch (e: Exception) { null }
            return Typeface(tf, style)
        }

        val DEFAULT: Typeface by lazy { family(null, NORMAL) }
        val DEFAULT_BOLD: Typeface by lazy { family(null, BOLD) }
        val SANS_SERIF: Typeface by lazy { family("Helvetica Neue", NORMAL) }
        val SERIF: Typeface by lazy { family("Times New Roman", NORMAL) }
        val MONOSPACE: Typeface by lazy { family("Menlo", NORMAL).let { if (it.sk == null) family("Courier New", NORMAL) else it } }

        fun create(family: String?, style: Int): Typeface = family(family, style)
        fun create(base: Typeface?, style: Int): Typeface {
            val name = base?.sk?.familyName
            return family(name, style)
        }
        fun defaultFromStyle(style: Int): Typeface = family(null, style)
        fun createFromFile(path: String): Typeface {
            val tf = try { FontMgr.default.makeFromFile(path) } catch (e: Exception) { null }
            return Typeface(tf, NORMAL)
        }
        fun createFromFile(f: File): Typeface = createFromFile(f.path)
        fun createFromAsset(mgr: Any?, path: String): Typeface = DEFAULT
        fun systemFamilies(): List<String> = try { (0 until FontMgr.default.familiesCount).map { FontMgr.default.getFamilyName(it) } } catch (e: Exception) { emptyList() }
    }
}

class Paint(flags: Int = 0) {
    enum class Align { LEFT, CENTER, RIGHT }
    enum class Style { FILL, STROKE, FILL_AND_STROKE }
    enum class Cap { BUTT, ROUND, SQUARE }
    enum class Join { MITER, ROUND, BEVEL }

    companion object {
        const val ANTI_ALIAS_FLAG = 1
        const val FILTER_BITMAP_FLAG = 2
        const val DITHER_FLAG = 4
        const val FAKE_BOLD_TEXT_FLAG = 32
    }

    var isAntiAlias: Boolean = flags and ANTI_ALIAS_FLAG != 0
    var isFilterBitmap: Boolean = flags and FILTER_BITMAP_FLAG != 0
    var isDither: Boolean = false
    var isFakeBoldText: Boolean = flags and FAKE_BOLD_TEXT_FLAG != 0
    var color: Int = AndroidColor.BLACK
    var alpha: Int
        get() = AndroidColor.alpha(color)
        set(v) { color = (color and 0x00ffffff) or ((v.coerceIn(0, 255)) shl 24) }
    var style: Style = Style.FILL
    var strokeWidth: Float = 0f
    var strokeCap: Cap = Cap.BUTT
    var strokeJoin: Join = Join.MITER
    var textSize: Float = 12f
    var textAlign: Align = Align.LEFT
    var typeface: Typeface? = null
    var letterSpacing: Float = 0f
    var textSkewX: Float = 0f
    var textScaleX: Float = 1f
    var maskFilter: BlurMaskFilter? = null
    var shader: Any? = null
    var xfermode: Any? = null
    var colorFilter: Any? = null
    internal var shadow: FloatArray? = null
    internal var shadowColor: Int = 0

    fun setShadowLayer(radius: Float, dx: Float, dy: Float, color: Int) { shadow = floatArrayOf(radius, dx, dy); shadowColor = color }
    fun clearShadowLayer() { shadow = null }
    fun setARGB(a: Int, r: Int, g: Int, b: Int) { color = AndroidColor.argb(a, r, g, b) }
    fun set(o: Paint) {
        isAntiAlias = o.isAntiAlias; color = o.color; style = o.style; strokeWidth = o.strokeWidth; textSize = o.textSize
        textAlign = o.textAlign; typeface = o.typeface; maskFilter = o.maskFilter; shadow = o.shadow; shadowColor = o.shadowColor
        letterSpacing = o.letterSpacing; isFakeBoldText = o.isFakeBoldText
    }

    internal fun font(): Font {
        val tf = typeface?.sk ?: Typeface.DEFAULT.sk
        val f = if (tf != null) Font(tf, textSize) else Font(null, textSize)
        if (isFakeBoldText || (typeface?.isBold == true && tf?.isBold != true)) f.isEmboldened = true
        f.skewX = textSkewX
        f.scaleX = textScaleX
        f.isSubpixel = true
        f.edging = if (isAntiAlias) org.jetbrains.skia.FontEdging.ANTI_ALIAS else org.jetbrains.skia.FontEdging.ALIAS
        return f
    }

    internal fun sk(): org.jetbrains.skia.Paint {
        val p = org.jetbrains.skia.Paint()
        p.isAntiAlias = isAntiAlias
        p.color = color
        p.mode = when (style) { Style.FILL -> PaintMode.FILL; Style.STROKE -> PaintMode.STROKE; Style.FILL_AND_STROKE -> PaintMode.STROKE_AND_FILL }
        p.strokeWidth = strokeWidth
        p.strokeCap = when (strokeCap) { Cap.BUTT -> org.jetbrains.skia.PaintStrokeCap.BUTT; Cap.ROUND -> org.jetbrains.skia.PaintStrokeCap.ROUND; Cap.SQUARE -> org.jetbrains.skia.PaintStrokeCap.SQUARE }
        maskFilter?.let { mf ->
            val mode = when (mf.style) { BlurMaskFilter.Blur.NORMAL -> FilterBlurMode.NORMAL; BlurMaskFilter.Blur.SOLID -> FilterBlurMode.SOLID; BlurMaskFilter.Blur.OUTER -> FilterBlurMode.OUTER; BlurMaskFilter.Blur.INNER -> FilterBlurMode.INNER }
            p.maskFilter = MaskFilter.makeBlur(mode, (mf.radius / 2f).coerceAtLeast(0.1f))
        }
        shadow?.let { s ->
            p.imageFilter = ImageFilter.makeDropShadow(s[1], s[2], (s[0] / 2f).coerceAtLeast(0.1f), (s[0] / 2f).coerceAtLeast(0.1f), shadowColor)
        }
        return p
    }

    fun measureText(text: String): Float {
        if (text.isEmpty()) return 0f
        val tl = TextLine.make(text, font())
        return tl.width + letterSpacing * textSize * (text.length - 1).coerceAtLeast(0)
    }
    fun measureText(text: CharSequence, start: Int, end: Int): Float = measureText(text.substring(start, end))
    fun measureText(chars: CharArray, index: Int, count: Int): Float = measureText(chars.concatToString(index, index + count))

    fun getTextBounds(text: String, start: Int, end: Int, bounds: Rect) {
        val sub = text.substring(start, end)
        val f = font()
        val tl = TextLine.make(sub, f)
        val m = f.metrics
        bounds.set(0, m.ascent.toInt(), tl.width.toInt(), m.descent.toInt())
    }

    fun ascent(): Float = font().metrics.ascent
    fun descent(): Float = font().metrics.descent
    @kotlin.jvm.JvmName("getFontSpacingFn") fun getFontSpacing(): Float = font().spacing
    val fontSpacing: Float get() = font().spacing
    val fontMetrics: FontMetrics get() = font().metrics.let { FontMetrics(it.top, it.ascent, it.descent, it.bottom, it.leading) }
    fun getFontMetrics(fm: FontMetrics?): Float { val m = font().metrics; fm?.apply { top = m.top; ascent = m.ascent; descent = m.descent; bottom = m.bottom; leading = m.leading }; return m.descent - m.ascent + m.leading }
    val fontMetricsInt: FontMetricsInt get() = font().metrics.let { FontMetricsInt(it.top.toInt(), it.ascent.toInt(), it.descent.toInt(), it.bottom.toInt(), it.leading.toInt()) }

    class FontMetrics(var top: Float = 0f, var ascent: Float = 0f, var descent: Float = 0f, var bottom: Float = 0f, var leading: Float = 0f)
    class FontMetricsInt(var top: Int = 0, var ascent: Int = 0, var descent: Int = 0, var bottom: Int = 0, var leading: Int = 0)
}

/** android.graphics.Canvas implemented on top of a Skia canvas. */
class Canvas internal constructor(internal val sk: org.jetbrains.skia.Canvas) {
    constructor(bitmap: Bitmap) : this(org.jetbrains.skia.Canvas(bitmap.sk))

    val width: Int get() = 0
    val height: Int get() = 0

    fun save(): Int = sk.save()
    fun restore() = sk.restore()
    fun restoreToCount(c: Int) = sk.restoreToCount(c)
    fun saveLayer(bounds: RectF?, paint: Paint?): Int = sk.saveLayer(bounds?.sk(), paint?.sk())
    fun translate(dx: Float, dy: Float) { sk.translate(dx, dy) }
    fun scale(sx: Float, sy: Float) { sk.scale(sx, sy) }
    fun scale(sx: Float, sy: Float, px: Float, py: Float) { sk.translate(px, py); sk.scale(sx, sy); sk.translate(-px, -py) }
    fun rotate(deg: Float) { sk.rotate(deg) }
    fun rotate(deg: Float, px: Float, py: Float) { sk.translate(px, py); sk.rotate(deg); sk.translate(-px, -py) }
    fun skew(sx: Float, sy: Float) { sk.skew(sx, sy) }
    fun concat(m: Matrix) { sk.concat(m.sk()) }
    fun clipRect(r: RectF): Boolean { sk.clipRect(r.sk()); return true }
    fun clipRect(l: Float, t: Float, r: Float, b: Float): Boolean { sk.clipRect(SkRect.makeLTRB(l, t, r, b)); return true }

    fun drawColor(color: Int) { sk.clear(0); sk.drawPaint(org.jetbrains.skia.Paint().also { it.color = color }) }
    fun drawColor(color: Int, mode: Any?) { sk.drawPaint(org.jetbrains.skia.Paint().also { it.color = color }) }
    fun drawARGB(a: Int, r: Int, g: Int, b: Int) { sk.drawPaint(org.jetbrains.skia.Paint().also { it.color = AndroidColor.argb(a, r, g, b) }) }
    fun drawRGB(r: Int, g: Int, b: Int) = drawARGB(255, r, g, b)
    fun drawPaint(p: Paint) { sk.drawPaint(p.sk()) }
    fun drawPoint(x: Float, y: Float, p: Paint) { sk.drawPoint(x, y, p.sk()) }
    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, p: Paint) { sk.drawLine(x0, y0, x1, y1, p.sk()) }
    fun drawRect(r: RectF, p: Paint) { sk.drawRect(r.sk(), p.sk()) }
    fun drawRect(r: Rect, p: Paint) { sk.drawRect(r.sk(), p.sk()) }
    fun drawRect(l: Float, t: Float, r: Float, b: Float, p: Paint) { sk.drawRect(SkRect.makeLTRB(l, t, r, b), p.sk()) }
    fun drawRoundRect(r: RectF, rx: Float, ry: Float, p: Paint) { sk.drawRRect(RRect.makeLTRB(r.left, r.top, r.right, r.bottom, rx, ry), p.sk()) }
    fun drawRoundRect(l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float, p: Paint) { sk.drawRRect(RRect.makeLTRB(l, t, r, b, rx, ry), p.sk()) }
    fun drawCircle(cx: Float, cy: Float, radius: Float, p: Paint) { sk.drawCircle(cx, cy, radius, p.sk()) }
    fun drawOval(r: RectF, p: Paint) { sk.drawOval(r.sk(), p.sk()) }
    fun drawArc(r: RectF, start: Float, sweep: Float, useCenter: Boolean, p: Paint) { sk.drawArc(r.left, r.top, r.right, r.bottom, start, sweep, useCenter, p.sk()) }

    fun drawText(text: String, x: Float, y: Float, p: Paint) {
        if (text.isEmpty()) return
        val font = p.font()
        val w = p.measureText(text)
        val dx = when (p.textAlign) { Paint.Align.LEFT -> 0f; Paint.Align.CENTER -> -w / 2f; Paint.Align.RIGHT -> -w }
        if (p.letterSpacing == 0f) {
            sk.drawTextLine(TextLine.make(text, font), x + dx, y, p.sk())
        } else {
            var cx = x + dx
            val extra = p.letterSpacing * p.textSize
            val sp = p.sk()
            for (cp in text.codePointsList()) {
                val s = codePointToString(cp)
                val tl = TextLine.make(s, font)
                sk.drawTextLine(tl, cx, y, sp)
                cx += tl.width + extra
            }
        }
    }
    fun drawText(text: CharSequence, start: Int, end: Int, x: Float, y: Float, p: Paint) = drawText(text.substring(start, end), x, y, p)
    fun drawText(chars: CharArray, index: Int, count: Int, x: Float, y: Float, p: Paint) = drawText(chars.concatToString(index, index + count), x, y, p)

    private fun sampling(p: Paint?): SamplingMode = if (p == null || p.isFilterBitmap || p.isAntiAlias) SamplingMode.LINEAR else SamplingMode.DEFAULT

    fun drawBitmap(b: Bitmap, left: Float, top: Float, p: Paint?) {
        val img = Image.makeFromBitmap(b.sk)
        sk.drawImageRect(img, SkRect.makeWH(b.width.toFloat(), b.height.toFloat()), SkRect.makeXYWH(left, top, b.width.toFloat(), b.height.toFloat()), sampling(p), p?.sk(), true)
    }
    fun drawBitmap(b: Bitmap, src: Rect?, dst: RectF, p: Paint?) {
        val img = Image.makeFromBitmap(b.sk)
        val s = src?.sk() ?: SkRect.makeWH(b.width.toFloat(), b.height.toFloat())
        sk.drawImageRect(img, s, dst.sk(), sampling(p), p?.sk(), true)
    }
    fun drawBitmap(b: Bitmap, src: Rect?, dst: Rect, p: Paint?) {
        val img = Image.makeFromBitmap(b.sk)
        val s = src?.sk() ?: SkRect.makeWH(b.width.toFloat(), b.height.toFloat())
        sk.drawImageRect(img, s, dst.sk(), sampling(p), p?.sk(), true)
    }
    fun drawBitmap(b: Bitmap, m: Matrix, p: Paint?) {
        sk.save()
        sk.concat(m.sk())
        drawBitmap(b, 0f, 0f, p)
        sk.restore()
    }
}

/** Compose DrawScope canvas -> android-like canvas. */
val androidx.compose.ui.graphics.Canvas.nativeCanvas: Canvas get() = Canvas(this.composeNativeCanvas)
