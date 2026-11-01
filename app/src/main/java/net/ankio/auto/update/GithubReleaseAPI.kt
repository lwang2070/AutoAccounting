/*
 * Copyright (C) 2026 ankio(ankio@ankio.net)
 * Licensed under the Apache License, Version 3.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-3.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.ankio.auto.update

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.ankio.auto.http.RequestsUtils
import net.ankio.auto.storage.Logger

/**
 * 从自己的 GitHub Release 读取最新版本。
 *
 * 仓库是公开的，走匿名接口就能读，不需要 token、不需要自建服务器。
 * 匿名调用有每小时 60 次的限额，App 一天最多查几次，够用。
 */
object GithubReleaseAPI {
    const val OWNER = "lwang2070"
    const val REPO = "AutoAccounting"

    private const val RELEASES_URL = "https://api.github.com/repos/$OWNER/$REPO/releases?per_page=20"

    /**
     * APK 下载地址。文件名由发布流程固定成 `AutoAccounting-<版本号>.apk`，
     * 所以这里能直接拼出来，不用先列一遍 assets。
     */
    fun apkUrl(version: String): String =
        "https://github.com/$OWNER/$REPO/releases/download/v$version/$REPO-$version.apk"

    /**
     * 读取最新版本。
     *
     * 返回 [Result] 是为了让调用方分得清两种情况：
     * - `success(null)`：查到了，但仓库还没有任何 Release（或没有符合渠道的）
     * - `failure`：网络不通、被限流、返回体不是预期的 JSON
     *
     * @param channel Stable 只认正式版；其他渠道允许预发布版
     */
    suspend fun latest(channel: String): Result<UpdateModel?> {
        val client = RequestsUtils()
        client.addHeader("Accept", "application/vnd.github+json")

        return client.get(RELEASES_URL)
            .onFailure { Logger.w("检查更新失败: ${it.message}", it) }
            .mapCatching { raw ->
                val releases = JsonParser.parseString(raw).asJsonArray
                val wanted = if (channel.equals(VersionUtils.CHANNEL_STABLE, ignoreCase = true)) {
                    releases.firstOrNull { !it.asJsonObject.get("prerelease").asBoolean }
                } else {
                    releases.firstOrNull()
                }
                wanted?.let { toUpdateModel(it.asJsonObject) }
            }
    }

    private fun toUpdateModel(json: JsonObject): UpdateModel? {
        val tag = json.readString("tag_name") ?: return null
        return UpdateModel(
            log = json.readString("body").orEmpty(),
            date = json.readString("published_at").orEmpty().take(10),
            version = tag.removePrefix("v")
        )
    }

    /** 字段可能是 JSON null，直接 asString 会抛异常，这里统一当空串处理。 */
    private fun JsonObject.readString(key: String): String? {
        val value = get(key) ?: return null
        return if (value.isJsonNull) null else value.asString
    }
}
