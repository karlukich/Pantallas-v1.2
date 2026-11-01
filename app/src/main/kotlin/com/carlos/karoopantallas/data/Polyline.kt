package com.carlos.karoopantallas.data

/**
 * Decodifica una polilínea codificada de Google.
 * El Karoo entrega el perfil de la ruta como pares (distancia en m, altitud en m) con precisión 1.
 */
object Polyline {
    fun decode(encoded: String, precision: Int = 1): List<Pair<Double, Double>> {
        val factor = Math.pow(10.0, precision.toDouble())
        val out = ArrayList<Pair<Double, Double>>()
        var index = 0
        var a = 0
        var b = 0
        while (index < encoded.length) {
            var result = 0
            var shift = 0
            var byte: Int
            do {
                byte = encoded[index++].code - 63
                result = result or ((byte and 0x1f) shl shift)
                shift += 5
            } while (byte >= 0x20 && index < encoded.length)
            a += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            if (index >= encoded.length) break
            result = 0
            shift = 0
            do {
                byte = encoded[index++].code - 63
                result = result or ((byte and 0x1f) shl shift)
                shift += 5
            } while (byte >= 0x20 && index < encoded.length)
            b += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            out.add(a / factor to b / factor)
        }
        return out
    }

    /** Codifica pares (lat, lng) en una polilínea de Google (precisión 5, la que pide el mapa del Karoo). */
    fun encode(points: List<Pair<Double, Double>>, precision: Int = 5): String {
        val factor = Math.pow(10.0, precision.toDouble())
        val sb = StringBuilder()
        var pa = 0L
        var pb = 0L
        fun put(v: Long) {
            var x = if (v < 0) (v shl 1).inv() else v shl 1
            while (x >= 0x20) {
                sb.append(((0x20 or (x and 0x1f).toInt()) + 63).toChar())
                x = x shr 5
            }
            sb.append((x + 63).toInt().toChar())
        }
        for ((a, b) in points) {
            val ia = Math.round(a * factor)
            val ib = Math.round(b * factor)
            put(ia - pa)
            put(ib - pb)
            pa = ia
            pb = ib
        }
        return sb.toString()
    }
}
