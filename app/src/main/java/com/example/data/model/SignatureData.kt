package com.example.data.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Base64
import java.io.ByteArrayOutputStream
import org.json.JSONArray
import org.json.JSONObject

data class Point(
    val x: Float,
    val y: Float,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Retorna o ponto com coordenadas normalizadas (0.0f a 1.0f)
     */
    fun toNormalized(canvasW: Float, canvasH: Float): Point {
        val safeW = if (canvasW > 0f) canvasW else 1f
        val safeH = if (canvasH > 0f) canvasH else 1f
        return Point(
            x = (x / safeW).coerceIn(0f, 1f),
            y = (y / safeH).coerceIn(0f, 1f),
            timestamp = timestamp
        )
    }

    /**
     * Converte de coordenadas normalizadas (0.0f a 1.0f) para pixels na resolução alvo
     */
    fun toAbsolute(canvasW: Float, canvasH: Float): Point {
        return Point(
            x = x * canvasW,
            y = y * canvasH,
            timestamp = timestamp
        )
    }
}

data class Stroke(
    val points: List<Point> = emptyList(),
    val color: Int = 0xFF0D47A1.toInt(),
    val strokeWidth: Float = 5.5f
) {
    fun toNormalized(canvasW: Float, canvasH: Float): Stroke {
        return copy(points = points.map { it.toNormalized(canvasW, canvasH) })
    }
}

