package com.nanuq.native_video_encoder

object AudioMixer {
    fun mixPcmBytesLE(micBytes: ByteArray, gameBytes: ByteArray): ByteArray {
        val shortMic = micBytes.toShortArrayLE()
        val shortGame = gameBytes.toShortArrayLE()

        val mixedShort = mixShortArrays(shortMic, shortGame)

        return mixedShort.toByteArrayLE()
    }

    private fun mixShortArrays(micPcm: ShortArray, gamePcm: ShortArray): ShortArray {
        val maxLength = maxOf(micPcm.size, gamePcm.size)
        val mixed = ShortArray(maxLength)

        for (i in 0 until maxLength) {
            val micSample = if (i < micPcm.size) micPcm[i] else 0
            val gameSample = if (i < gamePcm.size) gamePcm[i] else 0

            var sum = micSample + gameSample

            // Clamp to prevent overflow
            if (sum > Short.MAX_VALUE) sum = Short.MAX_VALUE.toInt()
            if (sum < Short.MIN_VALUE) sum = Short.MIN_VALUE.toInt()

            mixed[i] = sum.toShort()
        }

        return mixed
    }

    private fun ByteArray.toShortArrayLE(): ShortArray {
        val shortCount = this.size / 2
        val shortArray = ShortArray(shortCount)
        for (i in 0 until shortCount) {
            val lo = this[i * 2].toInt() and 0xFF
            val hi = this[i * 2 + 1].toInt()
            shortArray[i] = ((hi shl 8) or lo).toShort()
        }
        return shortArray
    }

    private fun ShortArray.toByteArrayLE(): ByteArray {
        val byteArray = ByteArray(this.size * 2)
        for (i in this.indices) {
            val value = this[i].toInt()
            byteArray[i * 2] = (value and 0xFF).toByte()
            byteArray[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return byteArray
    }
}