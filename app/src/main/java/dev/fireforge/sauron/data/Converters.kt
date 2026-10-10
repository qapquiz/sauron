package dev.fireforge.sauron.data

import androidx.room.TypeConverter
import dev.fireforge.sauron.domain.SlipSource
import dev.fireforge.sauron.domain.SlipSourceCodec
import java.time.Instant

class Converters {

    @TypeConverter
    fun instantToMillis(value: Instant): Long = value.toEpochMilli()

    @TypeConverter
    fun millisToInstant(value: Long): Instant = Instant.ofEpochMilli(value)

    @TypeConverter
    fun slipSourceToString(value: SlipSource): String = SlipSourceCodec.encode(value)

    @TypeConverter
    fun stringToSlipSource(value: String): SlipSource = SlipSourceCodec.decode(value)
}