data class SignatureData(
    val strokes: List<Stroke> = emptyList(),
    val canvasWidth: Float = 1000f,
    val canvasHeight: Float = 600f,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Calcula o Bounding Box (retângulo envolvente) de todos os pontos desenhados.
     */
    fun getBoundingBox(): RectF {
        if (strokes.isEmpty()) return RectF(0f, 0f, canvasWidth, canvasHeight)

        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        var hasPoints = false

        for (stroke in strokes) {
            for (p in stroke.points) {
                if (p.x < minX) minX = p.x
                if (p.x > maxX) maxX = p.x
                if (p.y < minY) minY = p.y
                if (p.y > maxY) maxY = p.y
                hasPoints = true
            }
        }

        return if (hasPoints) {
            RectF(minX, minY, maxX, maxY)
        } else {
            RectF(0f, 0f, canvasWidth, canvasHeight)
        }
    }

    /**
     * Retorna a lista de traços com coordenadas normalizadas na escala 0.0f a 1.0f
     */
    fun getNormalizedStrokes(): List<Stroke> {
        val safeW = if (canvasWidth > 0f) canvasWidth else 1000f
        val safeH = if (canvasHeight > 0f) canvasHeight else 600f
        return strokes.map { it.toNormalized(safeW, safeH) }
    }

    /**
     * Gera um Bitmap da assinatura com traçado vetorial suave (Bézier), antialiasing e
     * recorte inteligente do Bounding Box para eliminar bordas vazias desnecessárias e quadriculamento.
     */
    fun toBitmap(
        targetWidth: Int = 800,
        targetHeight: Int = 400,
        strokeColor: Int = 0xFF0D47A1.toInt(),
        strokeWidthPx: Float = 6.0f,
        backgroundColor: Int = android.graphics.Color.TRANSPARENT,
        cropToBounds: Boolean = true,
        paddingPercent: Float = 0.05f
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (backgroundColor != android.graphics.Color.TRANSPARENT) {
            canvas.drawColor(backgroundColor)
        }

        if (strokes.isEmpty()) return bitmap

        val bounds = getBoundingBox()
        val origW = if (bounds.width() > 10f) bounds.width() else canvasWidth.coerceAtLeast(10f)
        val origH = if (bounds.height() > 10f) bounds.height() else canvasHeight.coerceAtLeast(10f)

        val padX = if (cropToBounds) origW * paddingPercent else 0f
        val padY = if (cropToBounds) origH * paddingPercent else 0f

        val effectiveMinX = if (cropToBounds) (bounds.left - padX).coerceAtLeast(0f) else 0f
        val effectiveMinY = if (cropToBounds) (bounds.top - padY).coerceAtLeast(0f) else 0f
        val effectiveW = if (cropToBounds) (origW + padX * 2f) else canvasWidth
        val effectiveH = if (cropToBounds) (origH + padY * 2f) else canvasHeight

        val scaleX = targetWidth.toFloat() / effectiveW.coerceAtLeast(1f)
        val scaleY = targetHeight.toFloat() / effectiveH.coerceAtLeast(1f)
        val scale = minOf(scaleX, scaleY)

        val drawW = effectiveW * scale
        val drawH = effectiveH * scale
        val offsetX = (targetWidth - drawW) / 2f
        val offsetY = (targetHeight - drawH) / 2f

        val paint = Paint().apply {
            isAntiAlias = true
            isDither = true
            color = strokeColor
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = strokeWidthPx
        }

        for (stroke in strokes) {
            val pts = stroke.points
            if (pts.isEmpty()) continue

            val path = Path()
            val first = pts.first()
            val fx = offsetX + ((first.x - effectiveMinX) * scale)
            val fy = offsetY + ((first.y - effectiveMinY) * scale)
            path.moveTo(fx, fy)

            if (pts.size == 1) {
                canvas.drawCircle(fx, fy, strokeWidthPx / 2f, paint.apply { style = Paint.Style.FILL })
                paint.style = Paint.Style.STROKE
            } else if (pts.size == 2) {
                val sec = pts[1]
                val sx = offsetX + ((sec.x - effectiveMinX) * scale)
                val sy = offsetY + ((sec.y - effectiveMinY) * scale)
                path.lineTo(sx, sy)
                canvas.drawPath(path, paint)
            } else {
                for (i in 1 until pts.size - 1) {
                    val p0 = pts[i]
                    val p1 = pts[i + 1]
                    val p0x = offsetX + ((p0.x - effectiveMinX) * scale)
                    val p0y = offsetY + ((p0.y - effectiveMinY) * scale)
                    val p1x = offsetX + ((p1.x - effectiveMinX) * scale)
                    val p1y = offsetY + ((p1.y - effectiveMinY) * scale)
                    val midX = (p0x + p1x) / 2f
                    val midY = (p0y + p1y) / 2f
                    path.quadTo(p0x, p0y, midX, midY)
                }
                val last = pts.last()
                val lx = offsetX + ((last.x - effectiveMinX) * scale)
                val ly = offsetY + ((last.y - effectiveMinY) * scale)
                path.lineTo(lx, ly)
                canvas.drawPath(path, paint)
            }
        }

        return bitmap
    }

    /**
     * Converte a assinatura renderizada em Base64 PNG
     */
    fun toBase64Png(
        targetWidth: Int = 800,
        targetHeight: Int = 400,
        strokeColor: Int = 0xFF0D47A1.toInt()
    ): String {
        val bmp = toBitmap(
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            strokeColor = strokeColor,
            backgroundColor = android.graphics.Color.TRANSPARENT,
            cropToBounds = true
        )
        val stream = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val byteArray = stream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    fun toJson(): String {
        val root = JSONObject()
        root.put("canvasWidth", canvasWidth.toDouble())
        root.put("canvasHeight", canvasHeight.toDouble())
        root.put("createdAt", createdAt)

        val strokesArray = JSONArray()
        for (stroke in strokes) {
            val strokeObj = JSONObject()
            strokeObj.put("color", stroke.color)
            strokeObj.put("strokeWidth", stroke.strokeWidth.toDouble())

            val pointsArray = JSONArray()
            for (p in stroke.points) {
                val pObj = JSONObject()
                pObj.put("x", p.x.toDouble())
                pObj.put("y", p.y.toDouble())
                pObj.put("t", p.timestamp)
                // Inclui coordenadas normalizadas adicionais no JSON para compatibilidade e portabilidade
                val norm = p.toNormalized(canvasWidth, canvasHeight)
                pObj.put("nx", norm.x.toDouble())
                pObj.put("ny", norm.y.toDouble())
                pointsArray.put(pObj)
            }
            strokeObj.put("points", pointsArray)
            strokesArray.put(strokeObj)
        }
        root.put("strokes", strokesArray)
        return root.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): SignatureData {
            if (jsonStr.isBlank()) return SignatureData()
            return try {
                val root = JSONObject(jsonStr)
                val canvasWidth = root.optDouble("canvasWidth", 1000.0).toFloat()
                val canvasHeight = root.optDouble("canvasHeight", 600.0).toFloat()
                val createdAt = root.optLong("createdAt", System.currentTimeMillis())

                val strokesList = mutableListOf<Stroke>()
                val strokesArray = root.optJSONArray("strokes")
                if (strokesArray != null) {
                    for (i in 0 until strokesArray.length()) {
                        val sObj = strokesArray.getJSONObject(i)
                        val color = sObj.optInt("color", 0xFF0D47A1.toInt())
                        val strokeWidth = sObj.optDouble("strokeWidth", 5.5).toFloat()

                        val pointsList = mutableListOf<Point>()
                        val pointsArray = sObj.optJSONArray("points")
                        if (pointsArray != null) {
                            for (j in 0 until pointsArray.length()) {
                                val pObj = pointsArray.getJSONObject(j)
                                val x = pObj.getDouble("x").toFloat()
                                val y = pObj.getDouble("y").toFloat()
                                pointsList.add(
                                    Point(
                                        x = x,
                                        y = y,
                                        timestamp = pObj.optLong("t", System.currentTimeMillis())
                                    )
                                )
                            }
                        }
                        strokesList.add(Stroke(points = pointsList, color = color, strokeWidth = strokeWidth))
                    }
                }
                SignatureData(
                    strokes = strokesList,
                    canvasWidth = canvasWidth,
                    canvasHeight = canvasHeight,
                    createdAt = createdAt
                )
            } catch (e: Exception) {
                SignatureData()
            }
        }
    }
}
