package com.example.videodownload.parser

import com.example.videodownload.data.model.VideoInfo

/** 单一视频来源的解析能力。 */
interface VideoParser {
    fun supports(url: String): Boolean
    suspend fun parse(url: String): VideoInfo?
}

/** 按顺序尝试专用解析器，最后回退到通用解析器。 */
class ParseCoordinator(
    private val specializedParsers: List<VideoParser>,
    private val fallbackParser: VideoParser,
) {
    suspend fun parse(url: String): VideoInfo? {
        specializedParsers
            .asSequence()
            .filter { it.supports(url) }
            .forEach { parser ->
                parser.parse(url)?.takeIf { it.formats.isNotEmpty() }?.let { return it }
            }
        return fallbackParser.parse(url)?.takeIf { it.formats.isNotEmpty() }
    }
}
